package com.sakurakugu.fakeplayer.event;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.command.FakePlayerCommand;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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

        viewer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, player) -> new FakePlayerMenu(containerId, inventory, fake),
                Component.translatable("gui.fakeplayer.title", fake.getGameProfile().name())
            ),
            data -> {
                // 客户端没有 FakeServerPlayer 引用，只传递界面展示所需的稳定快照。
                data.writeVarInt(fake.getId());
                data.writeUtf(fake.getGameProfile().name());
            }
        );
        // 阻止原版继续处理右键实体，避免同时触发物品交互。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
