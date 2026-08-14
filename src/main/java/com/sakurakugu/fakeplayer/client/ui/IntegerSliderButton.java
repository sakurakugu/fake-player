package com.sakurakugu.fakeplayer.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;

/** 将滑块位置映射到闭区间整数值的纯色滑动条。 */
public final class IntegerSliderButton extends SolidSliderButton {
    private int minimum;
    private int maximum;
    private int selectedValue;
    private final IntFunction<Component> messageFactory;
    private final IntConsumer onValueChanged;

    public IntegerSliderButton(
        int x,
        int y,
        int width,
        int height,
        int minimum,
        int maximum,
        int selectedValue,
        IntFunction<Component> messageFactory,
        IntConsumer onValueChanged
    ) {
        super(x, y, width, height, Component.empty(), normalize(minimum, maximum, selectedValue));
        this.minimum = minimum;
        this.maximum = maximum;
        this.selectedValue = Math.clamp(selectedValue, minimum, maximum);
        this.messageFactory = messageFactory;
        this.onValueChanged = onValueChanged;
        updateMessage();
    }

    public int selectedValue() {
        return selectedValue;
    }

    /** 更新取值范围和当前值，但不触发用户操作回调。 */
    public void setRange(int minimum, int maximum, int selectedValue) {
        validateRange(minimum, maximum);
        this.minimum = minimum;
        this.maximum = maximum;
        this.selectedValue = Math.clamp(selectedValue, minimum, maximum);
        value = normalize(minimum, maximum, this.selectedValue);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (messageFactory != null) {
            setMessage(messageFactory.apply(selectedValue));
        }
    }

    @Override
    protected void applyValue() {
        int nextValue = minimum + (int) Math.round(value * (maximum - minimum));
        if (nextValue == selectedValue) {
            return;
        }
        selectedValue = nextValue;
        onValueChanged.accept(selectedValue);
    }

    private static double normalize(int minimum, int maximum, int selectedValue) {
        validateRange(minimum, maximum);
        return (double) (Math.clamp(selectedValue, minimum, maximum) - minimum) / (maximum - minimum);
    }

    private static void validateRange(int minimum, int maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("滑动条最大值必须大于最小值");
        }
    }
}
