package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/** 统一创建假人全局菜单和控制菜单。 */
public final class FakePlayerMenuOpener {
    private FakePlayerMenuOpener() {
    }

    public static void openGlobal(ServerPlayer viewer) {
        openGlobal(viewer, false);
    }

    public static void openList(ServerPlayer viewer) {
        openGlobal(viewer, true);
    }

    private static void openGlobal(ServerPlayer viewer, boolean openListInitially) {
        List<String> names = FakePlayerManager.all(viewer.level().getServer()).stream()
            .map(fake -> fake.getGameProfile().name())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        viewer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, player) -> new GlobalFakePlayerMenu(
                    containerId,
                    inventory,
                    openListInitially,
                    names
                ),
                Component.translatable("gui.fakeplayer.global.title")
            ),
            data -> {
                data.writeBoolean(openListInitially);
                data.writeVarInt(names.size());
                names.forEach(name -> data.writeUtf(name, 64));
            }
        );
    }

    public static void openControl(ServerPlayer viewer, FakeServerPlayer fake) {
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
    }

    public static void openInventory(ServerPlayer viewer, FakeServerPlayer fake) {
        // 假玩家背包正好是 36 格，使用原版四行箱子菜单可直接编辑全部主背包槽位。
        viewer.openMenu(new SimpleMenuProvider(
            (containerId, inventory, player) -> new ChestMenu(
                MenuType.GENERIC_9x4, containerId, inventory, fake.getInventory(), 4),
            Component.translatable("gui.fakeplayer.inventory", fake.getGameProfile().name())
        ));
    }
}
