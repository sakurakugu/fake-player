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

    public FakePlayerInventoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(
            containerId,
            inventory,
            null,
            data.readUtf(64),
            View.fromNetwork(data.readVarInt()),
            data.readVarInt(),
            data.readBoolean(),
            data.readBoolean()
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
            possessedByViewer, targetOccupied);
    }

    private FakePlayerInventoryMenu(
        int containerId,
        Inventory viewerInventory,
        FakeServerPlayer target,
        String targetName,
        View view,
        int targetEntityId,
        boolean possessedByViewer,
        boolean targetOccupied
    ) {
        super(ModMenus.FAKE_PLAYER_INVENTORY.get(), containerId);
        this.target = target;
        this.targetName = targetName;
        this.view = view;
        this.targetEntityId = targetEntityId;
        this.possessedByViewer = possessedByViewer;
        this.targetOccupied = targetOccupied;
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
            default -> {
                return false;
            }
        }
        return true;
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
