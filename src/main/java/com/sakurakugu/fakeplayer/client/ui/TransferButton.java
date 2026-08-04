package com.sakurakugu.fakeplayer.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** 根据 Shift 和 Ctrl 状态切换转移模式的 12 像素箭头按钮。 */
public final class TransferButton extends Button {
    public static final int SIZE = 12;

    private final Direction direction;
    private final OnTransfer onTransfer;
    private boolean showingAll;
    private boolean showingHotbar;

    public TransferButton(int x, int y, Direction direction, OnTransfer onTransfer) {
        super(x, y, SIZE, SIZE, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.direction = direction;
        this.onTransfer = onTransfer;
        updateTooltip(false, false);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onTransfer.run(input.hasShiftDown(), input.hasControlDown());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean transferAll = minecraft.hasShiftDown();
        boolean includeHotbar = minecraft.hasControlDown();
        if (showingAll != transferAll || showingHotbar != includeHotbar) {
            updateTooltip(transferAll, includeHotbar);
        }
        drawBackground(graphics, isMouseOver(mouseX, mouseY));
        drawArrow(graphics, transferAll);
    }

    private void updateTooltip(boolean transferAll, boolean includeHotbar) {
        showingAll = transferAll;
        showingHotbar = includeHotbar;
        Component message = Component.translatable("gui.fakeplayer.transfer_" + direction.translationPart
            + (transferAll ? "_all" : "_matching"));
        if (includeHotbar) {
            message = message.copy().append(Component.translatable("gui.fakeplayer.transfer_hotbar_suffix"));
        }
        setMessage(message);
        Component tooltip = getMessage().copy();
        if (!includeHotbar) {
            tooltip = tooltip.copy().append(Component.literal("\n"))
                .append(Component.translatable("gui.fakeplayer.transfer_hotbar_hint").withColor(0x555555));
        }
        if (!transferAll) {
            tooltip = tooltip.copy().append(Component.literal("\n"))
                .append(Component.translatable("gui.fakeplayer.transfer_all_hint").withColor(0x555555));
        }
        setTooltip(Tooltip.create(tooltip));
    }

    private void drawBackground(GuiGraphicsExtractor graphics, boolean hovered) {
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();
        if (hovered) {
            graphics.fill(x, y, right, bottom, 0xFFFFFFFF);
        }
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFFAAAAAA);
        graphics.fill(x + 2, y + 2, right - 1, bottom - 1, 0xFF8B8B8B);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        graphics.fill(right - 2, y + 1, right - 1, bottom - 1, 0xFF555555);
    }

    private void drawArrow(GuiGraphicsExtractor graphics, boolean showGap) {
        int centerX = getX() + 6;
        if (direction == Direction.TO_CONTAINER) {
            drawUpArrow(graphics, centerX, getY() + 1, 0xFF78A849, 0xFF59843C, showGap);
        } else {
            drawDownArrow(graphics, centerX - 1, getY() + 1, 0xFFC53212, 0xFF8D0B05, showGap);
        }
    }

    private static void drawUpArrow(
        GuiGraphicsExtractor graphics, int x, int y, int color, int shadow, boolean showGap
    ) {
        graphics.fill(x - 1, y + 4, x, y + 9, color);
        graphics.fill(x - 1, y + 1, x + 1, y + 2, color);
        graphics.fill(x - 2, y + 2, x + 2, y + 3, color);
        graphics.fill(x - 3, y + 3, x + 3, y + 4, color);
        graphics.fill(x, y + 4, x + 1, y + 9, shadow);
        if (showGap) {
            graphics.fill(x - 1, y + 6, x + 1, y + 7, 0xFF8B8B8B);
        }
    }

    private static void drawDownArrow(
        GuiGraphicsExtractor graphics, int x, int y, int color, int shadow, boolean showGap
    ) {
        graphics.fill(x, y + 1, x + 1, y + 6, color);
        graphics.fill(x - 2, y + 6, x + 4, y + 7, color);
        graphics.fill(x - 1, y + 7, x + 3, y + 8, color);
        graphics.fill(x, y + 8, x + 2, y + 9, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 6, shadow);
        if (showGap) {
            graphics.fill(x, y + 3, x + 2, y + 4, 0xFF8B8B8B);
        }
    }

    public enum Direction {
        TO_CONTAINER("to_container"),
        TO_INVENTORY("to_inventory");

        private final String translationPart;

        Direction(String translationPart) {
            this.translationPart = translationPart;
        }
    }

    @FunctionalInterface
    public interface OnTransfer {
        void run(boolean transferAll, boolean includeHotbar);
    }
}
