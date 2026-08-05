package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.command.FakePlayerCommand;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import com.sakurakugu.fakeplayer.menu.BotManagementActions;
import com.sakurakugu.fakeplayer.menu.BotManagementMenu;
import com.sakurakugu.fakeplayer.menu.ChunkLoaderActions;
import com.sakurakugu.fakeplayer.menu.ChunkLoaderMenu;
import com.sakurakugu.fakeplayer.menu.GlobalFakePlayerMenu;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
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
            BotActionPayload.TYPE,
            BotActionPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof BotManagementMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && FakePlayerConfig.canUseCommands(player.createCommandSourceStack())) {
                    BotManagementActions.handle(player, payload);
                }
            }
        );
        registrar.playToServer(
            SpawnFakePlayerPayload.TYPE,
            SpawnFakePlayerPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof GlobalFakePlayerMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && FakePlayerConfig.canUseCommands(player.createCommandSourceStack())) {
                    FakePlayerCommand.spawnFromMenu(player, payload.name());
                }
            }
        );
        registrar.playToServer(
            ChunkLoaderActionPayload.TYPE,
            ChunkLoaderActionPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof ChunkLoaderMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && FakePlayerConfig.canUseCommands(player.createCommandSourceStack())) {
                    ChunkLoaderActions.handle(player, payload);
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
        registrar.playToServer(
            StopPossessionPayload.TYPE,
            StopPossessionPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    FakePlayerPossession.stop(player);
                }
            }
        );
        registrar.playToClient(ChunkMapSnapshotPayload.TYPE, ChunkMapSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(PossessionStatePayload.TYPE, PossessionStatePayload.STREAM_CODEC);
        registrar.playToClient(BodyRotationPayload.TYPE, BodyRotationPayload.STREAM_CODEC);
    }
}
