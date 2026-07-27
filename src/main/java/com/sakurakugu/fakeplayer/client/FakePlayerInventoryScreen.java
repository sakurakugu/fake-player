package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.FakePlayerInventoryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 绘制假人完整物品栏和末影箱，物品交互由原版容器界面处理。 */
public final class FakePlayerInventoryScreen extends AbstractContainerScreen<FakePlayerInventoryMenu> {
    public FakePlayerInventoryScreen(FakePlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.screenWidth(), menu.screenHeight());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0222528);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 16, 0xFF2F4A3D);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF8B8B8B);

        // 为每个真实槽位绘制稳定边框，不使用会写入容器的伪按钮物品。
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xFF101214);
            graphics.outline(x, y, 18, 18, 0xFF666A6D);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 8, 5, 0xFFFFFFFF, false);
        int inventoryLabelY = menu.view() == FakePlayerInventoryMenu.View.INVENTORY ? 118 : 76;
        graphics.text(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFC8C8C8, false);
        if (menu.view() == FakePlayerInventoryMenu.View.INVENTORY) {
            graphics.text(font, Component.translatable("gui.fakeplayer.equipment"), 176, 8, 0xFFC8C8C8, false);
        }
    }
}
