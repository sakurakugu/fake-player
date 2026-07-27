package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
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
        String name = profile.name();
        // 假玩家与真实玩家共享玩家列表，名称或 UUID 任一冲突都不能加入。
        if (server.getPlayerList().getPlayers().stream().anyMatch(player ->
            player.getUUID().equals(profile.id())
                || player.getGameProfile().name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("duplicate");
        }

        // 使用离线 UUID，使同名假玩家在不同启动周期中拥有稳定身份。
        FakeServerPlayer fake = new FakeServerPlayer(server, level, profile);
        fake.snapTo(position.x, position.y, position.z, rotation.y, rotation.x);

        // 通过原版登录流程接入玩家列表，让追踪、区块加载和广播行为保持一致。
        FakeConnection connection = new FakeConnection();
        server.getPlayerList().placeNewPlayer(connection, fake, CommonListenerCookie.createInitial(profile, false));
        fake.connection.teleport(position.x, position.y, position.z, rotation.y, rotation.x);
        fake.gameMode.changeGameModeForPlayer(gameType);
        fake.getAbilities().flying = flying && fake.getAbilities().mayfly;
        fake.setHealth(fake.getMaxHealth());
        fake.showAllSkinLayers();
        return fake;
    }

    public static FakeServerPlayer shadow(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        GameProfile profile = player.getGameProfile();
        Vec3 position = player.position();
        Vec2 rotation = player.getRotationVector();
        GameType gameType = player.gameMode.getGameModeForPlayer();
        float health = player.getHealth();
        boolean flying = player.getAbilities().flying;

        // 先按原版退出流程保存并移除真玩家，释放其名称和 UUID 后再创建替身。
        server.getPlayerList().remove(player);
        player.connection.disconnect(Component.translatable("commands.fakeplayer.shadow_kicked"));

        FakeServerPlayer fake = new FakeServerPlayer(server, level, profile, player.clientInformation());
        fake.snapTo(position.x, position.y, position.z, rotation.y, rotation.x);
        FakeConnection connection = new FakeConnection();
        server.getPlayerList().placeNewPlayer(connection, fake, CommonListenerCookie.createInitial(profile, false));
        // 登录流程不会为手工创建的玩家恢复存档，这里同时继承背包、经验、饥饿值和药水效果。
        fake.restoreFrom(player, true);
        fake.connection.teleport(position.x, position.y, position.z, rotation.y, rotation.x);
        fake.gameMode.changeGameModeForPlayer(gameType);
        fake.setHealth(Math.min(health, fake.getMaxHealth()));
        fake.getAbilities().flying = flying && fake.getAbilities().mayfly;
        return fake;
    }

    public static void remove(FakeServerPlayer fake) {
        // 移除操作可能由死亡回调和菜单同时触发，需要保证可重复调用。
        if (fake.hasDisconnected()) {
            return;
        }
        fake.actions().stop();
        fake.disconnect();
        fake.server().getPlayerList().remove(fake);
    }

    public static void kill(FakeServerPlayer fake) {
        boolean keepInventory = fake.level().getGameRules().get(GameRules.KEEP_INVENTORY);
        if (!keepInventory && fake.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
            // 生存模式遵循 keepInventory；其他模式直接保存背包，便于下次同名玩家恢复。
            for (int slot = 0; slot < fake.getInventory().getContainerSize(); slot++) {
                if (!fake.getInventory().getItem(slot).isEmpty()) {
                    fake.drop(fake.getInventory().removeItemNoUpdate(slot), true, false);
                }
            }
        }
        remove(fake);
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
