package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.GlobalFakePlayerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 显示所有假人并允许进入单个假人的控制页。 */
public final class GlobalFakePlayerScreen extends AbstractContainerScreen<GlobalFakePlayerMenu> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 240;
    private static final int PAGE_SIZE = 5;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_GAP = 5;

    private int page;

    public GlobalFakePlayerScreen(GlobalFakePlayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int firstIndex = page * PAGE_SIZE;
        int endIndex = Math.min(firstIndex + PAGE_SIZE, menu.playerNames().size());
        for (int index = firstIndex; index < endIndex; index++) {
            int row = index - firstIndex;
            int actionId = index;
            addRenderableWidget(
                Button.builder(Component.literal(menu.playerNames().get(index)), button -> sendAction(actionId))
                    .bounds(leftPos + 16, topPos + 48 + row * (BUTTON_HEIGHT + ROW_GAP), PANEL_WIDTH - 32, BUTTON_HEIGHT)
                    .build()
            );
        }

        int footerY = topPos + PANEL_HEIGHT - 30;
        Button previous = Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(leftPos + 16, footerY, 32, 20)
            .build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        addRenderableWidget(
            Button.builder(Component.translatable("gui.fakeplayer.global.refresh"), button -> sendAction(menu.playerNames().size()))
                .bounds(leftPos + 106, footerY, 88, 20)
                .build()
        );

        Button next = Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(leftPos + PANEL_WIDTH - 48, footerY, 32, 20)
            .build();
        next.active = page + 1 < pageCount();
        addRenderableWidget(next);
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(page + offset, pageCount() - 1));
        rebuildButtons();
    }

    private int pageCount() {
        return Math.max(1, (menu.playerNames().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void sendAction(int actionId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actionId);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xF0222528);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 40, 0xFF2F4A3D);
        graphics.fill(leftPos, topPos + 40, leftPos + PANEL_WIDTH, topPos + 42, 0xFFD5A94E);
        graphics.outline(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, PANEL_WIDTH / 2, 12, 0xFFFFFFFF);
        if (menu.playerNames().isEmpty()) {
            graphics.centeredText(font, Component.translatable("gui.fakeplayer.global.empty"), PANEL_WIDTH / 2, 100, 0xFFAAAAAA);
        }
        graphics.centeredText(
            font,
            Component.translatable("gui.fakeplayer.global.page", page + 1, pageCount(), menu.playerNames().size()),
            PANEL_WIDTH / 2,
            27,
            0xFFB8D8C5
        );
    }
}
