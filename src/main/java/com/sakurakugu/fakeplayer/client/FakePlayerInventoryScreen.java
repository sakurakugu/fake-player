package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.FakePlayerInventoryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 绘制假人完整物品栏；末影箱使用原版三行容器界面。 */
public final class FakePlayerInventoryScreen extends AbstractContainerScreen<FakePlayerInventoryMenu> {
    private static final Identifier CONTAINER_BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier INVENTORY_BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int TARGET_INVENTORY_HEIGHT = 159;
    private static final int HOTBAR_SELECTOR_TOP = 159;
    private static final int HOTBAR_SELECTOR_HEIGHT = 5;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int HOTBAR_SLOT_SPACING = 18;
    // 选择区整体比快捷栏第一格左移 1 像素，这样才对的齐。
    private static final int HOTBAR_SELECTOR_LEFT = 7;
    // 与快捷栏 18 像素格距一致，选择框覆盖整个格子。
    private static final int HOTBAR_SELECTOR_WIDTH = 18;
    private static final int VIEWER_SECTION_TOP = 167;
    // 仅用于覆盖原版 2x2 合成区。
    private static final int CRAFTING_AREA_LEFT = 97;
    private static final int CRAFTING_AREA_TOP = 17;
    private static final int CRAFTING_AREA_WIDTH = 74;
    private static final int CRAFTING_AREA_HEIGHT = 36;

    // 这两个按钮刚好在副手所在的上方，然后和副手位置之间空一格
    private static final int ACTION_BUTTON_LEFT = 76;
    private static final int ACTION_BUTTON_TOP = 7;
    private static final int ACTION_BUTTON_WIDTH = 18;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int ACTION_BUTTON_GAP = 0; // 两个按钮之间的间距

