package com.sakurakugu.fakeplayer.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 绘制并处理九格快捷栏选择区。 */
public final class HotbarSelector extends Button {
    public static final int SLOT_COUNT = 9;
    public static final int SLOT_WIDTH = 18;

    private final IntSupplier selectedSlot;
    private final IntConsumer onSelected;

    public HotbarSelector(
        int x,
        int y,
        int height,
        IntSupplier selectedSlot,
        IntConsumer onSelected
    ) {
        super(x, y, SLOT_COUNT * SLOT_WIDTH, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.selectedSlot = selectedSlot;
        this.onSelected = onSelected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int slotX = getX() + slot * SLOT_WIDTH;
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_WIDTH
                && mouseY >= getY() && mouseY < getY() + getHeight();
            int color = slot == selectedSlot.getAsInt()
                ? hovered ? 0xFF5DDB6C : 0xFF36B54A
                : hovered ? 0xFF8A8A8A : 0xFF5A5A5A;
            PixelGui.drawInventorySlotBackground(
                graphics, slotX, getY(), SLOT_WIDTH, getHeight(), color);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        int slot = (int) (event.x() - getX()) / SLOT_WIDTH;
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        onSelected.accept(slot);
        return true;
    }
}
