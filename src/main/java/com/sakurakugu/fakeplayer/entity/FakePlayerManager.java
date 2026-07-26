package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 负责假玩家的创建、查询和移除，并统一维护其玩家列表生命周期。 */
public final class FakePlayerManager {
    private FakePlayerManager() {
    }

    public static FakeServerPlayer spawn(MinecraftServer server, ServerLevel level, String name, Vec3 position, Vec2 rotation) {
        // 假玩家与真实玩家共享服务端玩家列表，因此名称不能与任何在线玩家重复。
        if (server.getPlayerList().getPlayerByName(name) != null) {
            throw new IllegalArgumentException("duplicate");
        }

        // 使用离线 UUID，使同名假玩家在不同启动周期中拥有稳定身份。
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        FakeServerPlayer fake = new FakeServerPlayer(server, level, profile);
        fake.snapTo(position.x, position.y, position.z, rotation.y, rotation.x);

        // 通过原版登录流程接入玩家列表，让追踪、区块加载和广播行为保持一致。
        FakeConnection connection = new FakeConnection();
        server.getPlayerList().placeNewPlayer(connection, fake, CommonListenerCookie.createInitial(profile, false));
        fake.connection.teleport(position.x, position.y, position.z, rotation.y, rotation.x);
        fake.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        fake.setHealth(fake.getMaxHealth());
        fake.showAllSkinLayers();
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

    public static FakeServerPlayer find(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name) instanceof FakeServerPlayer fake ? fake : null;
    }

    public static List<FakeServerPlayer> all(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
            .filter(FakeServerPlayer.class::isInstance)
            .map(FakeServerPlayer.class::cast)
            .toList();
    }
}
