package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderSavedData.Anchor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** 当前维度的加载点快照，同时供地图和 HUD 使用。 */
public record ChunkMapSnapshotPayload(
    boolean openScreen,
    String dimension,
    int playerChunkX,
    int playerChunkZ,
    List<AnchorView> anchors
) implements CustomPacketPayload {
    private static final int MAX_ANCHORS = 1024;
    public static final Type<ChunkMapSnapshotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_map_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkMapSnapshotPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkMapSnapshotPayload::write, ChunkMapSnapshotPayload::new);

    public ChunkMapSnapshotPayload {
        anchors = List.copyOf(anchors);
    }

    private ChunkMapSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf(256), buffer.readInt(), buffer.readInt(), readAnchors(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(openScreen);
        buffer.writeUtf(dimension, 256);
        buffer.writeInt(playerChunkX);
        buffer.writeInt(playerChunkZ);
        buffer.writeVarInt(anchors.size());
        anchors.forEach(anchor -> anchor.write(buffer));
    }

    public static ChunkMapSnapshotPayload create(ServerPlayer player, List<Anchor> anchors, boolean openScreen) {
        String dimension = player.level().dimension().identifier().toString();
        List<AnchorView> views = anchors.stream()
            .filter(anchor -> anchor.dimension().toString().equals(dimension))
            .limit(MAX_ANCHORS)
            .map(AnchorView::from)
            .toList();
        return new ChunkMapSnapshotPayload(openScreen, dimension,
            player.chunkPosition().x(), player.chunkPosition().z(), views);
    }

    private static List<AnchorView> readAnchors(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_ANCHORS);
        List<AnchorView> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(new AnchorView(buffer));
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record AnchorView(String name, int chunkX, int chunkZ, int radius, boolean enabled, boolean ticking) {
        private AnchorView(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUtf(32), buffer.readInt(), buffer.readInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name, 32);
            buffer.writeInt(chunkX);
            buffer.writeInt(chunkZ);
            buffer.writeVarInt(radius);
            buffer.writeBoolean(enabled);
            buffer.writeBoolean(ticking);
        }

        private static AnchorView from(Anchor anchor) {
            return new AnchorView(anchor.name(), anchor.position().getX() >> 4, anchor.position().getZ() >> 4,
                anchor.radius(), anchor.enabled(), anchor.ticking());
        }

        public boolean contains(int chunkX, int chunkZ) {
            return enabled && Math.abs(chunkX - this.chunkX) <= radius
                && Math.abs(chunkZ - this.chunkZ) <= radius;
        }
    }
}
