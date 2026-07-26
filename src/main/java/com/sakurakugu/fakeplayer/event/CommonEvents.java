package com.sakurakugu.fakeplayer.event;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.command.FakePlayerCommand;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 处理服务端通用事件，包括命令注册和假玩家交互。 */
@EventBusSubscriber(modid = FakePlayerMod.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        FakePlayerCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        // 仅服务端真实玩家右键假玩家时打开控制菜单。
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof FakeServerPlayer fake)) {
            return;
        }

        FakePlayerMenuOpener.openControl(viewer, fake);
        // 阻止原版继续处理右键实体，避免同时触发物品交互。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
