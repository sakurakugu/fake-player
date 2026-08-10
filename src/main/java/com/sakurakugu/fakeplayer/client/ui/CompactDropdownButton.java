package com.sakurakugu.fakeplayer.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 适合窄面板的紧凑下拉选择器。 */
public final class CompactDropdownButton<T> extends Button {
    private static final int OPTION_HEIGHT = 16;
    private static final int TEXT_PADDING = 4;
    private final List<T> options;
    private final Function<T, Component> labelFactory;
    private final Consumer<T> onSelected;
    private T selected;
    private boolean open;

    public CompactDropdownButton(
        int x,
        int y,
        int width,
        int height,
        List<T> options,
        T selected,
        Function<T, Component> labelFactory,
        Consumer<T> onSelected
    ) {
        super(x, y, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
        if (options.isEmpty() || !options.contains(selected)) {
            throw new IllegalArgumentException("下拉选择器必须包含初始选项");
        }
        this.options = List.copyOf(options);
        this.selected = selected;
        this.labelFactory = labelFactory;
        this.onSelected = onSelected;
        updateMessage();
    }

    public T selected() {
        return selected;
    }

    public boolean isOpen() {
        return open;
    }

    public void setSelected(T selected) {
        if (options.contains(selected)) {
            this.selected = selected;
            updateMessage();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawCompactControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        graphics.enableScissor(getX() + TEXT_PADDING, getY(), getX() + getWidth() - 10, getY() + getHeight());
        graphics.text(Minecraft.getInstance().font, getMessage(),
            getX() + TEXT_PADDING, getY() + (getHeight() - 8) / 2,
            active ? 0xFFFFFFFF : 0xFF777777, false);
        graphics.disableScissor();
        graphics.text(Minecraft.getInstance().font, Component.literal(open ? "▲" : "▼"),
            getX() + getWidth() - 9, getY() + (getHeight() - 8) / 2 + 1,
            active ? 0xFFFFFFFF : 0xFF777777, false);
    }

    /** 在屏幕的最后绘制阶段调用，确保选项列表不会被其他面板遮住。 */
    public void extractPopup(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        int graphicsOriginX,
        int graphicsOriginY
    ) {
        if (!open || !visible) {
            return;
        }
        int left = getX() - graphicsOriginX;
        int top = getY() - graphicsOriginY + getHeight();
        int right = left + getWidth();
        int bottom = top + options.size() * OPTION_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xFF202326);
        graphics.outline(left, top, getWidth(), bottom - top, 0xFF8B8B8B);
        for (int index = 0; index < options.size(); index++) {
            int optionTop = top + index * OPTION_HEIGHT;
            int absoluteOptionTop = optionTop + graphicsOriginY;
            if (mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= absoluteOptionTop && mouseY < absoluteOptionTop + OPTION_HEIGHT) {
                graphics.fill(left + 1, optionTop + 1, right - 1, optionTop + OPTION_HEIGHT - 1, 0xFF3E5F4D);
            }
            graphics.text(Minecraft.getInstance().font, labelFactory.apply(options.get(index)),
                left + TEXT_PADDING, optionTop + (OPTION_HEIGHT - 8) / 2, 0xFFFFFFFF, false);
        }
    }

    /** 屏幕在调用父类点击处理前调用，用于接收控件本体之外的选项点击。 */
    public boolean popupMouseClicked(MouseButtonEvent event) {
        if (!open || event.button() != 0) {
            return false;
        }
        int left = getX();
        int top = getY() + getHeight();
        int right = left + getWidth();
        int bottom = top + options.size() * OPTION_HEIGHT;
        if (event.x() >= left && event.x() < right && event.y() >= top && event.y() < bottom) {
            int index = (int) ((event.y() - top) / OPTION_HEIGHT);
            selected = options.get(index);
            updateMessage();
            open = false;
            onSelected.accept(selected);
            return true;
        }
        if (!isMouseOver(event.x(), event.y())) {
            open = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        open = !open;
        return true;
    }

    private void updateMessage() {
        setMessage(labelFactory.apply(selected));
    }
}
