package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData;
import com.sakurakugu.fakeplayer.chunkloading.FakePlayerSimulationService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 负责假玩家的创建、查询和移除，并统一维护其玩家列表生命周期。 */
public final class FakePlayerManager {
    private FakePlayerManager() {
    }

    public static FakeServerPlayer spawn(
        MinecraftServer server,
        ServerLevel level,
        String name,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying
    ) {
        return spawn(server, level, new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name), position, rotation,
            gameType, flying);
    }

    public static FakeServerPlayer spawn(
        MinecraftServer server,
        ServerLevel level,
        GameProfile profile,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying
    ) {
        return spawn(server, level, profile, position, rotation, gameType, flying, Optional.empty(), true, true);
    }

    /** 按完整原版玩家数据创建假玩家，用于恢复驻留状态和预设快照。 */
    public static FakeServerPlayer spawnFromPlayerData(
        MinecraftServer server,
        ServerLevel level,
        GameProfile profile,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying,
        CompoundTag playerData
    ) {
        return spawn(server, level, profile, position, rotation, gameType, flying, Optional.of(playerData), false,
            false);
    }

    private static FakeServerPlayer spawn(
        MinecraftServer server,
        ServerLevel level,
        GameProfile profile,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying,
        Optional<CompoundTag> suppliedPlayerData,
        boolean overrideSavedSpawnState,
        boolean trackResident
    ) {
        String name = profile.name();
        // 假玩家与真实玩家共享玩家列表，名称或 UUID 任一冲突都不能加入。
        if (server.getPlayerList().getPlayers().stream().anyMatch(player ->
            player.getUUID().equals(profile.id())
                || player.getGameProfile().name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("duplicate");
        }

        // 使用离线 UUID，使同名假玩家在不同启动周期中拥有稳定身份。
        FakeServerPlayer fake = new FakeServerPlayer(server, level, profile);
        Optional<CompoundTag> playerData = suppliedPlayerData.isPresent()
            ? suppliedPlayerData
            : FakePlayerPersistence.readPlayerData(fake);
        playerData.ifPresent(data -> FakePlayerPersistence.applyPlayerData(fake, data));
        fake.snapTo(position.x, position.y, position.z, rotation.y, rotation.x);
        fake.gameMode.changeGameModeForPlayer(gameType);
        fake.getAbilities().flying = flying && fake.getAbilities().mayfly;

        // 通过原版登录流程接入玩家列表，让追踪、区块加载和广播行为保持一致。
        FakeConnection connection = new FakeConnection();
        try {
            server.getPlayerList().placeNewPlayer(connection, fake, CommonListenerCookie.createInitial(profile, false));
            playerData.ifPresent(data -> FakePlayerPersistence.finishPlayerDataLoad(fake, data, !overrideSavedSpawnState));
            if (overrideSavedSpawnState) {
                // 手动生成的位置由命令决定，不恢复存档中的载具关系。
                fake.stopRiding();
            }
            fake.connection.teleport(fake.getX(), fake.getY(), fake.getZ(), fake.getYRot(), fake.getXRot());
            fake.showAllSkinLayers();
            if (trackResident) {
                FakePlayerPersistence.track(fake);
            }
            return fake;
        } catch (RuntimeException exception) {
            // 登录事件可能在玩家已加入列表后抛出异常，不能只依据 placeNewPlayer 是否正常返回。
            if (server.getPlayerList().getPlayers().stream().anyMatch(player -> player == fake)) {
                remove(fake, false);
            }
            throw exception;
        }
    }

    public static FakeServerPlayer shadow(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        GameProfile profile = player.getGameProfile();
        Vec3 position = player.position();
        Vec2 rotation = player.getRotationVector();
        boolean flying = player.getAbilities().flying;

        // 先按原版退出流程保存并移除真玩家，释放其名称和 UUID 后再创建替身。
        server.getPlayerList().remove(player);
        player.connection.disconnect(Component.translatable("commands.fakeplayer.shadow_kicked"));

        FakeServerPlayer fake = new FakeServerPlayer(server, level, profile, player.clientInformation());
        fake.snapTo(position.x, position.y, position.z, rotation.y, rotation.x);
        // 在加入玩家列表前继承原版状态，使登录事件看到的就是最终玩家数据。
        fake.restoreFrom(player, true);
        fake.getAbilities().flying = flying && fake.getAbilities().mayfly;
        FakeConnection connection = new FakeConnection();
        server.getPlayerList().placeNewPlayer(connection, fake, CommonListenerCookie.createInitial(profile, false));
        fake.connection.teleport(position.x, position.y, position.z, rotation.y, rotation.x);
        FakePlayerPersistence.track(fake);
        return fake;
    }

    public static void remove(FakeServerPlayer fake) {
        remove(fake, true);
    }

    /** 移除假玩家；若正在被附身，先把身体状态交换回去再移除。 */
    public static void remove(FakeServerPlayer fake, boolean removeResident) {
        // 移除操作可能由死亡回调和菜单同时触发，需要保证可重复调用。
        if (fake.hasDisconnected()) {
            return;
        }
        FakePlayerPossession.restoreTarget(fake);
        fake.actions().stop();
        fake.shakeOffPlayers();
        if (removeResident) {
            FakePlayerPersistence.untrack(fake);
            FakePlayerSimulationService.removePolicy(fake.server(), fake.getUUID());
        }
        fake.disconnect();
        fake.server().getPlayerList().remove(fake);
    }

    public static void kill(FakeServerPlayer fake) {
        // 与 Carpet 的 player kill 一致：这里表示让假玩家退出，而不是模拟一次死亡。
        remove(fake);
    }

    /** 将全部玩家状态迁移到新身份，并重建实体以同步玩家列表。 */
    public static RenameResult rename(FakeServerPlayer player, GameProfile profile) {
        MinecraftServer server = player.server();
        if (player.isPassenger() || !player.getPassengers().isEmpty()) {
            return RenameResult.failure("commands.fakeplayer.rename_riding");
        }
        FakePlayerSavedData savedData = FakePlayerPersistence.data(server);
        if (server.getPlayerList().getPlayers().stream().anyMatch(other -> other != player
            && (other.getUUID().equals(profile.id())
                || other.getGameProfile().name().equalsIgnoreCase(profile.name())))) {
            return RenameResult.failure("commands.fakeplayer.duplicate");
        }
        if (!profile.id().equals(player.getUUID())
            && (server.getPlayerList().loadPlayerData(new NameAndId(profile)).isPresent()
                || FakePlayerPersistence.hasPlayerProgressData(server, profile.id()))) {
            return RenameResult.failure("commands.fakeplayer.rename_existing_data");
        }

        String oldName = player.getGameProfile().name();
        UUID oldUuid = player.getUUID();
        ServerLevel level = player.level();
        Vec3 position = player.position();
        Vec2 rotation = player.getRotationVector();
        GameType gameType = player.gameMode.getGameModeForPlayer();
        boolean flying = player.getAbilities().flying;
        FakePlayerSavedData.PlayerSnapshot snapshot = FakePlayerSavedData.PlayerSnapshot.from(player, true);
        savedData.migratePlayer(oldUuid, profile);
        remove(player, false);

        FakeServerPlayer renamed = null;
        boolean progressDataMoved = false;
        try {
            if (!oldUuid.equals(profile.id())) {
                FakePlayerPersistence.movePlayerProgressData(server, oldUuid, profile.id());
                progressDataMoved = true;
            }
            renamed = restoreSnapshot(
                server, level, snapshot, profile, position, rotation, gameType, flying);
            if (!oldUuid.equals(profile.id())) {
                FakePlayerPersistence.movePlayerData(renamed, oldUuid);
            }
            FakePlayerPersistence.track(renamed);
            return RenameResult.success(renamed);
        } catch (RuntimeException | IOException exception) {
            if (renamed != null) {
                remove(renamed, false);
                try {
                    FakePlayerPersistence.deletePlayerData(server, profile.id());
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            if (progressDataMoved) {
                try {
                    FakePlayerPersistence.movePlayerProgressData(server, profile.id(), oldUuid);
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            savedData.migratePlayer(profile.id(), new GameProfile(oldUuid, oldName, snapshot.profile().properties()));
            try {
                FakeServerPlayer restored = restoreSnapshot(
                    server, level, snapshot, snapshot.profile(), position, rotation, gameType, flying);
                if (!oldUuid.equals(profile.id())) {
                    FakePlayerPersistence.movePlayerData(restored, profile.id());
                }
                FakePlayerPersistence.track(restored);
            } catch (RuntimeException | IOException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            return RenameResult.failure("commands.fakeplayer.rename_failed");
        }
    }

    private static FakeServerPlayer restoreSnapshot(
        MinecraftServer server,
        ServerLevel level,
        FakePlayerSavedData.PlayerSnapshot snapshot,
        GameProfile profile,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying
    ) {
        CompoundTag migratedPlayerData = snapshot.playerData();
        migratedPlayerData.remove("UUID");
        FakeServerPlayer restored = spawnFromPlayerData(
            server, level, profile, position, rotation, gameType, flying,
            migratedPlayerData);
        restored.actions().restore(snapshot.actions());
        restored.automation().setSettings(snapshot.automation());
        return restored;
    }

    public record RenameResult(FakeServerPlayer player, String messageKey) {
        public static RenameResult success(FakeServerPlayer player) {
            return new RenameResult(player, "");
        }

        public static RenameResult failure(String messageKey) {
            return new RenameResult(null, messageKey);
        }

        public boolean successful() {
            return player != null;
        }
    }

    public static FakeServerPlayer find(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name) instanceof FakeServerPlayer fake ? fake : null;
    }

    public static FakeServerPlayer find(MinecraftServer server, GameProfile profile) {
        return all(server).stream()
            .filter(fake -> fake.getUUID().equals(profile.id())
                || fake.getGameProfile().name().equalsIgnoreCase(profile.name()))
            .findFirst()
            .orElse(null);
    }

    public static List<FakeServerPlayer> all(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
            .filter(FakeServerPlayer.class::isInstance)
            .map(FakeServerPlayer.class::cast)
            .toList();
    }
}
