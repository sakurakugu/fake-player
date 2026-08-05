package com.sakurakugu.fakeplayer.menu;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 向客户端提供区块加载点管理界面的只读快照。 */
public final class ChunkLoaderMenu extends AbstractContainerMenu {
    public static final int MAX_ANCHORS = 1024;
    private final int maximumRadius;
    private final List<AnchorSummary> anchors;

    public ChunkLoaderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readVarInt(), readAnchors(data));
    }

    public ChunkLoaderMenu(int containerId, Inventory inventory, int maximumRadius, List<AnchorSummary> anchors) {
        super(ModMenus.CHUNK_LOADER.get(), containerId);
        this.maximumRadius = maximumRadius;
        this.anchors = List.copyOf(anchors);
    }

    private static List<AnchorSummary> readAnchors(RegistryFriendlyByteBuf data) {
        int count = Math.min(data.readVarInt(), MAX_ANCHORS);
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> new AnchorSummary(
            data.readUtf(32), data.readUtf(256), data.readInt(), data.readInt(), data.readInt(),
            data.readVarInt(), data.readBoolean(), data.readBoolean())).toList();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public int maximumRadius() {
        return maximumRadius;
    }

    public List<AnchorSummary> anchors() {
        return anchors;
    }

    public record AnchorSummary(
        String name, String dimension, int x, int y, int z, int radius, boolean enabled, boolean ticking
    ) {
    }
}
