package com.sakurakugu.fakeplayer.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 使用物品栏槽位样式，并用物品或纹理渲染图标的按钮。 */
/** 仅限用到有物品栏的区域，用到其他区域会很丑 */
public final class InventorySlotButton extends Button {
    private static final int SIZE = 18;

    private final ItemStack icon;
    private final Identifier textureIcon;
    private final ItemStack overlay;

    public InventorySlotButton(int x, int y, ItemStack icon, Component tooltip, OnPress onPress) {
        this(x, y, icon, null, null, tooltip, onPress);
    }

    public InventorySlotButton(int x, int y, Identifier textureIcon, Component tooltip, OnPress onPress) {
        this(x, y, null, null, textureIcon, tooltip, onPress);
    }

    public InventorySlotButton(
        int x, int y, Identifier textureIcon, ItemStack overlay, Component tooltip, OnPress onPress
    ) {
        this(x, y, null, overlay, textureIcon, tooltip, onPress);
    }

    private InventorySlotButton(
        int x, int y, ItemStack icon, ItemStack overlay, Identifier textureIcon,
        Component tooltip, OnPress onPress
    ) {
        super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.overlay = overlay;
        this.textureIcon = textureIcon;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int color = isMouseOver(mouseX, mouseY) ? 0xFFC0C0C0 : 0xFF8B8B8B;

        // 使用原版物品栏的凹槽边框：内部 16x16，整体 18x18。
        PixelGui.drawInventorySlotBackground(graphics, x, y, getWidth(), getHeight(), color);
        if (textureIcon != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureIcon, x + 1, y + 1,
                0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            graphics.item(icon, x + 1, y + 1);
        }
        if (overlay != null) {
            graphics.item(overlay, x + 2, y + 2);
        }
    }
}
