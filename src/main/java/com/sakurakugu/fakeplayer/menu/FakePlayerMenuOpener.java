package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

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
                    FakePlayerConfig.globalSettingsMask(),
                    names
                ),
                Component.translatable("gui.fakeplayer.global.title")
            ),
            data -> {
                data.writeBoolean(openListInitially);
                data.writeVarInt(FakePlayerConfig.globalSettingsMask());
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
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.INVENTORY);
    }

    public static void openPossessedInventory(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.POSSESSED_INVENTORY);
    }

    public static void openEnderChest(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.ENDER_CHEST);
    }

    private static void openStorage(ServerPlayer viewer, FakeServerPlayer fake, FakePlayerInventoryMenu.View view) {
        boolean possessedByViewer = FakePlayerPossession.isControlling(viewer, fake);
        boolean targetOccupied = FakePlayerPossession.isPossessed(fake);
        Component title = Component.translatable(
            view == FakePlayerInventoryMenu.View.ENDER_CHEST
                ? "gui.fakeplayer.ender_chest"
                : "gui.fakeplayer.inventory",
            fake.getGameProfile().name()
        );
        viewer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, player) -> new FakePlayerInventoryMenu(
                    containerId, inventory, fake, view, possessedByViewer, targetOccupied),
                title
            ),
            data -> {
                data.writeUtf(fake.getGameProfile().name());
                data.writeVarInt(view.ordinal());
                data.writeVarInt(fake.getId());
                data.writeBoolean(possessedByViewer);
                data.writeBoolean(targetOccupied);
            }
        );
    }
}
