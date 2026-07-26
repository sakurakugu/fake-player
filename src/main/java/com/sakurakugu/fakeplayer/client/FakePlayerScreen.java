package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.FakePlayerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 假玩家控制菜单的客户端绘制界面。 */
public final class FakePlayerScreen extends AbstractContainerScreen<FakePlayerMenu> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 172;
    private static final int PADDING = 12;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_HEIGHT = 22;

    public FakePlayerScreen(FakePlayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = (PANEL_WIDTH - PADDING * 2 - BUTTON_GAP) / 2;
        int left = leftPos + PADDING;
        int right = left + buttonWidth + BUTTON_GAP;
        int firstRow = topPos + 48;
        int rowStep = BUTTON_HEIGHT + BUTTON_GAP;

        addActionButton(left, firstRow, buttonWidth, "gui.fakeplayer.attack", FakePlayerMenu.ACTION_ATTACK);
        addActionButton(right, firstRow, buttonWidth, "gui.fakeplayer.use", FakePlayerMenu.ACTION_USE);
        addActionButton(left, firstRow + rowStep, buttonWidth, "gui.fakeplayer.jump", FakePlayerMenu.ACTION_JUMP);
        addActionButton(right, firstRow + rowStep, buttonWidth, "gui.fakeplayer.sneak", FakePlayerMenu.ACTION_SNEAK);
        addActionButton(left, firstRow + rowStep * 2, buttonWidth, "gui.fakeplayer.turn_left", FakePlayerMenu.ACTION_LEFT);
        addActionButton(right, firstRow + rowStep * 2, buttonWidth, "gui.fakeplayer.turn_right", FakePlayerMenu.ACTION_RIGHT);
        addActionButton(left, firstRow + rowStep * 3, buttonWidth, "gui.fakeplayer.stop", FakePlayerMenu.ACTION_STOP);
        addActionButton(right, firstRow + rowStep * 3, buttonWidth, "gui.fakeplayer.remove", FakePlayerMenu.ACTION_REMOVE);
    }

    private void addActionButton(int x, int y, int width, String translationKey, int actionId) {
        addRenderableWidget(
            Button.builder(Component.translatable(translationKey), button -> sendAction(actionId))
                .bounds(x, y, width, BUTTON_HEIGHT)
                .build()
        );
    }

    private void sendAction(int actionId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actionId);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // 使用专用面板代替原版箱子纹理，并用标题栏区分信息与操作区域。
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xF0222528);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 40, 0xFF2F4A3D);
        graphics.fill(leftPos, topPos + 40, leftPos + PANEL_WIDTH, topPos + 42, 0xFFD5A94E);
        graphics.outline(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, PANEL_WIDTH / 2, 11, 0xFFFFFFFF);
        graphics.centeredText(
            font,
            Component.translatable("gui.fakeplayer.status", menu.targetName()),
            PANEL_WIDTH / 2,
            25,
            0xFFB8D8C5
        );
    }
}
