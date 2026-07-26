package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 以三行容器布局提供假玩家动作按钮的服务端菜单。 */
public final class FakePlayerMenu extends AbstractContainerMenu {
    // 槽位编号直接对应三行九列容器中的按钮位置。
    public static final int SLOT_ATTACK = 10;
    public static final int SLOT_USE = 11;
    public static final int SLOT_JUMP = 12;
    public static final int SLOT_STOP = 13;
    public static final int SLOT_LEFT = 14;
    public static final int SLOT_RIGHT = 15;
    public static final int SLOT_SNEAK = 16;
    public static final int SLOT_REMOVE = 22;

    private final SimpleContainer display = new SimpleContainer(27);
    private final FakeServerPlayer target;
    private final int targetId;
    private final String targetName;

    public FakePlayerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        // 客户端构造：目标实体只存在于服务端，网络数据仅用于显示。
        this(containerId, inventory, null, data.readVarInt(), data.readUtf(64));
    }

    public FakePlayerMenu(int containerId, Inventory inventory, FakeServerPlayer target) {
        // 服务端构造：保留目标引用，以便点击按钮时直接执行动作。
        this(containerId, inventory, target, target.getId(), target.getGameProfile().name());
    }

    private FakePlayerMenu(int containerId, Inventory inventory, FakeServerPlayer target, int targetId, String targetName) {
        super(ModMenus.FAKE_PLAYER.get(), containerId);
        this.target = target;
        this.targetId = targetId;
        this.targetName = targetName;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new DisplaySlot(display, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        populateDisplay();
    }

    private void populateDisplay() {
        display.setItem(4, named(Items.PLAYER_HEAD, Component.translatable("gui.fakeplayer.status", targetName)));
        display.setItem(SLOT_ATTACK, named(Items.IRON_SWORD, Component.translatable("gui.fakeplayer.attack")));
        display.setItem(SLOT_USE, named(Items.BOW, Component.translatable("gui.fakeplayer.use")));
        display.setItem(SLOT_JUMP, named(Items.SLIME_BALL, Component.translatable("gui.fakeplayer.jump")));
        display.setItem(SLOT_STOP, named(Items.BARRIER, Component.translatable("gui.fakeplayer.stop")));
        display.setItem(SLOT_LEFT, named(Items.COMPASS, Component.translatable("gui.fakeplayer.turn_left")));
        display.setItem(SLOT_RIGHT, named(Items.COMPASS, Component.translatable("gui.fakeplayer.turn_right")));
        display.setItem(SLOT_SNEAK, named(Items.LEATHER_BOOTS, Component.translatable("gui.fakeplayer.sneak")));
        display.setItem(SLOT_REMOVE, named(Items.LAVA_BUCKET, Component.translatable("gui.fakeplayer.remove")));
    }

    private static ItemStack named(Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        // 客户端点击会同步到服务端；只有持有目标引用的一侧负责实际执行。
        if (target == null || slotId < 0 || slotId >= display.getContainerSize()) {
            return;
        }
        switch (slotId) {
            case SLOT_ATTACK -> target.actions().toggleAttack();
            case SLOT_USE -> target.actions().toggleUse();
            case SLOT_JUMP -> target.actions().jump();
            case SLOT_STOP -> target.actions().stop();
            case SLOT_LEFT -> target.actions().turn(-45.0F);
            case SLOT_RIGHT -> target.actions().turn(45.0F);
            case SLOT_SNEAK -> target.actions().toggleSneak();
            case SLOT_REMOVE -> {
                player.closeContainer();
                FakePlayerManager.remove(target);
            }
            default -> {
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 客户端无法校验目标；服务端则在目标离线或玩家距离超过 8 格时关闭菜单。
        return target == null || (!target.hasDisconnected() && player.distanceToSqr(target) <= 64.0);
    }

    public int targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // 菜单中的物品仅作为按钮图标，不允许玩家修改。
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
