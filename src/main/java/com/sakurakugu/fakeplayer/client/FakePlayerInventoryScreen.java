package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.FakePlayerInventoryMenu;
import com.sakurakugu.fakeplayer.client.ui.CompactButton;
import com.sakurakugu.fakeplayer.client.ui.CompactSliderButton;
import com.sakurakugu.fakeplayer.client.ui.IconButton;
import com.sakurakugu.fakeplayer.client.ui.IconTabButton;
import com.sakurakugu.fakeplayer.client.ui.OverlayPanelManager;
import com.sakurakugu.fakeplayer.client.ui.PixelGui;
import com.sakurakugu.fakeplayer.client.ui.ToggleSwitchButton;
import com.sakurakugu.fakeplayer.client.ui.TransferButton;
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
    private static final Identifier DROP_TAB_ICON =
        Identifier.fromNamespaceAndPath("fakeplayer", "textures/gui/drop_tab.png");
    public static final Identifier POSSESSION_ENTER_ICON =
        Identifier.fromNamespaceAndPath("fakeplayer", "textures/gui/possession_enter.png");
    public static final Identifier POSSESSION_EXIT_ICON =
        Identifier.fromNamespaceAndPath("fakeplayer", "textures/gui/possession_exit.png");
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
    // 普通管理页面不开放假人的 2x2 合成区。
    private static final int CRAFTING_AREA_LEFT = 97;
    private static final int CRAFTING_AREA_TOP = 17;
    private static final int CRAFTING_AREA_WIDTH = 74;
    private static final int CRAFTING_AREA_HEIGHT = 36;

    // 三个按钮纵向排列，附身按钮正好位于末影箱下方和副手槽上方。
    private static final int ACTION_BUTTON_LEFT = 76;
    private static final int ACTION_BUTTON_TOP = 7;
    private static final int ACTION_BUTTON_WIDTH = 18;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int ACTION_BUTTON_GAP = 0;
    private static final int DROP_PANEL_TOP = 8;
    private static final int DROP_PANEL_WIDTH = 94;
    private static final int DROP_PANEL_HEIGHT = 109;
    private static final int DROP_TAB_WIDTH = 21;
    private static final int DROP_TAB_HEIGHT = 24;
    private static final int TRANSFER_BUTTON_LEFT = 144;
    private static final int TRANSFER_BUTTON_TOP = 165;
    // 自动化标签放在 Q 键丢弃标签按钮下方。
    private static final int AUTOMATION_PANEL_TOP = DROP_PANEL_TOP + DROP_TAB_HEIGHT + 2;
    private static final int AUTOMATION_PANEL_WIDTH = 94;
    private static final int AUTOMATION_PANEL_HEIGHT = 97;
    private static final int AUTOMATION_BUTTON_HEIGHT = 16;
    private static final String[] AUTOMATION_KEYS = {
        "auto_replenishment", "shulker_replenishment", "auto_replace_tools", "auto_fishing"
    };

    private final OverlayPanelManager panelManager = new OverlayPanelManager();
    private final OverlayPanelManager.Panel automationPanel = panelManager.addPanel();
    private final OverlayPanelManager.Panel dropPanel = panelManager.addPanel();
    private boolean continuousDrop;
    private boolean percentageDrop;
    private int dropAmount = 1;
    private int dropPercentage = 100;
    private DropAmountSlider dropAmountSlider;
    private Button dropModeButton;

    public FakePlayerInventoryScreen(FakePlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.screenWidth(), menu.screenHeight());
    }

    @Override
    protected void init() {
        super.init();
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            return;
        }
        if (menu.view() == FakePlayerInventoryMenu.View.POSSESSED_INVENTORY) {
            addRenderableWidget(
                new IconButton(
                    leftPos + ACTION_BUTTON_LEFT,
                    topPos + ACTION_BUTTON_TOP + (ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP) * 2,
                    POSSESSION_EXIT_ICON,
                    Component.translatable("gui.fakeplayer.stop_possessing"),
                    button -> sendAction(FakePlayerInventoryMenu.ACTION_POSSESS)
                )
            );
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
        IconButton possessButton = addRenderableWidget(
            new IconButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP + (ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP) * 2,
                menu.possessedByViewer() ? POSSESSION_EXIT_ICON : POSSESSION_ENTER_ICON,
                menu.targetOccupied() && !menu.possessedByViewer() ? new ItemStack(Items.BARRIER) : null,
                Component.translatable(menu.possessedByViewer()
                    ? "gui.fakeplayer.stop_possessing"
                    : menu.targetOccupied() ? "gui.fakeplayer.possess_disabled" : "gui.fakeplayer.possess"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_POSSESS)
            )
        );
        if (menu.targetOccupied() && !menu.possessedByViewer()) {
            possessButton.active = false;
        }
        addRenderableWidget(
            new IconButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP + ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP,
                new ItemStack(Items.ENDER_CHEST),
                Component.translatable("gui.fakeplayer.open_ender_chest"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_ENDER_CHEST)
            )
        );
        addRenderableWidget(new TransferButton(
            leftPos + TRANSFER_BUTTON_LEFT,
            topPos + TRANSFER_BUTTON_TOP,
            TransferButton.Direction.TO_CONTAINER,
            (transferAll, includeHotbar) -> sendAction(
                transferActionId(true, transferAll, includeHotbar))
        ));
        addRenderableWidget(new TransferButton(
            leftPos + TRANSFER_BUTTON_LEFT + TransferButton.SIZE,
            topPos + TRANSFER_BUTTON_TOP,
            TransferButton.Direction.TO_INVENTORY,
            (transferAll, includeHotbar) -> sendAction(
                transferActionId(false, transferAll, includeHotbar))
        ));

        int automationLeft = leftPos + imageWidth;
        int automationTop = topPos + AUTOMATION_PANEL_TOP + 21;
        ToggleSwitchButton[] automationButtons = new ToggleSwitchButton[AUTOMATION_KEYS.length];
        for (int index = 0; index < AUTOMATION_KEYS.length; index++) {
            int actionId = FakePlayerInventoryMenu.ACTION_AUTO_REPLENISHMENT + index;
            int automationIndex = index;
            automationButtons[index] = addRenderableWidget(new ToggleSwitchButton(
                automationLeft + 6,
                automationTop + index * (AUTOMATION_BUTTON_HEIGHT + 2),
                AUTOMATION_PANEL_WIDTH - 12,
                AUTOMATION_BUTTON_HEIGHT,
                Component.translatable("gui.fakeplayer.automation." + AUTOMATION_KEYS[index]),
                () -> menu.automationEnabled(automationIndex),
                button -> sendAction(actionId)
            ));
        }

        int panelLeft = leftPos + imageWidth;
        int panelTop = topPos + DROP_PANEL_TOP;
        Button automationTabButton = addRenderableWidget(new IconTabButton(
            panelLeft,
            topPos + AUTOMATION_PANEL_TOP,
            DROP_TAB_WIDTH,
            DROP_TAB_HEIGHT,
            new ItemStack(Items.REPEATER),
            Component.translatable("gui.fakeplayer.automation.title"),
            button -> automationPanel.toggle()
        ));
        automationTabButton.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.automation.title")));

        // 控件始终在背景之后绘制，因此用覆盖层让展开的 Q 面板遮住下方自动化控件。
        DropPanelOverlay dropPanelOverlay = addRenderableWidget(new DropPanelOverlay(panelLeft, panelTop));

        Button tabButton = addRenderableWidget(new IconTabButton(
            panelLeft,
            panelTop,
            DROP_TAB_WIDTH,
            DROP_TAB_HEIGHT,
            DROP_TAB_ICON,
            Component.translatable("gui.fakeplayer.drop_tab"),
            button -> dropPanel.toggle()
        ));
        tabButton.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.drop_tab")));

        dropModeButton = addRenderableWidget(
            new CompactButton(panelLeft + 74, panelTop + 28, 14, 14, dropModeMessage(), button -> toggleDropMode())
        );
        updateDropModeTooltip();
        dropAmountSlider = addRenderableWidget(new DropAmountSlider(
            panelLeft + 6,
            panelTop + 46,
            82,
            16
        ));
        ToggleSwitchButton continuousDropButton = addRenderableWidget(
            new ToggleSwitchButton(panelLeft + 6, panelTop + 67, 82, 16,
                Component.translatable("gui.fakeplayer.drop_continuous"), () -> continuousDrop, button -> {
                continuousDrop = !continuousDrop;
            })
        );
        Button executeDropButton = addRenderableWidget(
            new CompactButton(
                panelLeft + 6,
                panelTop + 88,
                82,
                16,
                Component.translatable("gui.fakeplayer.drop_execute"),
                button -> sendAction(FakePlayerInventoryMenu.dropActionId(
                    currentDropValue(), percentageDrop, continuousDrop))
            )
        );
        automationPanel.bind(automationTabButton, automationButtons);
        dropPanel.bind(tabButton, dropPanelOverlay, dropModeButton, dropAmountSlider,
            continuousDropButton, executeDropButton);
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

    private void sendAction(int actionId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actionId);
        }
    }

    private static int transferActionId(boolean toTarget, boolean transferAll, boolean includeHotbar) {
        if (toTarget) {
            return includeHotbar
                ? transferAll
                    ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_ALL_WITH_HOTBAR
                    : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_MATCHING_WITH_HOTBAR
                : transferAll
                    ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_ALL
                    : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_MATCHING;
        }
        return includeHotbar
            ? transferAll
                ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_ALL_WITH_HOTBAR
                : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_MATCHING_WITH_HOTBAR
            : transferAll
                ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_ALL
                : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_MATCHING;
    }

    /** 让上方面板遮住下方的。 */
    private final class DropPanelOverlay extends Button {
        private DropPanelOverlay(int x, int y) {
            super(x, y, DROP_PANEL_WIDTH, DROP_PANEL_HEIGHT, Component.empty(), button -> {}, DEFAULT_NARRATION);
            active = false;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawDropPanel(graphics);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // 左上角由丢弃标签按钮处理，覆盖层不能抢先吞掉它的点击。
            boolean overTab = mouseX >= getX() && mouseX < getX() + DROP_TAB_WIDTH
                && mouseY >= getY() && mouseY < getY() + DROP_TAB_HEIGHT;
            return !overTab && super.isMouseOver(mouseX, mouseY);
        }
    }

    /** 根据当前计量模式，将滑块位置映射到整数数量或百分比。 */
    private final class DropAmountSlider extends CompactSliderButton {
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

        if (menu.view() == FakePlayerInventoryMenu.View.POSSESSED_INVENTORY) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                INVENTORY_BACKGROUND,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                imageWidth,
                imageHeight,
                256,
                256
            );
            drawTargetEntity(graphics, mouseX, mouseY);
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
        clearCraftingArea(graphics);
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

        drawAutomationPanel(graphics);
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

        drawTargetEntity(graphics, mouseX, mouseY);
        drawSelectorAreaSideBorders(graphics);
    }

    private void clearCraftingArea(GuiGraphicsExtractor graphics) {
        graphics.fill(
            leftPos + CRAFTING_AREA_LEFT,
            topPos + CRAFTING_AREA_TOP,
            leftPos + CRAFTING_AREA_LEFT + CRAFTING_AREA_WIDTH,
            topPos + CRAFTING_AREA_TOP + CRAFTING_AREA_HEIGHT,
            0xFFC6C6C6
        );
    }

    private void drawTargetEntity(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (minecraft.level == null) {
            return;
        }
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

    private void drawDropPanel(GuiGraphicsExtractor graphics) {
        int left = leftPos + imageWidth;
        int top = topPos + DROP_PANEL_TOP;
        int width = dropPanel.isOpen() ? DROP_PANEL_WIDTH : DROP_TAB_WIDTH;
        int height = dropPanel.isOpen() ? DROP_PANEL_HEIGHT : DROP_TAB_HEIGHT;
        PixelGui.drawTabBackground(graphics, left, top, width, height);

        if (dropPanel.isOpen()) {
            graphics.text(font, Component.translatable("gui.fakeplayer.drop_panel_title"), left + 22, top + 8,
                0xFF404040, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.drop_amount"), left + 6, top + 31,
                0xFF404040, false);
        }
    }

    private void drawAutomationPanel(GuiGraphicsExtractor graphics) {
        int left = leftPos + imageWidth;
        int top = topPos + AUTOMATION_PANEL_TOP;
        int width = automationPanel.isOpen() ? AUTOMATION_PANEL_WIDTH : DROP_TAB_WIDTH;
        int height = automationPanel.isOpen() ? AUTOMATION_PANEL_HEIGHT : DROP_TAB_HEIGHT;
        PixelGui.drawTabBackground(graphics, left, top, width, height);
        if (automationPanel.isOpen()) {
            // 标签图标仍覆盖在面板左侧，因此标题从图标右侧开始绘制。
            graphics.text(font, Component.translatable("gui.fakeplayer.automation.title"), left + 22, top + 8,
                0xFF404040, false);
        }
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
        if (menu.view() == FakePlayerInventoryMenu.View.POSSESSED_INVENTORY) {
            return;
        }

        graphics.text(font, title, 97, 6, -12566464, false);
        graphics.text(font, playerInventoryTitle, 8, 167, -12566464, false);
    }
}
