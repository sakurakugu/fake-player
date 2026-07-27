package com.sakurakugu.fakeplayer.persistence;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
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

    public static void restore(MinecraftServer server) {
        FakePlayerSavedData savedData = data(server);
        if (!FakePlayerConfig.restoreFakePlayers()) {
            savedData.clearResidents();
            return;
        }

        for (Resident resident : savedData.residents()) {
            LoadResult result = load(server, resident, FakePlayerConfig.restoreActions());
            if (!result.successful()) {
                FakePlayerMod.LOGGER.warn("无法恢复驻留假玩家 {}：{}", resident.name(), result.reason());
            }
        }
    }

    public static LoadResult loadPreset(MinecraftServer server, Preset preset) {
        return load(server, preset.player(), true);
    }

    private static LoadResult load(MinecraftServer server, Resident resident, boolean restoreActions) {
        if (server.getPlayerList().getPlayers().stream().anyMatch(player ->
            player.getUUID().equals(resident.uuid())
                || player.getGameProfile().name().equalsIgnoreCase(resident.name()))) {
            return LoadResult.failure("同名或同 UUID 玩家已在线");
        }
        NameAndId identity = new NameAndId(resident.uuid(), resident.name());
        if (server.getPlayerList().getBans().isBanned(identity)) {
            return LoadResult.failure("玩家档案已被服务器封禁");
        }
        if (server.getPlayerList().isUsingWhitelist()
            && !server.getPlayerList().isWhiteListed(identity)
            && !server.getPlayerList().isOp(identity)) {
            return LoadResult.failure("玩家档案不在服务器白名单中");
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, resident.dimension());
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return LoadResult.failure("目标维度不存在：" + resident.dimension());
        }
        Vec3 position = new Vec3(resident.x(), resident.y(), resident.z());
        if (!validPosition(level, position)) {
            return LoadResult.failure("保存的位置已超出世界范围或边界");
        }

        try {
            FakeServerPlayer fake = FakePlayerManager.spawn(
                server,
                level,
                new GameProfile(resident.uuid(), resident.name()),
                position,
                new Vec2(resident.pitch(), resident.yaw()),
                resident.gameType(),
                resident.flying()
            );
            if (restoreActions) {
                fake.actions().restore(resident.actions());
            }
            track(fake);
            return LoadResult.success(fake);
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("加载保存的假玩家 {} 时发生异常", resident.name(), exception);
            return LoadResult.failure(exception.getMessage() == null ? "生成失败" : exception.getMessage());
        }
    }

    private static boolean validPosition(ServerLevel level, Vec3 position) {
        return Double.isFinite(position.x)
            && Double.isFinite(position.y)
            && Double.isFinite(position.z)
            && Level.isInSpawnableBounds(BlockPos.containing(position))
            && level.getWorldBorder().isWithinBounds(EntityType.PLAYER.getDimensions().makeBoundingBox(position));
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