    public FakePlayerInventoryScreen(FakePlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.screenWidth(), menu.screenHeight());
    }

    @Override
    protected void init() {
        super.init();
        if (menu.view() != FakePlayerInventoryMenu.View.INVENTORY) {
            return;
        }
        addRenderableWidget(
            new IconButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP,
                new ItemStack(Items.BARRIER),
                Component.translatable("gui.fakeplayer.remove"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_REMOVE)
            )
        );
        addRenderableWidget(
            new IconButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP + ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP,
                new ItemStack(Items.ENDER_CHEST),
                Component.translatable("gui.fakeplayer.open_ender_chest"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_ENDER_CHEST)
            )
        );
    }

    private void sendAction(int actionId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actionId);
        }
    }

    /** 用原版物品渲染图标按钮，文字仅作为悬浮提示和无障碍说明。 */
    private static final class IconButton extends Button {
        private final ItemStack icon;

        private IconButton(int x, int y, ItemStack icon, Component tooltip, OnPress onPress) {
            super(x, y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT, tooltip, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();
            boolean hovered = mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom;
            int color = hovered ? 0xFFC0C0C0 : 0xFF8B8B8B;

            // 使用原版物品栏的凹槽边框：内部 16x16，整体 18x18。
            graphics.fill(x, y, right, y + 1, 0xFF373737);
            graphics.fill(x, y + 1, x + 1, bottom, 0xFF373737);
            graphics.fill(x + 1, bottom - 1, right, bottom, 0xFFFFFFFF);
            graphics.fill(right - 1, y + 1, right, bottom, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, right - 1, bottom - 1, color);
            graphics.fill(x, bottom - 1, x + 1, bottom, 0xFF8B8B8B);
            graphics.fill(right - 1, y, right, y + 1, 0xFF8B8B8B);
            graphics.item(icon, getX() + 1, getY() + 1);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            // 与原版 ContainerScreen 的三行容器背景保持一致。
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                CONTAINER_BACKGROUND,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                imageWidth,
                71,
                256,
                256
            );
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                CONTAINER_BACKGROUND,
                leftPos,
                topPos + 71,
                0.0F,
                126.0F,
                imageWidth,
                96,
                256,
                256
            );
            return;
        }

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            INVENTORY_BACKGROUND,
            leftPos,
            topPos,
            0.0F,
            0.0F,
            176,
            TARGET_INVENTORY_HEIGHT,
            256,
            256
        );
        // 仅清除原版 2x2 合成区域。
        graphics.fill(
            leftPos + CRAFTING_AREA_LEFT,
            topPos + CRAFTING_AREA_TOP,
            leftPos + CRAFTING_AREA_LEFT + CRAFTING_AREA_WIDTH,
            topPos + CRAFTING_AREA_TOP + CRAFTING_AREA_HEIGHT,
            0xFFC6C6C6
        );
        // 裁掉假人背包底部边框，再像原版箱子一样拼接操作者背包区域。
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            CONTAINER_BACKGROUND,
            leftPos,
            topPos + VIEWER_SECTION_TOP,
            0.0F,
            126.0F,
            176,
            96,
            256,
            256
        );
        graphics.fill(
            leftPos + 1,
            topPos + TARGET_INVENTORY_HEIGHT,
            leftPos + imageWidth - 1,
            topPos + VIEWER_SECTION_TOP,
            0xFFC6C6C6
        );

        for (int slot = 0; slot < HOTBAR_SLOT_COUNT; slot++) {
            int x = leftPos + HOTBAR_SELECTOR_LEFT + slot * HOTBAR_SLOT_SPACING;
            int y = topPos + HOTBAR_SELECTOR_TOP;
            boolean hovered = mouseX >= x && mouseX < x + HOTBAR_SELECTOR_WIDTH
                && mouseY >= y && mouseY < y + HOTBAR_SELECTOR_HEIGHT;
            int color = slot == menu.selectedHotbarSlot()
                ? hovered ? 0xFF5DDB6C : 0xFF36B54A
                : hovered ? 0xFF8A8A8A : 0xFF5A5A5A;
            // 使用原版选择框的明暗边框；每个选择框独立绘制，不与相邻选择框共线。
            int right = x + HOTBAR_SELECTOR_WIDTH;
            int bottom = y + HOTBAR_SELECTOR_HEIGHT;
            graphics.fill(x, y, right, y + 1, 0xFF373737);
            graphics.fill(x, y + 1, x + 1, bottom, 0xFF373737);
            graphics.fill(x + 1, bottom - 1, right, bottom, 0xFFFFFFFF);
            graphics.fill(right - 1, y + 1, right, bottom, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, right - 1, bottom - 1, color);
            graphics.fill(x, bottom - 1, x + 1, bottom, 0xFF8B8B8B);
            graphics.fill(right - 1, y, right, y + 1, 0xFF8B8B8B);
        }

        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(menu.targetEntityId());
            if (entity instanceof LivingEntity livingEntity) {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    leftPos + 26,
                    topPos + 8,
                    leftPos + 75,
                    topPos + 78,
                    30,
                    0.0625F,
                    mouseX,
                    mouseY,
                    livingEntity
                );
            }
        }
        drawSelectorAreaSideBorders(graphics);
    }

    /** 给快捷栏选择区左右两侧绘制外边框，左侧黑边内为高光，右侧黑边内为阴影。 */
    private void drawSelectorAreaSideBorders(GuiGraphicsExtractor graphics) {
        int top = topPos + TARGET_INVENTORY_HEIGHT;
        int bottom = topPos + VIEWER_SECTION_TOP;
        graphics.fill(leftPos, top, leftPos + 1, bottom, 0xFF000000);
        graphics.fill(leftPos + 1, top, leftPos + 3, bottom, 0xFFFFFFFF);
        graphics.fill(leftPos + imageWidth - 3, top, leftPos + imageWidth - 1, bottom, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 1, top, leftPos + imageWidth, bottom, 0xFF000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.view() == FakePlayerInventoryMenu.View.INVENTORY && event.button() == 0) {
            int relativeX = (int) event.x() - leftPos;
            int relativeY = (int) event.y() - topPos;
            if (relativeY >= HOTBAR_SELECTOR_TOP && relativeY < HOTBAR_SELECTOR_TOP + HOTBAR_SELECTOR_HEIGHT) {
                for (int slot = 0; slot < HOTBAR_SLOT_COUNT; slot++) {
                    int x = HOTBAR_SELECTOR_LEFT + slot * HOTBAR_SLOT_SPACING;
                    if (relativeX >= x && relativeX < x + HOTBAR_SELECTOR_WIDTH) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            super.extractLabels(graphics, mouseX, mouseY);
            return;
        }

        graphics.text(font, title, 97, 6, -12566464, false);
        graphics.text(font, playerInventoryTitle, 8, 170, -12566464, false);
    }
}
