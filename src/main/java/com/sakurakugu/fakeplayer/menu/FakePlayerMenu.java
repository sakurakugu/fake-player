package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 为假玩家控制页面提供服务端动作通道。 */
public final class FakePlayerMenu extends AbstractContainerMenu {
    // 动作编号通过原版容器按钮数据包从客户端发送到服务端。
    public static final int ACTION_ATTACK = 0;
    public static final int ACTION_USE = 1;
    public static final int ACTION_JUMP = 2;
    public static final int ACTION_STOP = 3;
    public static final int ACTION_LEFT = 4;
    public static final int ACTION_RIGHT = 5;
    public static final int ACTION_SNEAK = 6;
    public static final int ACTION_REMOVE = 7;
    public static final int ACTION_INVENTORY = 8;
    public static final int ACTION_ENDER_CHEST = 9;

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

    private FakePlayerMenu(
        int containerId,
        Inventory inventory,
        FakeServerPlayer target,
        int targetId,
        String targetName
    ) {
        super(ModMenus.FAKE_PLAYER.get(), containerId);
        this.target = target;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    public boolean clickMenuButton(Player player, int actionId) {
        // 客户端没有目标引用，实际动作只能由服务端菜单执行。
        if (target == null || !(player instanceof ServerPlayer viewer)
            || !FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())
            || FakePlayerPossession.isPossessed(target)) {
            return false;
        }
        switch (actionId) {
            case ACTION_ATTACK -> target.actions().toggleAttack();
            case ACTION_USE -> target.actions().toggleUse();
            case ACTION_JUMP -> target.actions().jump();
            case ACTION_STOP -> target.actions().stop();
            case ACTION_LEFT -> target.actions().turn(-45.0F);
            case ACTION_RIGHT -> target.actions().turn(45.0F);
            case ACTION_SNEAK -> target.actions().toggleSneak();
            case ACTION_INVENTORY -> FakePlayerMenuOpener.openInventory(viewer, target);
            case ACTION_ENDER_CHEST -> FakePlayerMenuOpener.openEnderChest(viewer, target);
            case ACTION_REMOVE -> {
                player.closeContainer();
                FakePlayerManager.remove(target);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 客户端无法校验目标；服务端仅在目标离线时关闭菜单，以支持远程控制。
        return target == null || !target.hasDisconnected() && !FakePlayerPossession.isPossessed(target);
    }

    public int targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

}
