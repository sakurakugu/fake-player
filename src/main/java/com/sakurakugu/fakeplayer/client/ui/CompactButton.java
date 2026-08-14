package com.sakurakugu.fakeplayer.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 适合窄侧栏的像素风按钮。 */
public final class CompactButton extends Button {
    private final PixelGlyph glyph;

    public CompactButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        glyph = null;
    }

    public CompactButton(int x, int y, int width, int height, PixelGlyph glyph,
                         Component tooltip, OnPress onPress) {
        super(x, y, width, height, tooltip, onPress, DEFAULT_NARRATION);
        this.glyph = glyph;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawCompactControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        if (glyph == null) {
            PixelGui.drawCenteredText(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY(), getWidth(), getHeight(), active ? 0xFFFFFFFF : 0xFF777777);
        } else {
            glyph.drawCentered(graphics, getX(), getY(), getWidth(), getHeight(),
                active ? 0xFFFFFFFF : 0xFF777777);
        }
    }
}
