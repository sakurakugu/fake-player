package com.sakurakugu.fakeplayer.persistence;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.PlayerSnapshot;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Preset;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Resident;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 连接假人生命周期与世界存档，并提供预设加载的统一校验。 */
public final class FakePlayerPersistence {
    private static final Set<MinecraftServer> BACKED_UP_SERVERS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private FakePlayerPersistence() {
    }

    public static FakePlayerSavedData data(MinecraftServer server) {
        backupBeforeFirstRead(server);
        return server.overworld().getDataStorage().computeIfAbsent(FakePlayerSavedData.TYPE);
    }

    private static void backupBeforeFirstRead(MinecraftServer server) {
        synchronized (BACKED_UP_SERVERS) {
            if (!BACKED_UP_SERVERS.add(server)) {
                return;
            }
        }
        Path file = server.getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve(FakePlayerMod.MOD_ID)
            .resolve("fake_players.dat");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String backupName = "fake_players." + System.currentTimeMillis() + ".dat.bak";
            Files.copy(file, file.resolveSibling(backupName));
        } catch (IOException exception) {
            // 备份失败不能阻止世界加载，原版读取流程仍会记录实际的数据错误。
            FakePlayerMod.LOGGER.warn("备份假玩家世界存档失败：{}", file, exception);
        }
    }

    public static void track(FakeServerPlayer player) {
        if (!FakePlayerConfig.restoreFakePlayers() || player.hasDisconnected()) {
            return;
        }
        data(player.server()).putResident(Resident.from(player, FakePlayerConfig.restoreActions()));
    }

    public static void untrack(FakeServerPlayer player) {
        data(player.server()).removeResident(player.getUUID());
    }

    /** 使用原版实体存档格式保存玩家状态，避免重复维护背包、位置和能力字段。 */
    public static CompoundTag snapshot(FakeServerPlayer player) {
        try (ProblemReporter.ScopedCollector collector =
                 new ProblemReporter.ScopedCollector(player.problemPath(), FakePlayerMod.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(collector, player.registryAccess());
            player.saveWithoutId(output);
            return output.buildResult();
        }
    }

    /** 读取原版 playerdata；调用方应在玩家加入玩家列表前应用主体数据。 */
    public static Optional<CompoundTag> readPlayerData(FakeServerPlayer player) {
        return player.server().getPlayerList().loadPlayerData(player.nameAndId());
    }

    /** 在原版登录流程之前应用玩家主体数据，使首次同步和登录事件看到正确状态。 */
    public static void applyPlayerData(FakeServerPlayer player, CompoundTag playerData) {
        try (ProblemReporter.ScopedCollector collector =
                 new ProblemReporter.ScopedCollector(player.problemPath(), FakePlayerMod.LOGGER)) {
            ValueInput input = TagValueInput.create(collector, player.registryAccess(), playerData);
            player.load(input);
        }
    }

    /** 玩家加入世界后恢复依赖世界实体列表的末影珍珠和载具。 */
    public static void finishPlayerDataLoad(
        FakeServerPlayer player,
        CompoundTag playerData,
        boolean restoreParentVehicle
    ) {
        try (ProblemReporter.ScopedCollector collector =
                 new ProblemReporter.ScopedCollector(player.problemPath(), FakePlayerMod.LOGGER)) {
            ValueInput input = TagValueInput.create(collector, player.registryAccess(), playerData);
            player.loadAndSpawnEnderPearls(input);
            if (restoreParentVehicle) {
                player.loadAndSpawnParentVehicle(input);
            }
        }
    }

    public static void restore(MinecraftServer server) {
        FakePlayerSavedData savedData = data(server);
        if (!FakePlayerConfig.restoreFakePlayers()) {
            savedData.clearResidents();
            return;
        }

        for (Resident resident : savedData.residents()) {
            LoadResult result = loadResident(server, resident, FakePlayerConfig.restoreActions());
            if (!result.successful()) {
                FakePlayerMod.LOGGER.warn("无法恢复驻留假玩家 {}：{}", resident.name(), result.reason());
            }
        }
    }

    public static LoadResult loadPreset(MinecraftServer server, Preset preset) {
        return load(server, preset.player(), true);
    }

    private static LoadResult loadResident(MinecraftServer server, Resident resident, boolean restoreActions) {
        NameAndId identity = new NameAndId(resident.uuid(), resident.name());
        Optional<CompoundTag> saved = server.getPlayerList().loadPlayerData(identity);
        if (saved.isEmpty()) {
            return LoadResult.failure("原版玩家存档不存在：" + resident.uuid());
        }
        return load(server, new PlayerSnapshot(resident.uuid(), resident.name(), saved.get(), resident.actions()),
            restoreActions);
    }

    private static LoadResult load(MinecraftServer server, PlayerSnapshot snapshot, boolean restoreActions) {
        if (server.getPlayerList().getPlayers().stream().anyMatch(player ->
            player.getUUID().equals(snapshot.uuid())
                || player.getGameProfile().name().equalsIgnoreCase(snapshot.name()))) {
            return LoadResult.failure("同名或同 UUID 玩家已在线");
        }
        NameAndId identity = new NameAndId(snapshot.uuid(), snapshot.name());
        if (server.getPlayerList().getBans().isBanned(identity)) {
            return LoadResult.failure("玩家档案已被服务器封禁");
        }
        if (server.getPlayerList().isUsingWhitelist()
            && !server.getPlayerList().isWhiteListed(identity)
            && !server.getPlayerList().isOp(identity)) {
            return LoadResult.failure("玩家档案不在服务器白名单中");
        }

        SpawnState spawnState;
        try {
            spawnState = readSpawnState(server, snapshot.playerData());
        } catch (RuntimeException exception) {
            return LoadResult.failure(exception.getMessage() == null ? "原版玩家存档无效" : exception.getMessage());
        }
        if (!validPosition(spawnState.level(), spawnState.position())) {
            return LoadResult.failure("保存的位置已超出世界范围或边界");
        }

        FakeServerPlayer fake = null;
        try {
            fake = FakePlayerManager.spawnFromPlayerData(
                server,
                spawnState.level(),
                new GameProfile(snapshot.uuid(), snapshot.name()),
                spawnState.position(),
                spawnState.rotation(),
                spawnState.gameType(),
                spawnState.flying(),
                snapshot.playerData()
            );
            if (restoreActions) {
                fake.actions().restore(snapshot.actions());
            }
            track(fake);
            return LoadResult.success(fake);
        } catch (RuntimeException exception) {
            if (fake != null) {
                FakePlayerManager.remove(fake, false);
            }
            FakePlayerMod.LOGGER.error("加载保存的假玩家 {} 时发生异常", snapshot.name(), exception);
            return LoadResult.failure(exception.getMessage() == null ? "生成失败" : exception.getMessage());
        }
    }

    private static SpawnState readSpawnState(MinecraftServer server, CompoundTag playerData) {
        try (ProblemReporter.ScopedCollector collector =
                 new ProblemReporter.ScopedCollector(FakePlayerMod.LOGGER)) {
            ValueInput input = TagValueInput.create(collector, server.registryAccess(), playerData);
            String dimensionName = input.getString("Dimension")
                .orElseThrow(() -> new IllegalArgumentException("原版玩家存档缺少目标维度"));
            Identifier dimensionId = Identifier.tryParse(dimensionName);
            if (dimensionId == null) {
                throw new IllegalArgumentException("原版玩家存档中的维度无效：" + dimensionName);
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                throw new IllegalArgumentException("目标维度不存在：" + dimensionId);
            }
            Vec3 position = input.read("Pos", Vec3.CODEC)
                .orElseThrow(() -> new IllegalArgumentException("原版玩家存档缺少位置"));
            Vec2 rotation = input.read("Rotation", Vec2.CODEC).orElse(Vec2.ZERO);
            GameType gameType = input.read("playerGameType", GameType.LEGACY_ID_CODEC)
                .orElse(GameType.SURVIVAL);
            boolean flying = input.read("abilities", Abilities.Packed.CODEC)
                .map(Abilities.Packed::flying)
                .orElse(false);
            return new SpawnState(level, position, rotation, gameType, flying);
        }
    }

    private static boolean validPosition(ServerLevel level, Vec3 position) {
        return Double.isFinite(position.x)
            && Double.isFinite(position.y)
            && Double.isFinite(position.z)
            && Level.isInSpawnableBounds(BlockPos.containing(position))
            && level.getWorldBorder().isWithinBounds(EntityType.PLAYER.getDimensions().makeBoundingBox(position));
    }

    private record SpawnState(
        ServerLevel level,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying
    ) {
    }

    public record LoadResult(Optional<FakeServerPlayer> player, String reason) {
        public static LoadResult success(FakeServerPlayer player) {
            return new LoadResult(Optional.of(player), "");
        }

        public static LoadResult failure(String reason) {
            return new LoadResult(Optional.empty(), reason);
        }

        public boolean successful() {
            return player.isPresent();
        }
    }
}
