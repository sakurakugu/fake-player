package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.FakePlayerInventoryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
    private static final Identifier DROP_TAB_ICON =
        Identifier.fromNamespaceAndPath("fakeplayer", "textures/gui/drop_tab.png");
    private static final int TARGET_INVENTORY_HEIGHT = 159;
    private static final int HOTBAR_SELECTOR_TOP = 159;
    private static final int HOTBAR_SELECTOR_HEIGHT = 5;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int HOTBAR_SLOT_SPACING = 18;
    // 选择区整体比快捷栏第一格左移 1 像素，这样才对的齐。
    private static final int HOTBAR_SELECTOR_LEFT = 7;
    // 与快捷栏 18 像素格距一致，选择框覆盖整个格子。
    private static final int HOTBAR_SELECTOR_WIDTH = 18;
    private static final int VIEWER_SECTION_TOP = 164;
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
    private static final int DROP_PANEL_TOP = 8;
    private static final int DROP_PANEL_WIDTH = 94;
    private static final int DROP_PANEL_HEIGHT = 109;
    private static final int DROP_TAB_WIDTH = 21;
    private static final int DROP_TAB_HEIGHT = 24;

    private boolean dropPanelOpen;
    private boolean continuousDrop;
    private boolean percentageDrop;
    private int dropAmount = 1;
    private int dropPercentage = 100;
    private DropAmountSlider dropAmountSlider;
    private Button dropModeButton;
    private Button continuousDropButton;
    private Button executeDropButton;

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

        int panelLeft = leftPos + imageWidth;
        int panelTop = topPos + DROP_PANEL_TOP;
        Button tabButton = addRenderableWidget(new DropTabButton(
            panelLeft,
            panelTop,
            button -> setDropPanelOpen(!dropPanelOpen)
        ));
        tabButton.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.drop_tab")));

        dropModeButton = addRenderableWidget(
            new PanelButton(panelLeft + 70, panelTop + 26, 18, 18, dropModeMessage(), button -> toggleDropMode())
        );
        updateDropModeTooltip();
        dropAmountSlider = addRenderableWidget(new DropAmountSlider(
            panelLeft + 6,
            panelTop + 46,
            82,
            16
        ));
        continuousDropButton = addRenderableWidget(
            new PanelButton(panelLeft + 6, panelTop + 67, 82, 16, continuousDropMessage(), button -> {
                continuousDrop = !continuousDrop;
                continuousDropButton.setMessage(continuousDropMessage());
            })
        );
        executeDropButton = addRenderableWidget(
            new PanelButton(
                panelLeft + 6,
                panelTop + 88,
                82,
                16,
                Component.translatable("gui.fakeplayer.drop_execute"),
                button -> sendAction(FakePlayerInventoryMenu.dropActionId(
                    currentDropValue(), percentageDrop, continuousDrop))
            )
        );
        setDropPanelOpen(dropPanelOpen);
    }

    private Component continuousDropMessage() {
        return Component.translatable(continuousDrop
            ? "gui.fakeplayer.drop_continuous_on"
            : "gui.fakeplayer.drop_continuous_off");
    }

    private Component dropModeMessage() {
        return Component.literal(percentageDrop ? "%" : "#");
    }

    private void toggleDropMode() {
        percentageDrop = !percentageDrop;
        dropModeButton.setMessage(dropModeMessage());
        updateDropModeTooltip();
        dropAmountSlider.refreshValue();
    }

    private void updateDropModeTooltip() {
        dropModeButton.setTooltip(Tooltip.create(Component.translatable(percentageDrop
            ? "gui.fakeplayer.drop_mode_percentage"
            : "gui.fakeplayer.drop_mode_amount")));
    }

    private int currentDropValue() {
        return percentageDrop ? dropPercentage : dropAmount;
    }

    private void setDropPanelOpen(boolean open) {
        dropPanelOpen = open;
        dropModeButton.visible = open;
        dropAmountSlider.visible = open;
        continuousDropButton.visible = open;
        executeDropButton.visible = open;
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

    /** 使用自定义纹理作为紧凑标签。 */
    private final class DropTabButton extends Button {
        private DropTabButton(int x, int y, OnPress onPress) {
            super(x, y, DROP_TAB_WIDTH, DROP_TAB_HEIGHT, Component.translatable("gui.fakeplayer.drop_tab"),
                onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int iconX = getX() + 2;
            int iconY = getY() + 4;
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                DROP_TAB_ICON,
                iconX,
                iconY,
                0.0F,
                0.0F,
                16,
                16,
                16,
                16
            );
        }
    }

    /** 绘制参考模组使用的紧凑像素按钮，避免原版宽按钮挤占侧栏空间。 */
    private final class PanelButton extends Button {
        private PanelButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawCompactControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
            drawCenteredTextWithoutShadow(graphics, getMessage(), getX(), getY(), getWidth(), getHeight(),
                active ? 0xFFFFFFFF : 0xFF777777);
        }
    }

    /** 根据当前计量模式，将滑块位置映射到整数数量或百分比。 */
    private final class DropAmountSlider extends AbstractSliderButton {
        private DropAmountSlider(int x, int y, int width, int height) {
            super(
                x,
                y,
                width,
                height,
                Component.literal(Integer.toString(dropAmount)),
                (double) (dropAmount - 1) / (FakePlayerInventoryMenu.MAX_DROP_AMOUNT - 1)
            );
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(currentDropValue() + (percentageDrop ? "%" : "")));
        }

        @Override
        protected void applyValue() {
            int maximum = percentageDrop
                ? FakePlayerInventoryMenu.MAX_DROP_PERCENTAGE
                : FakePlayerInventoryMenu.MAX_DROP_AMOUNT;
            int selectedValue = 1 + (int) Math.round(value * (maximum - 1));
            if (percentageDrop) {
                dropPercentage = selectedValue;
            } else {
                dropAmount = selectedValue;
            }
        }

        private void refreshValue() {
            int maximum = percentageDrop
                ? FakePlayerInventoryMenu.MAX_DROP_PERCENTAGE
                : FakePlayerInventoryMenu.MAX_DROP_AMOUNT;
            setValue((double) (currentDropValue() - 1) / (maximum - 1));
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            drawSliderTrack(graphics, x, y, width, height);

            // 保留原版 8 像素宽、占满控件高度的滑块手柄，只改用纯色绘制。
            int handleX = x + (int) (value * (width - 8));
            drawSliderHandle(graphics, handleX, y, height, isMouseOver(mouseX, mouseY));
            drawCenteredTextWithoutShadow(graphics, getMessage(), x, y, width, height, 0xFFFFFFFF);
        }
    }

    private void drawCenteredTextWithoutShadow(
        GuiGraphicsExtractor graphics,
        Component message,
        int x,
        int y,
        int width,
        int height,
        int color
    ) {
        graphics.text(font, message, x + (width - font.width(message)) / 2, y + (height - 8) / 2,
            color, false);
    }

    private void drawCompactControl(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        boolean hovered
    ) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, hovered ? 0xFFFFFFFF : 0xFF373737);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, right - 1, y + 2, 0xFFAAAAAA);
        graphics.fill(x + 1, y + 1, x + 2, bottom - 1, 0xFFAAAAAA);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, 0xFF565656);
        graphics.fill(right - 2, y + 1, right - 1, bottom - 1, 0xFF565656);
        graphics.fill(x + 1, bottom - 2, x + 2, bottom - 1, 0xFF8B8B8B);
        graphics.fill(right - 2, y + 1, right - 1, y + 2, 0xFF8B8B8B);
    }

    private void drawSliderHandle(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int height,
        boolean hovered
    ) {
        drawCompactControl(graphics, x, y, 8, height, hovered);
    }

    /** 滑动条轨道使用无高光的深色内底，手柄仍保留原来的明暗边框。 */
    private void drawSliderTrack(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF404040);
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

        drawDropPanel(graphics);

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

    private void drawDropPanel(GuiGraphicsExtractor graphics) {
        int left = leftPos + imageWidth;
        int top = topPos + DROP_PANEL_TOP;
        int width = dropPanelOpen ? DROP_PANEL_WIDTH : DROP_TAB_WIDTH;
        int height = dropPanelOpen ? DROP_PANEL_HEIGHT : DROP_TAB_HEIGHT;
        drawTabBackground(graphics, left, top, width, height);

        if (dropPanelOpen) {
            graphics.text(font, Component.translatable("gui.fakeplayer.drop_panel_title"), left + 22, top + 8,
                0xFF404040, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.drop_amount"), left + 6, top + 31,
                0xFF404040, false);
        }
    }

    /** 左侧留出连接边，使展开页看起来是从容器边缘伸出的标签。 */
    private void drawTabBackground(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;
        // 左侧与物品栏直接相连，只绘制上、右、下三边的黑色外框。
        graphics.fill(left, top, right - 2, top + 1, 0xFF000000);
        graphics.fill(left, top + 1, right - 1, top + 2, 0xFF000000);
        graphics.fill(right - 1, top + 2, right, bottom - 2, 0xFF000000);
        graphics.fill(left, bottom - 2, right - 1, bottom - 1, 0xFF000000);
        graphics.fill(left, bottom - 1, right - 2, bottom, 0xFF000000);
        graphics.fill(left, top + 2, right - 1, bottom - 2, 0xFFC6C6C6);
        graphics.fill(left, top + 1, right - 2, top + 2, 0xFFFFFFFF);
        graphics.fill(right - 2, top + 2, right - 1, bottom - 2, 0xFF565656);
        graphics.fill(left, bottom - 2, right - 2, bottom - 1, 0xFF565656);
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
        graphics.text(font, playerInventoryTitle, 8, 167, -12566464, false);
    }
}
