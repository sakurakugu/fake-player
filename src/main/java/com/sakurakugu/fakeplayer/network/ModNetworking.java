package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import java.util.List;

/** 注册客户端与服务端之间的假人菜单请求。 */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            OpenGlobalMenuPayload.TYPE,
            OpenGlobalMenuPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && FakePlayerConfig.canUseCommands(player.createCommandSourceStack())) {
                    FakePlayerMenuOpener.openGlobal(player);
                }
            }
        );
        registrar.playToServer(
            RequestChunkMapPayload.TYPE,
            RequestChunkMapPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && FakePlayerConfig.canUseCommands(player.createCommandSourceStack())) {
                    var anchors = List.copyOf(ChunkLoaderManager.data(player.level().getServer()).anchors());
                    PacketDistributor.sendToPlayer(player,
                        ChunkMapSnapshotPayload.create(player, anchors, payload.openScreen()));
                }
            }
        );
        registrar.playToClient(ChunkMapSnapshotPayload.TYPE, ChunkMapSnapshotPayload.STREAM_CODEC);
    }
}
