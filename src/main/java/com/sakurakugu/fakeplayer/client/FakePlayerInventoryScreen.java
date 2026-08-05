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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int CRAFTING_AREA_WIDTH = 76;
    private static final int CRAFTING_AREA_HEIGHT = 55;
    private static final int CONTROL_LEFT = 97;
    private static final int CONTROL_TOP = 18;
    private static final int CONTROL_SIZE = 18;
    private static final int SNEAK_BUTTON_LEFT = 155;

    // 三个按钮纵向排列，附身按钮正好位于末影箱下方和副手槽上方。
    private static final int ACTION_BUTTON_LEFT = 76;
    private static final int ACTION_BUTTON_TOP = 7;
    private static final int ACTION_BUTTON_WIDTH = 18;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int ACTION_BUTTON_GAP = 0;
    private static final int DROP_PANEL_WIDTH = 94;
    private static final int DROP_PANEL_HEIGHT = 109;
    private static final int DROP_TAB_WIDTH = 21;
    private static final int DROP_TAB_HEIGHT = 24;
    private static final int AIM_PANEL_TOP = 8;
    private static final int CONTINUOUS_PANEL_TOP = AIM_PANEL_TOP + DROP_TAB_HEIGHT + 2;
    private static final int DROP_PANEL_TOP = CONTINUOUS_PANEL_TOP + DROP_TAB_HEIGHT + 2;
    private static final int TRANSFER_BUTTON_LEFT = 144;
    private static final int TRANSFER_BUTTON_TOP = 165;
    private static final int ENDER_CHEST_TRANSFER_BUTTON_TOP = 73;
    // 侧栏依次放置视觉朝向、持续控制、Q 键丢弃和自动化标签。
    private static final int AUTOMATION_PANEL_TOP = DROP_PANEL_TOP + DROP_TAB_HEIGHT + 2;
    private static final int AUTOMATION_PANEL_WIDTH = 94;
    private static final int AUTOMATION_PANEL_HEIGHT = 97;
    private static final int AUTOMATION_BUTTON_HEIGHT = 16;
    private static final int CONTINUOUS_PANEL_WIDTH = 94;
    private static final int CONTINUOUS_PANEL_HEIGHT = 219;
    private static final int AIM_PANEL_WIDTH = 94;
    private static final int AIM_PANEL_HEIGHT = 234;
    private static final int AIM_PAD_SIZE = 62;
    private static final float MAX_HEAD_YAW_OFFSET = 50.0F;
    private static final int CONTINUOUS_BUTTON_HEIGHT = 16;
    private static final int CONTINUOUS_SLIDER_HEIGHT = 14;
    private static final String[] AUTOMATION_KEYS = {
        "auto_replenishment", "shulker_replenishment", "auto_replace_tools", "auto_fishing"
    };
    private static final String[] CONTINUOUS_KEYS = {
        "move_forward", "move_backward", "move_left", "move_right", "attack", "use", "jump"
    };
    private static final int[] CONTINUOUS_ACTIONS = {
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_FORWARD,
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_BACKWARD,
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_LEFT,
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_RIGHT,
        FakePlayerInventoryMenu.ACTION_TOGGLE_ATTACK,
        FakePlayerInventoryMenu.ACTION_TOGGLE_USE,
        FakePlayerInventoryMenu.ACTION_TOGGLE_JUMP
    };

    private final OverlayPanelManager panelManager = new OverlayPanelManager();
    // 按界面从上到下注册，展开的面板会遮挡并禁用其下方的标签。
    private final OverlayPanelManager.Panel aimPanel = panelManager.addPanel();
    private final OverlayPanelManager.Panel continuousPanel = panelManager.addPanel();
    private final OverlayPanelManager.Panel dropPanel = panelManager.addPanel();
    private final OverlayPanelManager.Panel automationPanel = panelManager.addPanel();
    private boolean continuousDrop;
    private boolean percentageDrop;
    private int dropAmount = 1;
    private int dropPercentage = 100;
    private int heldAction = -1;
    private int heldTicks;
    private boolean heldStarted;
    private DropAmountSlider dropAmountSlider;
    private Button dropModeButton;
    private Button flyUpButton;
    private Button flyDownButton;
    private AimPad aimPad;
    private AimPad directionPad;
    private EditBox pitchInput;
    private EditBox yawInput;
    private ToggleSwitchButton bodyFollowsHeadButton;
    private boolean syncingAimInputs;

    public FakePlayerInventoryScreen(FakePlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.screenWidth(), menu.screenHeight());
    }

    @Override
    protected void init() {
        super.init();
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            addTransferButtons(ENDER_CHEST_TRANSFER_BUTTON_TOP);
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
        addTransferButtons(TRANSFER_BUTTON_TOP);
        addControlButtons();
        addAimPanel();

        int panelLeft = leftPos + imageWidth;
        int automationTop = topPos + AUTOMATION_PANEL_TOP + 21;
        ToggleSwitchButton[] automationButtons = new ToggleSwitchButton[AUTOMATION_KEYS.length];
        for (int index = 0; index < AUTOMATION_KEYS.length; index++) {
            int actionId = FakePlayerInventoryMenu.ACTION_AUTO_REPLENISHMENT + index;
            int automationIndex = index;
            automationButtons[index] = addRenderableWidget(new ToggleSwitchButton(
                panelLeft + 6,
                automationTop + index * (AUTOMATION_BUTTON_HEIGHT + 2),
                AUTOMATION_PANEL_WIDTH - 12,
                AUTOMATION_BUTTON_HEIGHT,
                Component.translatable("gui.fakeplayer.automation." + AUTOMATION_KEYS[index]),
                () -> menu.automationEnabled(automationIndex),
                button -> sendAction(actionId)
            ));
        }
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
        automationPanel.bind(automationTabButton, automationButtons);

        ContinuousPanelOverlay continuousPanelOverlay = addRenderableWidget(
            new ContinuousPanelOverlay(panelLeft, topPos + CONTINUOUS_PANEL_TOP)
        );
        int continuousTop = topPos + CONTINUOUS_PANEL_TOP + 21;
        ToggleSwitchButton[] continuousButtons = new ToggleSwitchButton[CONTINUOUS_KEYS.length];
        for (int index = 0; index < CONTINUOUS_KEYS.length; index++) {
            int controlIndex = index;
            int buttonTop = index < 4
                ? continuousTop + index * (CONTINUOUS_BUTTON_HEIGHT + 2)
                : continuousTop + 4 * (CONTINUOUS_BUTTON_HEIGHT + 2)
                    + (index - 4) * (CONTINUOUS_BUTTON_HEIGHT + CONTINUOUS_SLIDER_HEIGHT + 4);
            continuousButtons[index] = addRenderableWidget(new ToggleSwitchButton(
                panelLeft + 6,
                buttonTop,
                CONTINUOUS_PANEL_WIDTH - 12,
                CONTINUOUS_BUTTON_HEIGHT,
                Component.translatable("gui.fakeplayer.continuous." + CONTINUOUS_KEYS[index]),
                () -> menu.continuousControlEnabled(controlIndex),
                button -> sendAction(CONTINUOUS_ACTIONS[controlIndex])
            ));
        }
        ContinuousIntervalSlider[] intervalSliders = new ContinuousIntervalSlider[3];
        for (int index = 0; index < intervalSliders.length; index++) {
            int buttonTop = continuousTop + 4 * (CONTINUOUS_BUTTON_HEIGHT + 2)
                + index * (CONTINUOUS_BUTTON_HEIGHT + CONTINUOUS_SLIDER_HEIGHT + 4);
            intervalSliders[index] = addRenderableWidget(new ContinuousIntervalSlider(
                panelLeft + 6,
                buttonTop + CONTINUOUS_BUTTON_HEIGHT + 2,
                CONTINUOUS_PANEL_WIDTH - 12,
                CONTINUOUS_SLIDER_HEIGHT,
                index
            ));
        }
        Button stopAllContinuousButton = addRenderableWidget(new CompactButton(
            panelLeft + 6,
            continuousTop + 4 * (CONTINUOUS_BUTTON_HEIGHT + 2)
                + intervalSliders.length * (CONTINUOUS_BUTTON_HEIGHT + CONTINUOUS_SLIDER_HEIGHT + 4) + 2,
            CONTINUOUS_PANEL_WIDTH - 12,
            CONTINUOUS_BUTTON_HEIGHT,
            Component.translatable("gui.fakeplayer.stop"),
            button -> sendAction(FakePlayerInventoryMenu.ACTION_STOP_ALL)
        ));
        Button continuousTabButton = addRenderableWidget(new IconTabButton(
            panelLeft,
            topPos + CONTINUOUS_PANEL_TOP,
            DROP_TAB_WIDTH,
            DROP_TAB_HEIGHT,
            new ItemStack(Items.CLOCK),
            Component.translatable("gui.fakeplayer.continuous.title"),
            button -> continuousPanel.toggle()
        ));
        continuousTabButton.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.continuous.title")));
        AbstractWidget[] continuousContents =
            new AbstractWidget[continuousButtons.length + intervalSliders.length + 2];
        continuousContents[0] = continuousPanelOverlay;
        System.arraycopy(continuousButtons, 0, continuousContents, 1, continuousButtons.length);
        System.arraycopy(intervalSliders, 0, continuousContents, continuousButtons.length + 1,
            intervalSliders.length);
        continuousContents[continuousContents.length - 1] = stopAllContinuousButton;
        continuousPanel.bind(continuousTabButton, continuousContents);

        int panelTop = topPos + DROP_PANEL_TOP;
        // 控件始终在背景之后绘制，因此用覆盖层让展开的 Q 面板遮住下方面板控件。
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
        dropPanel.bind(tabButton, dropPanelOverlay, dropModeButton, dropAmountSlider,
            continuousDropButton, executeDropButton);
    }

    /** 在假人物品栏的空白区域添加移动和即时动作操控杆。 */
    private void addControlButtons() {
        addControlButton(0, 0, "↶", FakePlayerInventoryMenu.ACTION_TURN_LEFT);
        addControlButton(1, 0, "↑", FakePlayerInventoryMenu.ACTION_MOVE_FORWARD);
        addControlButton(2, 0, "↷", FakePlayerInventoryMenu.ACTION_TURN_RIGHT);
        addControlButton(0, 1, "←", FakePlayerInventoryMenu.ACTION_MOVE_LEFT);
        addControlButton(1, 1, "S", FakePlayerInventoryMenu.ACTION_SNEAK);
        addControlButton(2, 1, "→", FakePlayerInventoryMenu.ACTION_MOVE_RIGHT);
        addControlButton(0, 2, "L", FakePlayerInventoryMenu.ACTION_ATTACK_ONCE);
        addControlButton(1, 2, "↓", FakePlayerInventoryMenu.ACTION_MOVE_BACKWARD);
        addControlButton(2, 2, "R", FakePlayerInventoryMenu.ACTION_USE_ONCE);

        addControlButtonAt(
            leftPos + SNEAK_BUTTON_LEFT,
            topPos + CONTROL_TOP + CONTROL_SIZE,
            "J",
            FakePlayerInventoryMenu.ACTION_JUMP
        );

        flyUpButton = addControlButtonAt(
            leftPos + SNEAK_BUTTON_LEFT,
            topPos + CONTROL_TOP,
            "↑",
            FakePlayerInventoryMenu.ACTION_FLY_UP
        );
        flyUpButton.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.fly_up")));
        flyDownButton = addControlButtonAt(
            leftPos + SNEAK_BUTTON_LEFT,
            topPos + CONTROL_TOP + CONTROL_SIZE * 2,
            "↓",
            FakePlayerInventoryMenu.ACTION_FLY_DOWN
        );
        flyDownButton.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.fly_down")));
        updateFlyingButtons();
    }

    private void addAimPanel() {
        int x = leftPos + imageWidth;
        int y = topPos + AIM_PANEL_TOP;
        bodyFollowsHeadButton = addRenderableWidget(new ToggleSwitchButton(
            x + 6, y + 20, AIM_PANEL_WIDTH - 12, 16,
            Component.translatable("gui.fakeplayer.look.body_follows_head"),
            menu::bodyFollowsHead,
            button -> sendAction(FakePlayerInventoryMenu.ACTION_TOGGLE_BODY_FOLLOWS_HEAD)
        ));
        bodyFollowsHeadButton.setTooltip(Tooltip.create(
            Component.translatable("gui.fakeplayer.look.body_follows_head_tooltip")));
        aimPad = addRenderableWidget(new AimPad(x + 16, y + 50, AIM_PAD_SIZE, false));
        directionPad = addRenderableWidget(new AimPad(x + 16, y + 124, AIM_PAD_SIZE, true));
        pitchInput = addRenderableWidget(new EditBox(font, x + 36, y + 192, 52, 16,
            Component.translatable("gui.fakeplayer.look_pitch")));
        yawInput = addRenderableWidget(new EditBox(font, x + 36, y + 212, 52, 16,
            Component.translatable("gui.fakeplayer.look_yaw")));
        pitchInput.setValue(Integer.toString(menu.pitch()));
        yawInput.setValue(Integer.toString(menu.yaw()));
        pitchInput.setFilter(value -> value.matches("-?\\d{0,3}"));
        yawInput.setFilter(value -> value.matches("-?\\d{0,3}"));
        pitchInput.setResponder(value -> {
            if (syncingAimInputs) return;
            submitAngleInput(pitchInput, value, -90, 90, true);
        });
        yawInput.setResponder(value -> {
            if (syncingAimInputs) return;
            submitAngleInput(yawInput, value, -180, 179, false);
        });
        Button tab = addRenderableWidget(new IconTabButton(x, y, DROP_TAB_WIDTH, DROP_TAB_HEIGHT, new ItemStack(Items.COMPASS),
            Component.translatable("gui.fakeplayer.look.title"), button -> aimPanel.toggle()));
        tab.setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.look.title")));
        aimPanel.bind(tab, bodyFollowsHeadButton, aimPad, directionPad, pitchInput, yawInput);
    }

    /** 修正超出范围的角度输入，并把最终值发送到服务端。 */
    private void submitAngleInput(EditBox input, String value, int minimum, int maximum, boolean pitch) {
        try {
            int angle = Integer.parseInt(value);
            int clamped = Math.clamp(angle, minimum, maximum);
            if (angle != clamped) {
                syncingAimInputs = true;
                input.setValue(Integer.toString(clamped));
                syncingAimInputs = false;
            }
            sendAction(pitch
                ? FakePlayerInventoryMenu.pitchAction(clamped)
                : FakePlayerInventoryMenu.yawAction(clamped));
        } catch (NumberFormatException ignored) {
            // 空输入和单独的负号是编辑过程中的合法中间状态。
        }
    }

    private void addControlButton(int column, int row, String label, int actionId) {
        addControlButtonAt(
            leftPos + CONTROL_LEFT + column * CONTROL_SIZE,
            topPos + CONTROL_TOP + row * CONTROL_SIZE,
            label,
            actionId
        );
    }

    private Button addControlButtonAt(int x, int y, String label, int actionId) {
        return addRenderableWidget(new CompactButton(
            x,
            y,
            CONTROL_SIZE,
            CONTROL_SIZE,
            Component.literal(label),
            ignored -> {
                sendAction(actionId);
                heldAction = actionId;
                heldTicks = 0;
                heldStarted = false;
            }
        ));
    }

    private void updateFlyingButtons() {
        if (flyUpButton == null || flyDownButton == null) {
            return;
        }
        boolean visible = menu.isFlying();
        flyUpButton.visible = visible;
        flyDownButton.visible = visible;
    }

    private void addTransferButtons(int buttonTop) {
        addRenderableWidget(new TransferButton(
            leftPos + TRANSFER_BUTTON_LEFT,
            topPos + buttonTop,
            TransferButton.Direction.TO_CONTAINER,
            (transferAll, includeHotbar) -> sendAction(
                transferActionId(true, transferAll, includeHotbar))
        ));
        addRenderableWidget(new TransferButton(
            leftPos + TRANSFER_BUTTON_LEFT + TransferButton.SIZE,
            topPos + buttonTop,
            TransferButton.Direction.TO_INVENTORY,
            (transferAll, includeHotbar) -> sendAction(
                transferActionId(false, transferAll, includeHotbar))
        ));
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
            // 覆盖层只负责绘制背景，不能参与鼠标命中，否则会拦截面板内按钮。
            return false;
        }
    }

    /** 持续控制面板的背景覆盖层，避免下方自动化控件透过面板显示。 */
    private final class ContinuousPanelOverlay extends Button {
        private ContinuousPanelOverlay(int x, int y) {
            super(x, y, CONTINUOUS_PANEL_WIDTH, CONTINUOUS_PANEL_HEIGHT,
                Component.empty(), button -> {}, DEFAULT_NARRATION);
            active = false;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawContinuousPanel(graphics);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // 覆盖层只负责绘制背景，不能参与鼠标命中，否则会拦截面板内按钮。
            return false;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateFlyingButtons();
        syncAimInputs();
        if (heldAction < 0) {
            return;
        }
        heldTicks++;
        if (!heldStarted && heldTicks >= 5) {
            int continuous = continuousAction(heldAction);
            if (continuous >= 0) {
                sendAction(continuous);
                heldStarted = true;
            }
        }
    }

    private static int continuousAction(int action) {
        return switch (action) {
            case FakePlayerInventoryMenu.ACTION_TURN_LEFT -> FakePlayerInventoryMenu.ACTION_TURN_LEFT_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_FORWARD -> FakePlayerInventoryMenu.ACTION_MOVE_FORWARD_HELD;
            case FakePlayerInventoryMenu.ACTION_TURN_RIGHT -> FakePlayerInventoryMenu.ACTION_TURN_RIGHT_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_LEFT -> FakePlayerInventoryMenu.ACTION_MOVE_LEFT_HELD;
            case FakePlayerInventoryMenu.ACTION_JUMP -> FakePlayerInventoryMenu.ACTION_JUMP_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_RIGHT -> FakePlayerInventoryMenu.ACTION_MOVE_RIGHT_HELD;
            case FakePlayerInventoryMenu.ACTION_ATTACK_ONCE -> FakePlayerInventoryMenu.ACTION_ATTACK_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_BACKWARD -> FakePlayerInventoryMenu.ACTION_MOVE_BACKWARD_HELD;
            case FakePlayerInventoryMenu.ACTION_USE_ONCE -> FakePlayerInventoryMenu.ACTION_USE_HELD;
            default -> -1;
        };
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

        // 从下到上绘制，让排在前面的标签和展开面板保持在最上层。
        drawAutomationPanel(graphics);
        drawDropPanel(graphics);
        drawContinuousPanel(graphics);
        drawAimPanel(graphics);

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

    /** 将滑块位置映射为持续动作的 tick 间隔。 */
    private final class ContinuousIntervalSlider extends CompactSliderButton {
        private final int controlIndex;
        private int interval;

        private ContinuousIntervalSlider(int x, int y, int width, int height, int controlIndex) {
            super(
                x,
                y,
                width,
                height,
                Component.empty(),
                (double) (menu.continuousInterval(controlIndex) - 1)
                    / (FakePlayerInventoryMenu.MAX_CONTINUOUS_INTERVAL - 1)
            );
            this.controlIndex = controlIndex;
            this.interval = menu.continuousInterval(controlIndex);
            updateMessage();
            setTooltip(Tooltip.create(Component.translatable("gui.fakeplayer.continuous.interval_tooltip")));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.fakeplayer.continuous.interval", interval));
        }

        @Override
        protected void applyValue() {
            int selected = 1 + (int) Math.round(
                value * (FakePlayerInventoryMenu.MAX_CONTINUOUS_INTERVAL - 1));
            if (selected == interval) {
                return;
            }
            interval = selected;
            updateMessage();
            sendAction(FakePlayerInventoryMenu.continuousIntervalActionId(controlIndex, interval));
        }
    }

    private void syncAimInputs() {
        if (pitchInput == null || yawInput == null) return;
        syncingAimInputs = true;
        if (!pitchInput.isFocused()) {
            pitchInput.setValue(Integer.toString(menu.pitch()));
        }
        if (!yawInput.isFocused()) {
            yawInput.setValue(Integer.toString(menu.yaw()));
        }
        syncingAimInputs = false;
    }

    private void drawContinuousPanel(GuiGraphicsExtractor graphics) {
        int left = leftPos + imageWidth;
        int top = topPos + CONTINUOUS_PANEL_TOP;
        int width = continuousPanel.isOpen() ? CONTINUOUS_PANEL_WIDTH : DROP_TAB_WIDTH;
        int height = continuousPanel.isOpen() ? CONTINUOUS_PANEL_HEIGHT : DROP_TAB_HEIGHT;
        PixelGui.drawTabBackground(graphics, left, top, width, height);
        if (continuousPanel.isOpen()) {
            graphics.text(font, Component.translatable("gui.fakeplayer.continuous.title"), left + 22, top + 8,
                0xFF404040, false);
        }
    }

    private void drawAimPanel(GuiGraphicsExtractor graphics) {
        int x = leftPos + imageWidth;
        int y = topPos + AIM_PANEL_TOP;
        PixelGui.drawTabBackground(graphics, x, y,
            aimPanel.isOpen() ? AIM_PANEL_WIDTH : DROP_TAB_WIDTH,
            aimPanel.isOpen() ? AIM_PANEL_HEIGHT : DROP_TAB_HEIGHT);
        if (!aimPanel.isOpen()) {
            return;
        }
        graphics.text(font, Component.translatable("gui.fakeplayer.look.title"), x + 22, y + 8,
            0xFF404040, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.look.view"), x + 16, y + 41,
            0xFF606060, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.look.direction"), x + 16, y + 115,
            0xFF606060, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.look_pitch"), x + 6, y + 197,
            0xFF404040, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.look_yaw"), x + 6, y + 217,
            0xFF404040, false);
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

    /** 视角/身体朝向控制器；方形视角盘为自由二维输入，圆形朝向盘沿圆周连续旋转。 */
    private final class AimPad extends Button {
        private final boolean cardinal;
        private boolean dragging;
        private boolean draggingOutsideHorizontally;
        private float dragStartBodyYaw;
        private float dragYawOffset;
        AimPad(int x, int y, int size, boolean cardinal) {
            super(x, y, size, size, Component.empty(), button -> { }, DEFAULT_NARRATION);
            this.cardinal = cardinal;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
            if (cardinal) {
                setTooltip(Tooltip.create(Component.translatable(
                    "gui.fakeplayer.look.direction_tooltip",
                    menu.bodyYaw(),
                    Math.round(wrapDegrees(menu.yaw() - menu.bodyYaw())),
                    bodyBearing(menu.bodyYaw())
                )));
            } else {
                setTooltip(Tooltip.create(Component.translatable(
                    "gui.fakeplayer.look.view_tooltip", menu.pitch(), menu.yaw()
                )));
            }
            int cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2, r = getWidth() / 2 - 2;
            if (cardinal) {
                for (int a = 0; a < 360; a += 3) {
                    int px = cx + Math.round((float) Math.cos(Math.toRadians(a)) * r);
                    int py = cy + Math.round((float) Math.sin(Math.toRadians(a)) * r);
                    g.fill(px, py, px + 2, py + 2, 0xFF555555);
                }
            } else {
                int left = cx - r;
                int top = cy - r;
                int right = cx + r + 1;
                int bottom = cy + r + 1;
                g.fill(left, top, right, top + 2, 0xFF555555);
                g.fill(left, bottom - 2, right, bottom, 0xFF555555);
                g.fill(left, top + 2, left + 2, bottom - 2, 0xFF555555);
                g.fill(right - 2, top + 2, right, bottom - 2, 0xFF555555);
            }
            int bx;
            int by;
            if (cardinal) {
                double angle = Math.toRadians(menu.bodyYaw() + 90);
                bx = cx + (int) Math.round(Math.cos(angle) * r);
                by = cy + (int) Math.round(Math.sin(angle) * r);
            } else {
                // 联动时身体会吸收越界角度，拖动期间使用本地偏移避免网络同步造成摇杆抖动。
                float headOffset = menu.bodyFollowsHead() && dragging
                    ? dragYawOffset
                    : wrapDegrees(menu.yaw() - menu.bodyYaw());
                bx = cx + Math.round(headOffset * (r - 3) / MAX_HEAD_YAW_OFFSET);
                if (menu.bodyFollowsHead() && draggingOutsideHorizontally) {
                    // 只有鼠标越过左右边框时才让摇杆露出一半，表示当前正在带动身体。
                    bx = cx + (dragYawOffset < 0.0F ? -r : r);
                }
                by = cy + Math.round(menu.pitch() * (r - 3) / 90.0f);
            }
            g.fill(bx - 3, by - 3, bx + 4, by + 4, 0xFFFFFFFF);
            g.fill(bx - 2, by - 2, bx + 3, by + 3, 0xFFC6C6C6);
        }
        @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
            if (e.button() != 0 || !isMouseOver(e.x(), e.y())) return false;
            dragging = true;
            dragStartBodyYaw = menu.bodyYaw();
            update(e.x(), e.y());
            return true;
        }
        @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
            if (!dragging) return false; update(e.x(), e.y()); return true;
        }
        @Override public boolean mouseReleased(MouseButtonEvent e) {
            dragging = false;
            draggingOutsideHorizontally = false;
            return super.mouseReleased(e);
        }
        private void update(double mx, double my) {
            double cx = getX() + getWidth() / 2.0, cy = getY() + getHeight() / 2.0;
            double dx = mx - cx, dy = my - cy, r = getWidth() / 2.0 - 3;
            double rawDx = dx;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (cardinal && len > 0) { dx *= r / len; dy *= r / len; }
            else if (!cardinal) { dx = Math.max(-r, Math.min(r, dx)); dy = Math.max(-r, Math.min(r, dy)); }
            if (cardinal) {
                int yaw = (int) Math.round(Math.toDegrees(Math.atan2(dy, dx)) - 90);
                sendAction(FakePlayerInventoryMenu.bodyYawAction(yaw));
                return;
            }
            dragYawOffset = (float) (dx / r * MAX_HEAD_YAW_OFFSET);
            draggingOutsideHorizontally = menu.bodyFollowsHead() && Math.abs(rawDx) > r;
            float requestedYawOffset = menu.bodyFollowsHead()
                ? Math.clamp((float) (rawDx / r * MAX_HEAD_YAW_OFFSET), -179.0F, 179.0F)
                : dragYawOffset;
            float yawBase = menu.bodyFollowsHead() ? dragStartBodyYaw : menu.bodyYaw();
            int yaw = Math.round(yawBase + requestedYawOffset);
            sendAction(FakePlayerInventoryMenu.yawAction(yaw));
            int pitch = (int) Math.round(dy / r * 90.0);
            sendAction(FakePlayerInventoryMenu.pitchAction(pitch));
        }
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
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
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && heldAction >= 0) {
            if (heldStarted) {
                sendAction(FakePlayerInventoryMenu.ACTION_STOP_HELD);
            }
            resetHeldAction();
        }
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        if (heldStarted) {
            sendAction(FakePlayerInventoryMenu.ACTION_STOP_HELD);
        }
        resetHeldAction();
        super.removed();
    }

    private void resetHeldAction() {
        heldAction = -1;
        heldStarted = false;
        heldTicks = 0;
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
