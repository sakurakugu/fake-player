package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerActions;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 编辑假人的完整物品栏或末影箱，并同时显示操作者背包。 */
public final class FakePlayerInventoryMenu extends AbstractContainerMenu {
    private static final int INVENTORY_TARGET_SLOTS = 41;
    private static final int CRAFTING_SLOT_COUNT = 5;
    private static final int ENDER_CHEST_TARGET_SLOTS = 27;
    private static final int CRAFTING_RESULT_SLOT = INVENTORY_TARGET_SLOTS;
    private static final int CRAFTING_INPUT_START = CRAFTING_RESULT_SLOT + 1;
    private static final int CRAFTING_INPUT_END = CRAFTING_INPUT_START + 4;
    private static final int HOTBAR_SLOT_COUNT = 9;
    public static final int ACTION_ENDER_CHEST = HOTBAR_SLOT_COUNT;
    public static final int ACTION_REMOVE = HOTBAR_SLOT_COUNT + 1;
    public static final int ACTION_POSSESS = HOTBAR_SLOT_COUNT + 2;
    public static final int MAX_DROP_AMOUNT = 64;
    public static final int MAX_DROP_PERCENTAGE = 100;
    private static final int ACTION_DROP_AMOUNT_BASE = ACTION_POSSESS + 1;
    private static final int ACTION_DROP_AMOUNT_CONTINUOUS_BASE = ACTION_DROP_AMOUNT_BASE + MAX_DROP_AMOUNT;
    private static final int ACTION_DROP_PERCENTAGE_BASE = ACTION_DROP_AMOUNT_CONTINUOUS_BASE + MAX_DROP_AMOUNT;
    private static final int ACTION_DROP_PERCENTAGE_CONTINUOUS_BASE =
        ACTION_DROP_PERCENTAGE_BASE + MAX_DROP_PERCENTAGE;
    private static final int ACTION_DROP_END = ACTION_DROP_PERCENTAGE_CONTINUOUS_BASE + MAX_DROP_PERCENTAGE;
    public static final int ACTION_TRANSFER_TO_TARGET_MATCHING = ACTION_DROP_END;
    public static final int ACTION_TRANSFER_TO_TARGET_ALL = ACTION_TRANSFER_TO_TARGET_MATCHING + 1;
    public static final int ACTION_TRANSFER_TO_VIEWER_MATCHING = ACTION_TRANSFER_TO_TARGET_ALL + 1;
    public static final int ACTION_TRANSFER_TO_VIEWER_ALL = ACTION_TRANSFER_TO_VIEWER_MATCHING + 1;
    public static final int ACTION_TRANSFER_TO_TARGET_MATCHING_WITH_HOTBAR = ACTION_TRANSFER_TO_VIEWER_ALL + 1;
    public static final int ACTION_TRANSFER_TO_TARGET_ALL_WITH_HOTBAR = ACTION_TRANSFER_TO_TARGET_MATCHING_WITH_HOTBAR + 1;
    public static final int ACTION_TRANSFER_TO_VIEWER_MATCHING_WITH_HOTBAR = ACTION_TRANSFER_TO_TARGET_ALL_WITH_HOTBAR + 1;
    public static final int ACTION_TRANSFER_TO_VIEWER_ALL_WITH_HOTBAR = ACTION_TRANSFER_TO_VIEWER_MATCHING_WITH_HOTBAR + 1;
    public static final int ACTION_AUTO_REPLENISHMENT = ACTION_TRANSFER_TO_VIEWER_ALL_WITH_HOTBAR + 1;
    public static final int ACTION_AUTO_REPLENISHMENT_FROM_SHULKER_BOXES = ACTION_AUTO_REPLENISHMENT + 1;
    public static final int ACTION_AUTO_REPLACE_TOOLS = ACTION_AUTO_REPLENISHMENT_FROM_SHULKER_BOXES + 1;
    public static final int ACTION_AUTO_FISHING = ACTION_AUTO_REPLACE_TOOLS + 1;
    public static final int ACTION_MOVE_FORWARD = ACTION_AUTO_FISHING + 1;
    public static final int ACTION_MOVE_BACKWARD = ACTION_MOVE_FORWARD + 1;
    public static final int ACTION_MOVE_LEFT = ACTION_MOVE_BACKWARD + 1;
    public static final int ACTION_MOVE_RIGHT = ACTION_MOVE_LEFT + 1;
    public static final int ACTION_JUMP = ACTION_MOVE_RIGHT + 1;
    public static final int ACTION_ATTACK_ONCE = ACTION_JUMP + 1;
    public static final int ACTION_USE_ONCE = ACTION_ATTACK_ONCE + 1;
    public static final int ACTION_TURN_LEFT = ACTION_USE_ONCE + 1;
    public static final int ACTION_TURN_RIGHT = ACTION_TURN_LEFT + 1;
    public static final int ACTION_SNEAK = ACTION_TURN_RIGHT + 1;
    public static final int ACTION_MOVE_FORWARD_HELD = ACTION_SNEAK + 1;
    public static final int ACTION_MOVE_BACKWARD_HELD = ACTION_MOVE_FORWARD_HELD + 1;
    public static final int ACTION_MOVE_LEFT_HELD = ACTION_MOVE_BACKWARD_HELD + 1;
    public static final int ACTION_MOVE_RIGHT_HELD = ACTION_MOVE_LEFT_HELD + 1;
    public static final int ACTION_TURN_LEFT_HELD = ACTION_MOVE_RIGHT_HELD + 1;
    public static final int ACTION_TURN_RIGHT_HELD = ACTION_TURN_LEFT_HELD + 1;
    public static final int ACTION_STOP_HELD = ACTION_TURN_RIGHT_HELD + 1;
    public static final int ACTION_ATTACK_HELD = ACTION_STOP_HELD + 1;
    public static final int ACTION_USE_HELD = ACTION_ATTACK_HELD + 1;
    public static final int ACTION_JUMP_HELD = ACTION_USE_HELD + 1;
    public static final int ACTION_FLY_UP = ACTION_JUMP_HELD + 1;
    public static final int ACTION_FLY_DOWN = ACTION_FLY_UP + 1;
    public static final int ACTION_TOGGLE_MOVE_FORWARD = ACTION_FLY_DOWN + 1;
    public static final int ACTION_TOGGLE_MOVE_BACKWARD = ACTION_TOGGLE_MOVE_FORWARD + 1;
    public static final int ACTION_TOGGLE_MOVE_LEFT = ACTION_TOGGLE_MOVE_BACKWARD + 1;
    public static final int ACTION_TOGGLE_MOVE_RIGHT = ACTION_TOGGLE_MOVE_LEFT + 1;
    public static final int ACTION_TOGGLE_ATTACK = ACTION_TOGGLE_MOVE_RIGHT + 1;
    public static final int ACTION_TOGGLE_USE = ACTION_TOGGLE_ATTACK + 1;
    public static final int ACTION_TOGGLE_JUMP = ACTION_TOGGLE_USE + 1;
    public static final int ACTION_STOP_ALL_CONTINUOUS = ACTION_TOGGLE_JUMP + 1;
    public static final int MAX_CONTINUOUS_INTERVAL = 100;
    private static final int ACTION_CONTINUOUS_INTERVAL_BASE = ACTION_STOP_ALL_CONTINUOUS + 1;
    private static final int CONTINUOUS_INTERVAL_ACTION_COUNT = 3;
    private static final int ACTION_CONTINUOUS_INTERVAL_END = ACTION_CONTINUOUS_INTERVAL_BASE
        + MAX_CONTINUOUS_INTERVAL * CONTINUOUS_INTERVAL_ACTION_COUNT;
    private static final int ACTION_SET_PITCH_BASE = ACTION_CONTINUOUS_INTERVAL_END;
    private static final int ACTION_SET_YAW_BASE = ACTION_SET_PITCH_BASE + 181;
    private static final int ACTION_SET_BODY_YAW_BASE = ACTION_SET_YAW_BASE + 360;
    public static final int ACTION_SET_END = ACTION_SET_BODY_YAW_BASE + 360;
    public static int pitchAction(int pitch) { return ACTION_SET_PITCH_BASE + Math.max(-90, Math.min(90, pitch)) + 90; }
    public static int yawAction(int yaw) { return angleAction(ACTION_SET_YAW_BASE, yaw); }
    public static int bodyYawAction(int yaw) { return angleAction(ACTION_SET_BODY_YAW_BASE, yaw); }
    private static int angleAction(int base, int yaw) {
        return base + Math.floorMod(yaw + 180, 360);
    }
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final FakeServerPlayer target;
    private final String targetName;
    private final int targetEntityId;
    private final View view;
    private final int targetSlotCount;
    private final boolean possessedByViewer;
    private final boolean targetOccupied;
    private final Player viewer;
    private final Player craftingOwner;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 2, 2);
    private final ResultContainer resultSlots = new ResultContainer();
    private int selectedHotbarSlotSnapshot;
    private final DataSlot selectedHotbarSlot;
    private int automationMaskSnapshot;
    private final DataSlot automationMask;
    private int flyingSnapshot;
    private final DataSlot flying;
    private int continuousControlMaskSnapshot;
    private final DataSlot continuousControlMask;
    private final int[] continuousIntervals = new int[CONTINUOUS_INTERVAL_ACTION_COUNT];
    private final DataSlot[] continuousIntervalData = new DataSlot[CONTINUOUS_INTERVAL_ACTION_COUNT];
    private int heldControlAction = -1;
    private int pitchSnapshot;
    private int yawSnapshot;
    private int bodyYawSnapshot;
    private DataSlot pitchData;
    private DataSlot yawData;
    private DataSlot bodyYawData;

    public FakePlayerInventoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(
            containerId,
            inventory,
            null,
            data.readUtf(64),
            View.fromNetwork(data.readVarInt()),
            data.readVarInt(),
            data.readBoolean(),
            data.readBoolean(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt()
        );
    }

    public FakePlayerInventoryMenu(
        int containerId,
        Inventory inventory,
        FakeServerPlayer target,
        View view,
        boolean possessedByViewer,
        boolean targetOccupied
    ) {
        this(containerId, inventory, target, target.getGameProfile().name(), view, target.getId(),
            possessedByViewer, targetOccupied, automationMask(target), continuousControlMask(target),
            target.actions().repeatInterval(FakePlayerActions.ScheduledAction.ATTACK),
            target.actions().repeatInterval(FakePlayerActions.ScheduledAction.USE),
            target.actions().repeatInterval(FakePlayerActions.ScheduledAction.JUMP),
            Math.round(target.getXRot()), Math.round(target.getYRot()), Math.round(target.yBodyRot));
    }

    private FakePlayerInventoryMenu(
        int containerId,
        Inventory viewerInventory,
        FakeServerPlayer target,
        String targetName,
        View view,
        int targetEntityId,
        boolean possessedByViewer,
        boolean targetOccupied,
        int automationMask,
        int continuousControlMask,
        int attackInterval,
        int useInterval,
        int jumpInterval,
        int pitch,
        int yaw,
        int bodyYaw
    ) {
        super(ModMenus.FAKE_PLAYER_INVENTORY.get(), containerId);
        this.target = target;
        this.targetName = targetName;
        this.view = view;
        this.targetEntityId = targetEntityId;
        this.possessedByViewer = possessedByViewer;
        this.targetOccupied = targetOccupied;
        this.pitchSnapshot = pitch;
        this.yawSnapshot = yaw;
        this.bodyYawSnapshot = bodyYaw;
        this.pitchData = addDataSlot(new DataSlot() {
            public int get() { return target == null ? pitchSnapshot : Math.round(target.getXRot()); }
            public void set(int value) { pitchSnapshot = value; }
        });
        this.yawData = addDataSlot(new DataSlot() {
            public int get() { return target == null ? yawSnapshot : Math.round(target.getYRot()); }
            public void set(int value) { yawSnapshot = value; }
        });
        this.bodyYawData = addDataSlot(new DataSlot() {
            public int get() { return target == null ? bodyYawSnapshot : Math.round(target.yBodyRot); }
            public void set(int value) { bodyYawSnapshot = value; }
        });
        this.automationMaskSnapshot = automationMask;
        this.continuousControlMaskSnapshot = continuousControlMask;
        this.continuousIntervals[0] = attackInterval;
        this.continuousIntervals[1] = useInterval;
        this.continuousIntervals[2] = jumpInterval;
        this.viewer = viewerInventory.player;
        this.craftingOwner = target == null ? viewer : target;
        this.targetSlotCount = switch (view) {
            case INVENTORY -> INVENTORY_TARGET_SLOTS;
            case POSSESSED_INVENTORY -> INVENTORY_TARGET_SLOTS + CRAFTING_SLOT_COUNT;
            case ENDER_CHEST -> ENDER_CHEST_TARGET_SLOTS;
        };
        this.selectedHotbarSlot = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? selectedHotbarSlotSnapshot : target.getInventory().getSelectedSlot();
            }

            @Override
            public void set(int value) {
                selectedHotbarSlotSnapshot = value;
            }
        });
        this.automationMask = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? automationMaskSnapshot : automationMask(target);
            }

            @Override
            public void set(int value) {
                automationMaskSnapshot = value;
            }
        });
        this.flying = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? flyingSnapshot : target.getAbilities().flying ? 1 : 0;
            }

            @Override
            public void set(int value) {
                flyingSnapshot = value;
            }
        });
        this.continuousControlMask = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? continuousControlMaskSnapshot : continuousControlMask(target);
            }

            @Override
            public void set(int value) {
                continuousControlMaskSnapshot = value;
            }
        });
        for (int index = 0; index < continuousIntervalData.length; index++) {
            int intervalIndex = index;
            continuousIntervalData[index] = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    if (target != null) {
                        continuousIntervals[intervalIndex] = target.actions().repeatInterval(
                            continuousAction(intervalIndex));
                    }
                    return continuousIntervals[intervalIndex];
                }

                @Override
                public void set(int value) {
                    continuousIntervals[intervalIndex] = value;
                }
            });
        }

        Container targetContainer = target == null
            ? new SimpleContainer(view == View.ENDER_CHEST ? ENDER_CHEST_TARGET_SLOTS : INVENTORY_TARGET_SLOTS)
            : view == View.ENDER_CHEST ? target.getEnderChestInventory() : target.getInventory();
        if (view != View.ENDER_CHEST) {
            addTargetInventorySlots(targetContainer, target == null ? viewerInventory.player : target);
            if (view == View.POSSESSED_INVENTORY) {
                addCraftingSlots();
            }
            if (view == View.INVENTORY) {
                addViewerSlots(viewerInventory, 8, 178);
            }
        } else {
            addGrid(targetContainer, 0, 3, 8, 18);
            addViewerSlots(viewerInventory, 8, 85);
        }
    }

    private void addTargetInventorySlots(Container inventory, LivingEntity owner) {
        // 假人主背包使用原版 Inventory 索引：9-35 为主背包，0-8 为快捷栏。
        addGrid(inventory, 9, 3, 8, 84);
        addGrid(inventory, 0, 1, 8, 142);

        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[index];
            addSlot(new EquipmentSlotSlot(inventory, owner, equipmentSlot, 39 - index, 8, 8 + index * 18));
        }
        addSlot(new EquipmentSlotSlot(inventory, owner, EquipmentSlot.OFFHAND, 40, 77, 62));
    }

    private void addCraftingSlots() {
        addSlot(new ResultSlot(craftingOwner, craftSlots, resultSlots, 0, 154, 28));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                addSlot(new Slot(craftSlots, column + row * 2, 98 + column * 18, 18 + row * 18));
            }
        }
    }

    private void addViewerSlots(Inventory inventory, int left, int top) {
        addGrid(inventory, 9, 3, left, top);
        addGrid(inventory, 0, 1, left, top + 58);
    }

    private void addGrid(Container container, int startIndex, int rows, int left, int top) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, startIndex + row * 9 + column, left + column * 18, top + row * 18));
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput containerInput, Player player) {
        // 每个服务端槽位操作都重新校验，避免目标离线或权限变更后继续编辑。
        if (!canAccess(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
            return;
        }
        super.clicked(slotId, button, containerInput, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int actionId) {
        if (view == View.ENDER_CHEST || !canAccess(player)) {
            return false;
        }
        if (view == View.POSSESSED_INVENTORY && actionId != ACTION_POSSESS) {
            return false;
        }
        if (actionId >= 0 && actionId < HOTBAR_SLOT_COUNT) {
            target.getInventory().setSelectedSlot(actionId);
            broadcastChanges();
            return true;
        }
        if (!(player instanceof ServerPlayer viewer) || target == null) {
            return false;
        }
        if (actionId >= ACTION_DROP_AMOUNT_BASE && actionId < ACTION_DROP_END) {
            boolean percentage = actionId >= ACTION_DROP_PERCENTAGE_BASE;
            int continuousBase = percentage
                ? ACTION_DROP_PERCENTAGE_CONTINUOUS_BASE
                : ACTION_DROP_AMOUNT_CONTINUOUS_BASE;
            boolean continuous = actionId >= continuousBase;
            int base = percentage
                ? continuous ? ACTION_DROP_PERCENTAGE_CONTINUOUS_BASE : ACTION_DROP_PERCENTAGE_BASE
                : continuous ? ACTION_DROP_AMOUNT_CONTINUOUS_BASE : ACTION_DROP_AMOUNT_BASE;
            int value = actionId - base + 1;
            FakePlayerActions.RepeatMode mode = continuous
                ? FakePlayerActions.RepeatMode.CONTINUOUS
                : FakePlayerActions.RepeatMode.ONCE;
            if (percentage) {
                target.actions().dropPercentage(target.getInventory().getSelectedSlot(), value, mode, 1);
            } else {
                target.actions().dropAmount(target.getInventory().getSelectedSlot(), value, mode, 1);
            }
            return true;
        }
        if (actionId >= ACTION_CONTINUOUS_INTERVAL_BASE && actionId < ACTION_CONTINUOUS_INTERVAL_END) {
            int encoded = actionId - ACTION_CONTINUOUS_INTERVAL_BASE;
            int controlIndex = encoded / MAX_CONTINUOUS_INTERVAL;
            int interval = encoded % MAX_CONTINUOUS_INTERVAL + 1;
            target.actions().setRepeatInterval(continuousAction(controlIndex), interval);
            continuousIntervals[controlIndex] = interval;
            broadcastChanges();
            return true;
        }
        if (actionId >= ACTION_SET_PITCH_BASE && actionId < ACTION_SET_YAW_BASE) {
            target.actions().setViewRotation(actionId - ACTION_SET_PITCH_BASE - 90, target.getYRot());
            broadcastChanges();
            return true;
        }
        if (actionId >= ACTION_SET_YAW_BASE && actionId < ACTION_SET_BODY_YAW_BASE) {
            target.actions().setViewRotation(target.getXRot(), actionId - ACTION_SET_YAW_BASE - 180);
            broadcastChanges();
            return true;
        }
        if (actionId >= ACTION_SET_BODY_YAW_BASE && actionId < ACTION_SET_END) {
            target.actions().setBodyRotation(actionId - ACTION_SET_BODY_YAW_BASE - 180);
            broadcastChanges();
            return true;
        }
        switch (actionId) {
            case ACTION_ENDER_CHEST -> FakePlayerMenuOpener.openEnderChest(viewer, target);
            case ACTION_REMOVE -> {
                player.closeContainer();
                FakePlayerManager.remove(target);
            }
            case ACTION_POSSESS -> {
                if (FakePlayerPossession.isControlling(viewer, target)) {
                    FakePlayerPossession.stop(viewer);
                    viewer.closeContainer();
                } else {
                    FakePlayerPossession.start(viewer, target);
                }
            }
            case ACTION_TRANSFER_TO_TARGET_MATCHING -> transferItems(player, true, true, false);
            case ACTION_TRANSFER_TO_TARGET_ALL -> transferItems(player, true, false, false);
            case ACTION_TRANSFER_TO_VIEWER_MATCHING -> transferItems(player, false, true, false);
            case ACTION_TRANSFER_TO_VIEWER_ALL -> transferItems(player, false, false, false);
            case ACTION_TRANSFER_TO_TARGET_MATCHING_WITH_HOTBAR -> transferItems(player, true, true, true);
            case ACTION_TRANSFER_TO_TARGET_ALL_WITH_HOTBAR -> transferItems(player, true, false, true);
            case ACTION_TRANSFER_TO_VIEWER_MATCHING_WITH_HOTBAR -> transferItems(player, false, true, true);
            case ACTION_TRANSFER_TO_VIEWER_ALL_WITH_HOTBAR -> transferItems(player, false, false, true);
            case ACTION_AUTO_REPLENISHMENT,
                ACTION_AUTO_REPLENISHMENT_FROM_SHULKER_BOXES,
                ACTION_AUTO_REPLACE_TOOLS,
                ACTION_AUTO_FISHING -> {
                int index = actionId - ACTION_AUTO_REPLENISHMENT;
                target.automation().toggleSetting(index);
                // 只同步数据槽，保留客户端展开状态。
                broadcastChanges();
            }
            case ACTION_MOVE_FORWARD -> target.actions().moveOnce(FakePlayerActions.MoveDirection.FORWARD);
            case ACTION_MOVE_BACKWARD -> target.actions().moveOnce(FakePlayerActions.MoveDirection.BACKWARD);
            case ACTION_MOVE_LEFT -> target.actions().moveOnce(FakePlayerActions.MoveDirection.LEFT);
            case ACTION_MOVE_RIGHT -> target.actions().moveOnce(FakePlayerActions.MoveDirection.RIGHT);
            case ACTION_JUMP -> target.actions().jump();
            case ACTION_ATTACK_ONCE -> target.actions().attackOnce();
            case ACTION_USE_ONCE -> target.actions().useOnce();
            case ACTION_TURN_LEFT -> target.actions().turn(-1.0F);
            case ACTION_TURN_RIGHT -> target.actions().turn(1.0F);
            case ACTION_SNEAK -> target.actions().toggleSneak();
            case ACTION_MOVE_FORWARD_HELD -> startHeldMove(FakePlayerActions.MoveDirection.FORWARD);
            case ACTION_MOVE_BACKWARD_HELD -> startHeldMove(FakePlayerActions.MoveDirection.BACKWARD);
            case ACTION_MOVE_LEFT_HELD -> startHeldMove(FakePlayerActions.MoveDirection.LEFT);
            case ACTION_MOVE_RIGHT_HELD -> startHeldMove(FakePlayerActions.MoveDirection.RIGHT);
            case ACTION_TURN_LEFT_HELD -> startHeldTurn(-1.0F);
            case ACTION_TURN_RIGHT_HELD -> startHeldTurn(1.0F);
            case ACTION_STOP_HELD -> {
                stopHeldControl();
            }
            case ACTION_ATTACK_HELD -> {
                target.actions().startAttack();
                heldControlAction = ACTION_ATTACK_HELD;
            }
            case ACTION_USE_HELD -> {
                target.actions().startUse();
                heldControlAction = ACTION_USE_HELD;
            }
            case ACTION_JUMP_HELD -> {
                target.actions().startJump();
                heldControlAction = ACTION_JUMP_HELD;
            }
            case ACTION_FLY_UP -> target.actions().flyVertical(true);
            case ACTION_FLY_DOWN -> target.actions().flyVertical(false);
            case ACTION_TOGGLE_MOVE_FORWARD, ACTION_TOGGLE_MOVE_BACKWARD,
                ACTION_TOGGLE_MOVE_LEFT, ACTION_TOGGLE_MOVE_RIGHT -> {
                FakePlayerActions.MoveDirection direction = switch (actionId) {
                    case ACTION_TOGGLE_MOVE_FORWARD -> FakePlayerActions.MoveDirection.FORWARD;
                    case ACTION_TOGGLE_MOVE_BACKWARD -> FakePlayerActions.MoveDirection.BACKWARD;
                    case ACTION_TOGGLE_MOVE_LEFT -> FakePlayerActions.MoveDirection.LEFT;
                    default -> FakePlayerActions.MoveDirection.RIGHT;
                };
                if (target.actions().isMoving(direction)) {
                    target.actions().stopMove();
                } else {
                    target.actions().startMove(direction);
                }
                broadcastChanges();
            }
            case ACTION_TOGGLE_ATTACK -> {
                toggleContinuousAction(0);
                broadcastChanges();
            }
            case ACTION_TOGGLE_USE -> {
                toggleContinuousAction(1);
                broadcastChanges();
            }
            case ACTION_TOGGLE_JUMP -> {
                toggleContinuousAction(2);
                broadcastChanges();
            }
            case ACTION_STOP_ALL_CONTINUOUS -> {
                target.actions().stopMove();
                target.actions().stopAttack();
                target.actions().stopUse();
                target.actions().stopJump();
                broadcastChanges();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void startHeldMove(FakePlayerActions.MoveDirection direction) {
        target.actions().startMove(direction);
        heldControlAction = switch (direction) {
            case FORWARD -> ACTION_MOVE_FORWARD_HELD;
            case BACKWARD -> ACTION_MOVE_BACKWARD_HELD;
            case LEFT -> ACTION_MOVE_LEFT_HELD;
            case RIGHT -> ACTION_MOVE_RIGHT_HELD;
        };
    }

    private void toggleContinuousAction(int controlIndex) {
        FakePlayerActions.ScheduledAction action = continuousAction(controlIndex);
        boolean enabled = target.actions().isRepeating(action);
        int interval = target.actions().repeatInterval(action);
        switch (action) {
            case ATTACK -> {
                if (enabled) {
                    target.actions().stopAttack();
                } else {
                    target.actions().attack(FakePlayerActions.RepeatMode.INTERVAL, interval);
                }
            }
            case USE -> {
                if (enabled) {
                    target.actions().stopUse();
                } else {
                    target.actions().use(FakePlayerActions.RepeatMode.INTERVAL, interval);
                }
            }
            case JUMP -> {
                if (enabled) {
                    target.actions().stopJump();
                } else {
                    target.actions().jump(FakePlayerActions.RepeatMode.INTERVAL, interval);
                }
            }
            default -> throw new IllegalArgumentException("不支持切换该持续动作: " + action);
        }
    }

    private static FakePlayerActions.ScheduledAction continuousAction(int index) {
        return switch (index) {
            case 0 -> FakePlayerActions.ScheduledAction.ATTACK;
            case 1 -> FakePlayerActions.ScheduledAction.USE;
            case 2 -> FakePlayerActions.ScheduledAction.JUMP;
            default -> throw new IllegalArgumentException("无效的持续动作索引: " + index);
        };
    }

    private void startHeldTurn(float yawDelta) {
        target.actions().startTurn(yawDelta);
        heldControlAction = yawDelta < 0.0F ? ACTION_TURN_LEFT_HELD : ACTION_TURN_RIGHT_HELD;
    }

    private void stopHeldControl() {
        switch (heldControlAction) {
            case ACTION_MOVE_FORWARD_HELD, ACTION_MOVE_BACKWARD_HELD,
                ACTION_MOVE_LEFT_HELD, ACTION_MOVE_RIGHT_HELD -> target.actions().stopMove();
            case ACTION_TURN_LEFT_HELD, ACTION_TURN_RIGHT_HELD -> target.actions().stopTurn();
            case ACTION_ATTACK_HELD -> target.actions().stopAttack();
            case ACTION_USE_HELD -> target.actions().stopUse();
            case ACTION_JUMP_HELD -> target.actions().stopJump();
            default -> {
            }
        }
        heldControlAction = -1;
    }

    /** 默认只处理双方的 27 格主背包；按住 Ctrl 才包含快捷栏，装备槽始终保持原样。 */
    private void transferItems(Player player, boolean toTarget, boolean filterByContents, boolean includeHotbar) {
        int viewerStart = targetSlotCount;
        int sourceStart = toTarget ? viewerStart : 0;
        int sourceEnd = sourceStart + (includeHotbar ? 36 : 27);
        int destinationStart = toTarget ? 0 : viewerStart;
        int destinationEnd = destinationStart + (includeHotbar ? 36 : 27);
        List<ItemStack> destinationContents = filterByContents
            ? snapshotContents(destinationStart, destinationEnd)
            : List.of();

        for (int slotIndex = sourceStart; slotIndex < sourceEnd; slotIndex++) {
            Slot source = slots.get(slotIndex);
            if (!source.hasItem()) {
                continue;
            }
            ItemStack sourceStack = source.getItem();
            if (filterByContents && destinationContents.stream()
                .noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, sourceStack))) {
                continue;
            }

            ItemStack original = sourceStack.copy();
            if (!moveItemStackTo(sourceStack, destinationStart, destinationEnd, false)
                || sourceStack.getCount() == original.getCount()) {
                continue;
            }
            if (sourceStack.isEmpty()) {
                source.setByPlayer(ItemStack.EMPTY, original);
            } else {
                source.setChanged();
            }
            source.onTake(player, sourceStack);
        }
        broadcastChanges();
    }

    private List<ItemStack> snapshotContents(int start, int end) {
        List<ItemStack> contents = new ArrayList<>();
        for (int slotIndex = start; slotIndex < end; slotIndex++) {
            ItemStack stack = slots.get(slotIndex).getItem();
            if (!stack.isEmpty() && contents.stream()
                .noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                contents.add(stack.copyWithCount(1));
            }
        }
        return contents;
    }

    /** 将丢弃数值、计量模式和连续模式编码为原版菜单按钮协议可传输的动作编号。 */
    public static int dropActionId(int value, boolean percentage, boolean continuous) {
        int maximum = percentage ? MAX_DROP_PERCENTAGE : MAX_DROP_AMOUNT;
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException("Drop value must be between 1 and " + maximum);
        }
        int base = percentage
            ? continuous ? ACTION_DROP_PERCENTAGE_CONTINUOUS_BASE : ACTION_DROP_PERCENTAGE_BASE
            : continuous ? ACTION_DROP_AMOUNT_CONTINUOUS_BASE : ACTION_DROP_AMOUNT_BASE;
        return base + value - 1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (!canAccess(player) || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot source = slots.get(slotIndex);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = source.getItem();
        ItemStack original = sourceStack.copy();
        int viewerStart = targetSlotCount;

        if (view == View.POSSESSED_INVENTORY && slotIndex == CRAFTING_RESULT_SLOT) {
            if (!moveItemStackTo(sourceStack, 0, 36, true)) {
                return ItemStack.EMPTY;
            }
            source.onQuickCraft(sourceStack, original);
        } else if (view == View.POSSESSED_INVENTORY
            && slotIndex >= CRAFTING_INPUT_START && slotIndex < CRAFTING_INPUT_END) {
            if (!moveItemStackTo(sourceStack, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < viewerStart && view == View.POSSESSED_INVENTORY) {
            if (!moveWithinTargetInventory(sourceStack, slotIndex)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < viewerStart) {
            if (!moveItemStackTo(sourceStack, viewerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveToTarget(sourceStack, player)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY, original);
        } else {
            source.setChanged();
        }
        if (sourceStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        source.onTake(player, sourceStack);
        if (view == View.POSSESSED_INVENTORY && slotIndex == CRAFTING_RESULT_SLOT && !sourceStack.isEmpty()) {
            craftingOwner.drop(sourceStack, false);
        }
        return original;
    }

    private boolean moveWithinTargetInventory(ItemStack stack, int slotIndex) {
        if (slotIndex < 27) {
            return moveItemStackTo(stack, 27, 36, false);
        }
        return moveItemStackTo(stack, 0, 27, false);
    }

    @Override
    public void slotsChanged(Container container) {
        if (container != craftSlots || !(craftingOwner.level() instanceof ServerLevel level)
            || !(craftingOwner instanceof ServerPlayer serverCraftingOwner)
            || !(viewer instanceof ServerPlayer serverViewer)) {
            super.slotsChanged(container);
            return;
        }

        CraftingInput input = craftSlots.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
        ItemStack result = recipe.filter(holder -> resultSlots.setRecipeUsed(serverCraftingOwner, holder))
            .map(RecipeHolder::value)
            .map(craftingRecipe -> craftingRecipe.assemble(input))
            .filter(stack -> stack.isItemEnabled(level.enabledFeatures()))
            .orElse(ItemStack.EMPTY);

        resultSlots.setItem(0, result);
        setRemoteSlot(CRAFTING_RESULT_SLOT, result);
        serverViewer.connection.send(new ClientboundContainerSetSlotPacket(
            containerId, incrementStateId(), CRAFTING_RESULT_SLOT, result));
    }

    @Override
    public void removed(Player player) {
        if (heldControlAction >= 0 && target != null) {
            stopHeldControl();
        }
        // 附身界面没有操作者背包，关闭时鼠标携带物也必须回到假人。
        if (view == View.POSSESSED_INVENTORY && !player.level().isClientSide() && !getCarried().isEmpty()) {
            ItemStack carried = getCarried();
            setCarried(ItemStack.EMPTY);
            craftingOwner.getInventory().placeItemBackInInventory(carried);
        }
        super.removed(player);
        resultSlots.clearContent();
        if (!craftingOwner.level().isClientSide()) {
            clearContainer(craftingOwner, craftSlots);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot targetSlot) {
        return targetSlot.container != resultSlots && super.canTakeItemForPickAll(carried, targetSlot);
    }

    private boolean moveToTarget(ItemStack stack, Player player) {
        if (view == View.ENDER_CHEST) {
            return moveItemStackTo(stack, 0, targetSlotCount, false);
        }

        // 客户端菜单没有假人实体，使用同为玩家的操作者完成装备槽判定、以及类似箱子的手势操作判定。
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(stack);
        int equipmentIndex = switch (equipmentSlot) {
            case HEAD -> 36;
            case CHEST -> 37;
            case LEGS -> 38;
            case FEET -> 39;
            case OFFHAND -> 40;
            default -> -1;
        };
        if (equipmentIndex >= 0 && !slots.get(equipmentIndex).hasItem()
            && moveItemStackTo(stack, equipmentIndex, equipmentIndex + 1, false)) {
            return true;
        }
        // 前 36 个菜单槽位对应假人的主背包和快捷栏。
        return moveItemStackTo(stack, 0, 36, false);
    }

    private boolean canAccess(Player player) {
        if (target == null) {
            return true;
        }
        return !target.hasDisconnected()
            && player instanceof ServerPlayer viewer
            && FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())
            && !FakePlayerPossession.isPossessed(target);
    }

    @Override
    public boolean stillValid(Player player) {
        return canAccess(player);
    }

    public String targetName() {
        return targetName;
    }

    public View view() {
        return view;
    }

    public int targetEntityId() {
        return targetEntityId;
    }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot.get();
    }

    public boolean possessedByViewer() {
        return possessedByViewer;
    }

    public boolean targetOccupied() {
        return targetOccupied;
    }

    public boolean isFlying() {
        return flying.get() != 0;
    }

    public boolean automationEnabled(int index) {
        return (automationMask.get() & (1 << index)) != 0;
    }

    public int pitch() { return pitchData.get(); }
    public int yaw() { return yawData.get(); }
    public int bodyYaw() { return bodyYawData.get(); }

    public boolean continuousControlEnabled(int index) {
        return (continuousControlMask.get() & (1 << index)) != 0;
    }

    public int continuousInterval(int index) {
        return continuousIntervalData[index].get();
    }

    public static int continuousIntervalActionId(int index, int interval) {
        if (index < 0 || index >= CONTINUOUS_INTERVAL_ACTION_COUNT) {
            throw new IllegalArgumentException("无效的持续动作索引: " + index);
        }
        int value = Math.clamp(interval, 1, MAX_CONTINUOUS_INTERVAL);
        return ACTION_CONTINUOUS_INTERVAL_BASE + index * MAX_CONTINUOUS_INTERVAL + value - 1;
    }

    public static int continuousControlMask(FakeServerPlayer fake) {
        FakePlayerActions actions = fake.actions();
        int mask = 0;
        for (FakePlayerActions.MoveDirection direction : FakePlayerActions.MoveDirection.values()) {
            if (actions.isMoving(direction)) {
                mask |= 1 << direction.ordinal();
            }
        }
        if (actions.isRepeating(FakePlayerActions.ScheduledAction.ATTACK)) {
            mask |= 1 << 4;
        }
        if (actions.isRepeating(FakePlayerActions.ScheduledAction.USE)) {
            mask |= 1 << 5;
        }
        if (actions.isRepeating(FakePlayerActions.ScheduledAction.JUMP)) {
            mask |= 1 << 6;
        }
        return mask;
    }

    public static int automationMask(FakeServerPlayer fake) {
        var settings = fake.automation().settings();
        int mask = 0;
        if (settings.autoReplenishment()) {
            mask |= 1;
        }
        if (settings.autoReplenishmentFromShulkerBoxes()) {
            mask |= 1 << 1;
        }
        if (settings.autoReplaceTools()) {
            mask |= 1 << 2;
        }
        if (settings.autoFishing()) {
            mask |= 1 << 3;
        }
        return mask;
    }

    public int screenWidth() {
        return 176;
    }

    public int screenHeight() {
        return switch (view) {
            case INVENTORY -> 261;
            case POSSESSED_INVENTORY -> 166;
            case ENDER_CHEST -> 168;
        };
    }

    public enum View {
        INVENTORY,
        POSSESSED_INVENTORY,
        ENDER_CHEST

        ;

        private static View fromNetwork(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : INVENTORY;
        }
    }

    /** 复现原版装备槽限制，并把装备变化通知给假人实体。 */
    private static final class EquipmentSlotSlot extends Slot {
        private final LivingEntity owner;
        private final EquipmentSlot equipmentSlot;

        private EquipmentSlotSlot(
            Container container,
            LivingEntity owner,
            EquipmentSlot equipmentSlot,
            int containerIndex,
            int x,
            int y
        ) {
            super(container, containerIndex, x, y);
            this.owner = owner;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            owner.onEquipItem(equipmentSlot, oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public int getMaxStackSize() {
            return equipmentSlot == EquipmentSlot.OFFHAND ? super.getMaxStackSize() : 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return equipmentSlot == EquipmentSlot.OFFHAND || stack.canEquip(equipmentSlot, owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (equipmentSlot == EquipmentSlot.OFFHAND) {
                return super.mayPickup(player);
            }
            ItemStack stack = getItem();
            return stack.isEmpty() || player.isCreative()
                || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        }

        @Override
        public Identifier getNoItemIcon() {
            return switch (equipmentSlot) {
                case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                case OFFHAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                default -> null;
            };
        }
    }
}
