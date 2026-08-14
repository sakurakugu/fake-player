package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 为生成页面和假人列表提供客户端快照与服务端操作通道。 */
public final class GlobalFakePlayerMenu extends AbstractContainerMenu {
    public static final int ACTION_REFRESH = -1;

    private final List<String> playerNames;
    private final boolean openListInitially;
    private final boolean openSpawnInitially;

    public GlobalFakePlayerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBoolean(), data.readBoolean(), readPlayerNames(data));
    }

    public GlobalFakePlayerMenu(
        int containerId,
        Inventory inventory,
        boolean openListInitially,
        boolean openSpawnInitially,
        List<String> playerNames
    ) {
        super(ModMenus.GLOBAL_FAKE_PLAYER.get(), containerId);
        this.openListInitially = openListInitially;
        this.openSpawnInitially = openSpawnInitially;
        this.playerNames = List.copyOf(playerNames);
    }

    private static List<String> readPlayerNames(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(index -> data.readUtf(64))
            .toList();
    }

    @Override
    public boolean clickMenuButton(Player player, int actionId) {
        if (!(player instanceof ServerPlayer viewer)
            || !FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())) {
            return false;
        }
        if (actionId == ACTION_REFRESH) {
            FakePlayerMenuOpener.openList(viewer);
            return true;
        }
        if (actionId < 0 || actionId >= playerNames.size()) {
            return false;
        }

        // 按名称重新查询实体，避免快照中已移除的假人被操作。
        FakeServerPlayer target = FakePlayerManager.find(viewer.level().getServer(), playerNames.get(actionId));
        if (target == null) {
            FakePlayerMenuOpener.openList(viewer);
            return true;
        }
        FakePlayerMenuOpener.openInventory(viewer, target);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public List<String> playerNames() {
        return playerNames;
    }

    public boolean openListInitially() {
        return openListInitially;
    }

    public boolean openSpawnInitially() {
        return openSpawnInitially;
    }

}
