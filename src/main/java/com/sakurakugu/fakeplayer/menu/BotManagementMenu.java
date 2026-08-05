package com.sakurakugu.fakeplayer.menu;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 向客户端提供预设、分组和在线假人的只读快照。 */
public final class BotManagementMenu extends AbstractContainerMenu {
    private final boolean openGroupsInitially;
    private final List<PresetSummary> presets;
    private final List<GroupSummary> groups;
    private final List<String> onlinePlayers;

    public BotManagementMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBoolean(), readPresets(data), readGroups(data), readStrings(data));
    }

    public BotManagementMenu(
        int containerId,
        Inventory inventory,
        boolean openGroupsInitially,
        List<PresetSummary> presets,
        List<GroupSummary> groups,
        List<String> onlinePlayers
    ) {
        super(ModMenus.BOT_MANAGEMENT.get(), containerId);
        this.openGroupsInitially = openGroupsInitially;
        this.presets = List.copyOf(presets);
        this.groups = List.copyOf(groups);
        this.onlinePlayers = List.copyOf(onlinePlayers);
    }

    private static List<PresetSummary> readPresets(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(index -> new PresetSummary(data.readUtf(), data.readUtf(), data.readUtf()))
            .toList();
    }

    private static List<GroupSummary> readGroups(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(index -> new GroupSummary(data.readUtf(), readStrings(data)))
            .toList();
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> data.readUtf()).toList();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public boolean openGroupsInitially() {
        return openGroupsInitially;
    }

    public List<PresetSummary> presets() {
        return presets;
    }

    public List<GroupSummary> groups() {
        return groups;
    }

    public List<String> onlinePlayers() {
        return onlinePlayers;
    }

    public record PresetSummary(String id, String description, String playerName) {
    }

    public record GroupSummary(String id, List<String> presetIds) {
        public GroupSummary {
            presetIds = List.copyOf(presetIds);
        }
    }
}
