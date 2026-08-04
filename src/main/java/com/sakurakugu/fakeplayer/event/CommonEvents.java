package com.sakurakugu.fakeplayer.event;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.command.FakePlayerCommand;
import com.sakurakugu.fakeplayer.command.BotCommand;
import com.sakurakugu.fakeplayer.command.ChunkLoaderCommand;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** 处理服务端通用事件，包括命令注册和假玩家交互。 */
@EventBusSubscriber(modid = FakePlayerMod.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        FakePlayerCommand.register(event.getDispatcher());
        BotCommand.register(event.getDispatcher());
        ChunkLoaderCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        FakePlayerPersistence.restore(event.getServer());
        ChunkLoaderManager.reconcile(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        // 原版即将保存 playerdata，必须先把真人恢复到附身前的位置。
        FakePlayerPossession.stopAll();
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
                    // 保留驻留记录，假人只会在服务器下次启动时按名称和 UUID 占用情况决定是否恢复。
                    FakePlayerManager.remove(fake, false);
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
        // 仅服务端真实玩家右键假玩家时打开物品栏管理页面。
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof FakeServerPlayer fake)) {
            return;
        }
        if (!FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())) {
            return;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            // 附身中的假人表现为当前玩家的躯壳，右键时应与普通玩家一样不打开管理界面。
            // viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_locked"));
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        FakePlayerMenuOpener.openInventory(viewer, fake);
        // 阻止原版继续处理右键实体，避免同时触发物品交互。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FakePlayerPossession.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakeServerPlayer)) {
            // 真人退出前先恢复附身前的身体状态，确保原版保存的 playerdata 仍是真人原来的位置。
            if (!FakePlayerPossession.stop(player)) {
                FakePlayerPossession.discard(player);
            }
        }
    }

    @SubscribeEvent
    public static void preventDimensionTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof FakeServerPlayer fake && FakePlayerPossession.isPossessed(fake)) {
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ServerPlayer player
            && FakePlayerPossession.isPossessing(player)) {
            player.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_no_dimension"));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void possessionDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof FakeServerPlayer fake
            && FakePlayerPossession.handleShellDeath(fake, event.getSource())) {
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ServerPlayer player
            && FakePlayerPossession.handleActiveBodyDeath(player, event.getSource())) {
            event.setCanceled(true);
        }
    }
}
