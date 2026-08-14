package com.sakurakugu.fakeplayer.client.ui;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 视角或身体朝向的二维拖动控制器。 */
public final class RotationPad extends Button {
    private static final float MAX_HEAD_YAW_OFFSET = 50.0F;

    private final Mode mode;
    private final IntSupplier pitch;
    private final IntSupplier yaw;
    private final IntSupplier bodyYaw;
    private final BooleanSupplier bodyFollowsHead;
    private final IntConsumer onBodyYawChanged;
    private final ViewRotationConsumer onViewRotationChanged;
    private boolean dragging;
    private boolean draggingOutsideHorizontally;
    private float dragStartBodyYaw;
    private float dragYawOffset;

    public RotationPad(
        int x,
        int y,
        int size,
        Mode mode,
        IntSupplier pitch,
        IntSupplier yaw,
        IntSupplier bodyYaw,
        BooleanSupplier bodyFollowsHead,
        IntConsumer onBodyYawChanged,
        ViewRotationConsumer onViewRotationChanged
    ) {
        super(x, y, size, size, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.mode = mode;
        this.pitch = pitch;
        this.yaw = yaw;
        this.bodyYaw = bodyYaw;
        this.bodyFollowsHead = bodyFollowsHead;
        this.onBodyYawChanged = onBodyYawChanged;
        this.onViewRotationChanged = onViewRotationChanged;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateTooltip();
        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2;
        int radius = getWidth() / 2 - 2;
        drawBorder(graphics, centerX, centerY, radius);

        int handleX;
        int handleY;
        if (mode == Mode.BODY) {
            double angle = Math.toRadians(bodyYaw.getAsInt() + 90);
            handleX = centerX + (int) Math.round(Math.cos(angle) * radius);
            handleY = centerY + (int) Math.round(Math.sin(angle) * radius);
        } else {
            float headOffset = bodyFollowsHead.getAsBoolean() && dragging
                ? dragYawOffset
                : wrapDegrees(yaw.getAsInt() - bodyYaw.getAsInt());
            handleX = centerX + Math.round(headOffset * (radius - 3) / MAX_HEAD_YAW_OFFSET);
            if (bodyFollowsHead.getAsBoolean() && draggingOutsideHorizontally) {
                handleX = centerX + (dragYawOffset < 0.0F ? -radius : radius);
            }
            handleY = centerY + Math.round(pitch.getAsInt() * (radius - 3) / 90.0F);
        }
        graphics.fill(handleX - 3, handleY - 3, handleX + 4, handleY + 4, 0xFFFFFFFF);
        graphics.fill(handleX - 2, handleY - 2, handleX + 3, handleY + 3, 0xFFC6C6C6);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        dragging = true;
        dragStartBodyYaw = bodyYaw.getAsInt();
        updateValue(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        updateValue(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        draggingOutsideHorizontally = false;
        return super.mouseReleased(event);
    }

    private void drawBorder(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius) {
        if (mode == Mode.BODY) {
            for (int angle = 0; angle < 360; angle += 3) {
                int x = centerX + Math.round((float) Math.cos(Math.toRadians(angle)) * radius);
                int y = centerY + Math.round((float) Math.sin(Math.toRadians(angle)) * radius);
                graphics.fill(x, y, x + 2, y + 2, 0xFF555555);
            }
            return;
        }
        int left = centerX - radius;
        int top = centerY - radius;
        int right = centerX + radius + 1;
        int bottom = centerY + radius + 1;
        graphics.fill(left, top, right, top + 2, 0xFF555555);
        graphics.fill(left, bottom - 2, right, bottom, 0xFF555555);
        graphics.fill(left, top + 2, left + 2, bottom - 2, 0xFF555555);
        graphics.fill(right - 2, top + 2, right, bottom - 2, 0xFF555555);
    }

    private void updateValue(double mouseX, double mouseY) {
        double centerX = getX() + getWidth() / 2.0;
        double centerY = getY() + getHeight() / 2.0;
        double deltaX = mouseX - centerX;
        double deltaY = mouseY - centerY;
        double rawDeltaX = deltaX;
        double radius = getWidth() / 2.0 - 3;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (mode == Mode.BODY && length > 0) {
            deltaX *= radius / length;
            deltaY *= radius / length;
        } else if (mode == Mode.VIEW) {
            deltaX = Math.clamp(deltaX, -radius, radius);
            deltaY = Math.clamp(deltaY, -radius, radius);
        }
        if (mode == Mode.BODY) {
            int selectedYaw = (int) Math.round(Math.toDegrees(Math.atan2(deltaY, deltaX)) - 90);
            onBodyYawChanged.accept(selectedYaw);
            return;
        }
        dragYawOffset = (float) (deltaX / radius * MAX_HEAD_YAW_OFFSET);
        draggingOutsideHorizontally = bodyFollowsHead.getAsBoolean() && Math.abs(rawDeltaX) > radius;
        float requestedYawOffset = bodyFollowsHead.getAsBoolean()
            ? Math.clamp((float) (rawDeltaX / radius * MAX_HEAD_YAW_OFFSET), -179.0F, 179.0F)
            : dragYawOffset;
        float yawBase = bodyFollowsHead.getAsBoolean() ? dragStartBodyYaw : bodyYaw.getAsInt();
        int selectedYaw = Math.round(yawBase + requestedYawOffset);
        int selectedPitch = (int) Math.round(deltaY / radius * 90.0);
        onViewRotationChanged.accept(selectedPitch, selectedYaw);
    }

    private void updateTooltip() {
        if (mode == Mode.BODY) {
            setTooltip(Tooltip.create(Component.translatable(
                "gui.fakeplayer.look.direction_tooltip",
                bodyYaw.getAsInt(),
                Math.round(wrapDegrees(yaw.getAsInt() - bodyYaw.getAsInt())),
                bodyBearing(bodyYaw.getAsInt())
            )));
            return;
        }
        setTooltip(Tooltip.create(Component.translatable(
            "gui.fakeplayer.look.view_tooltip", pitch.getAsInt(), yaw.getAsInt())));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    /** 将 Minecraft 偏航角转换为易读的象限方位。 */
    private static Component bodyBearing(float bodyYaw) {
        int yaw = Math.round(wrapDegrees(bodyYaw));
        return switch (yaw) {
            case 0 -> Component.translatable("gui.fakeplayer.look.bearing.south");
            case -90 -> Component.translatable("gui.fakeplayer.look.bearing.east");
            case 90 -> Component.translatable("gui.fakeplayer.look.bearing.west");
            case -180 -> Component.translatable("gui.fakeplayer.look.bearing.north");
            default -> {
                if (yaw < -90) {
                    yield Component.translatable("gui.fakeplayer.look.bearing.east_north", -yaw - 90);
                }
                if (yaw < 0) {
                    yield Component.translatable("gui.fakeplayer.look.bearing.east_south", yaw + 90);
                }
                if (yaw < 90) {
                    yield Component.translatable("gui.fakeplayer.look.bearing.west_south", 90 - yaw);
                }
                yield Component.translatable("gui.fakeplayer.look.bearing.west_north", yaw - 90);
            }
        };
    }

    public enum Mode {
        VIEW,
        BODY
    }

    @FunctionalInterface
    public interface ViewRotationConsumer {
        void accept(int pitch, int yaw);
    }
}
