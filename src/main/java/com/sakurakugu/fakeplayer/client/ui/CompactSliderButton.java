package com.sakurakugu.fakeplayer.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** 使用像素风轨道和手柄的滑动条基类。 */
public abstract class CompactSliderButton extends AbstractSliderButton {
    protected CompactSliderButton(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        graphics.fill(x, y, x + width, y + height, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF565656);

        // 保留原版 8 像素宽、占满控件高度的滑块手柄，只改用纯色绘制。
        int handleX = x + (int) (value * (width - 8));
        PixelGui.drawCompactControl(graphics, handleX, y, 8, height, isMouseOver(mouseX, mouseY));
        PixelGui.drawCenteredText(graphics, Minecraft.getInstance().font, getMessage(),
            x, y, width, height, 0xFFFFFFFF);
    }
}
