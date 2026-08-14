package com.sakurakugu.fakeplayer.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 可复用的像素图标，负责自身尺寸和绘制。 */
public enum PixelGlyph {
    SAVE(10, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            graphics.fill(x, y, x + 10, y + 12, 0xFF555555);
            graphics.fill(x + 2, y + 1, x + 7, y + 5, color);
            graphics.fill(x + 2, y + 8, x + 8, y + 12, color);
        }
    },
    CLOSE(10, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            for (int offset = 0; offset < 7; offset++) {
                graphics.fill(x + 1 + offset, y + 2 + offset, x + 3 + offset, y + 4 + offset, color);
                graphics.fill(x + 7 - offset, y + 2 + offset, x + 9 - offset, y + 4 + offset, color);
            }
        }
    };

    private final int width;
    private final int height;

    PixelGlyph(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void drawCentered(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        draw(graphics, x + (width - this.width) / 2, y + (height - this.height) / 2, color);
    }

    protected abstract void draw(GuiGraphicsExtractor graphics, int x, int y, int color);
}
