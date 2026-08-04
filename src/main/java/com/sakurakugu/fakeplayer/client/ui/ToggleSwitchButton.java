package com.sakurakugu.fakeplayer.client.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 左侧显示标签、右侧显示当前状态的紧凑开关。 */
public final class ToggleSwitchButton extends Button {
    static final int SWITCH_WIDTH = 24;
    static final int SWITCH_HEIGHT = 12;
    static final int HANDLE_WIDTH = 8;

    private final BooleanSupplier enabled;

    public ToggleSwitchButton(
        int x, int y, int width, int height, Component message,
        BooleanSupplier enabled, OnPress onPress
    ) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.enabled = enabled;
        setTooltip(Tooltip.create(message));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int switchX = getX() + getWidth() - SWITCH_WIDTH;
        int switchY = getY() + (getHeight() - SWITCH_HEIGHT) / 2;
        int labelRight = switchX - 4;
        if (font.width(getMessage()) <= labelRight - getX()) {
            graphics.text(font, getMessage(), getX(), getY() + (getHeight() - 8) / 2, 0xFF404040, false);
        } else {
            PixelGui.drawScrollingText(graphics, font, getMessage(),
                getX(), labelRight, getY(), getHeight(), 0xFF404040);
        }
        PixelGui.drawToggle(graphics, switchX, switchY, enabled.getAsBoolean(), isMouseOver(mouseX, mouseY));
    }
}
