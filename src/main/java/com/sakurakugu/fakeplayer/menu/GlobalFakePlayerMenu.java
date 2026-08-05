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

/** 为全局设置和假人列表提供客户端快照与服务端操作通道。 */
public final class GlobalFakePlayerMenu extends AbstractContainerMenu {
    public static final int ACTION_REFRESH = -1;
    public static final int ACTION_OPEN_BOTS = -100;
    private static final int ACTION_SETTING_BASE = -2;

    private final List<String> playerNames;
    private final boolean openListInitially;
    private final int settingsMask;

    public GlobalFakePlayerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBoolean(), data.readVarInt(), readPlayerNames(data));
    }

    public GlobalFakePlayerMenu(
        int containerId,
        Inventory inventory,
        boolean openListInitially,
        int settingsMask,
        List<String> playerNames
    ) {
        super(ModMenus.GLOBAL_FAKE_PLAYER.get(), containerId);
        this.openListInitially = openListInitially;
        this.settingsMask = settingsMask;
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
        if (actionId == ACTION_OPEN_BOTS) {
            FakePlayerMenuOpener.openBotManagement(viewer);
            return true;
        }
        int settingIndex = ACTION_SETTING_BASE - actionId;
        if (FakePlayerConfig.toggleGlobalSetting(settingIndex)) {
            // 重新打开菜单，将服务端确认后的配置状态同步给客户端。
            FakePlayerMenuOpener.openGlobal(viewer);
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

    public boolean settingEnabled(int index) {
        return (settingsMask & (1 << index)) != 0;
    }

    public static int settingAction(int index) {
        return ACTION_SETTING_BASE - index;
    }
}
