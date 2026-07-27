package com.sakurakugu.fakeplayer.event;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.command.FakePlayerCommand;
import com.sakurakugu.fakeplayer.command.BotCommand;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** 处理服务端通用事件，包括命令注册和假玩家交互。 */
@EventBusSubscriber(modid = FakePlayerMod.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        FakePlayerCommand.register(event.getDispatcher());
        BotCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        FakePlayerPersistence.restore(event.getServer());
    }

    @SubscribeEvent
    public static void removeFakePlayerBeforeLogin(PlayerNegotiationEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        CompletableFuture<Void> removal = new CompletableFuture<>();
        event.enqueueWork(removal);
        // 登录协商可能来自网络线程，玩家列表只能交给服务器线程修改。
        server.execute(() -> {
            try {
                FakeServerPlayer fake = FakePlayerManager.find(server, event.getProfile());
                if (fake != null) {
                    FakePlayerManager.remove(fake);
                }
                removal.complete(null);
            } catch (RuntimeException exception) {
                FakePlayerMod.LOGGER.error("真玩家 {} 登录时移除同名假玩家失败", event.getProfile().name(), exception);
                removal.completeExceptionally(exception);
            }
        });
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        // 仅服务端真实玩家右键假玩家时打开控制菜单。
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof FakeServerPlayer fake)) {
            return;
        }
        if (!FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())) {
            return;
        }

        FakePlayerMenuOpener.openControl(viewer, fake);
        // 阻止原版继续处理右键实体，避免同时触发物品交互。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
