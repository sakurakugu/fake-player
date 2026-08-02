package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** 编辑假人的完整物品栏或末影箱，并同时显示操作者背包。 */
public final class FakePlayerInventoryMenu extends AbstractContainerMenu {
    private static final int INVENTORY_TARGET_SLOTS = 41;
    private static final int ENDER_CHEST_TARGET_SLOTS = 27;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final FakeServerPlayer target;
    private final String targetName;
    private final int targetEntityId;
    private final View view;
    private final int targetSlotCount;
    private int selectedHotbarSlotSnapshot;
    private final DataSlot selectedHotbarSlot;

    public FakePlayerInventoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(
            containerId,
            inventory,
            null,
            data.readUtf(64),
            data.readBoolean() ? View.ENDER_CHEST : View.INVENTORY,
            data.readVarInt()
        );
    }

    public FakePlayerInventoryMenu(int containerId, Inventory inventory, FakeServerPlayer target, View view) {
        this(containerId, inventory, target, target.getGameProfile().name(), view, target.getId());
    }

    private FakePlayerInventoryMenu(
        int containerId,
        Inventory viewerInventory,
        FakeServerPlayer target,
        String targetName,
        View view,
        int targetEntityId
    ) {
        super(ModMenus.FAKE_PLAYER_INVENTORY.get(), containerId);
        this.target = target;
        this.targetName = targetName;
        this.view = view;
        this.targetEntityId = targetEntityId;
        this.targetSlotCount = view == View.INVENTORY ? INVENTORY_TARGET_SLOTS : ENDER_CHEST_TARGET_SLOTS;
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
            ? new SimpleContainer(targetSlotCount)
            : view == View.INVENTORY ? target.getInventory() : target.getEnderChestInventory();
        if (view == View.INVENTORY) {
            addTargetInventorySlots(targetContainer, target == null ? viewerInventory.player : target);
            addViewerSlots(viewerInventory, 8, 181);
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
        if (view != View.INVENTORY || actionId < 0 || actionId >= HOTBAR_SLOT_COUNT || !canAccess(player)) {
            return false;
        }
        target.getInventory().setSelectedSlot(actionId);
        broadcastChanges();
        return true;
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

        if (slotIndex < viewerStart) {
            if (!moveItemStackTo(sourceStack, viewerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveToTarget(sourceStack)) {
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
        return original;
    }

    private boolean moveToTarget(ItemStack stack) {
        if (view == View.ENDER_CHEST) {
            return moveItemStackTo(stack, 0, targetSlotCount, false);
        }

        EquipmentSlot equipmentSlot = target.getEquipmentSlotForItem(stack);
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
            && FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack());
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

    public int screenWidth() {
        return 176;
    }

    public int screenHeight() {
        return view == View.INVENTORY ? 264 : 168;
    }

    public enum View {
        INVENTORY,
        ENDER_CHEST
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
