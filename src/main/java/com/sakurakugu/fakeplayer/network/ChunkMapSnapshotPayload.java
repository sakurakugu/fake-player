package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderSavedData;
import com.sakurakugu.fakeplayer.chunkloading.FakePlayerLoadPolicy;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadRegion;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** 当前维度的权威加载快照，同时供内置地图和第三方地图前端使用。 */
public record ChunkMapSnapshotPayload(
    boolean openScreen,
    long revision,
    String dimension,
    int playerChunkX,
    int playerChunkZ,
    List<AnchorView> regions,
    List<FakePlayerView> fakePlayers
) implements CustomPacketPayload {
    public static final int MAX_REGIONS = 1024;
    public static final int MAX_SNAPSHOT_CHUNKS = 65536;
    public static final int MAX_FAKE_PLAYERS = 1024;
    public static final Type<ChunkMapSnapshotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_map_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkMapSnapshotPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkMapSnapshotPayload::write, ChunkMapSnapshotPayload::new);

    public ChunkMapSnapshotPayload {
        regions = List.copyOf(regions);
        fakePlayers = List.copyOf(fakePlayers);
    }

    private ChunkMapSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarLong(), buffer.readUtf(256), buffer.readInt(), buffer.readInt(),
            readRegions(buffer), readFakePlayers(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(openScreen);
        buffer.writeVarLong(revision);
        buffer.writeUtf(dimension, 256);
        buffer.writeInt(playerChunkX);
        buffer.writeInt(playerChunkZ);
        buffer.writeVarInt(regions.size());
        regions.forEach(region -> region.write(buffer));
        buffer.writeVarInt(fakePlayers.size());
        fakePlayers.forEach(fake -> fake.write(buffer));
    }

    public static ChunkMapSnapshotPayload create(ServerPlayer player, ChunkLoaderSavedData data, boolean openScreen) {
        String dimension = player.level().dimension().identifier().toString();
        List<AnchorView> regionViews = data.regions().stream()
            .filter(region -> region.dimension().toString().equals(dimension))
            .limit(MAX_REGIONS)
            .map(AnchorView::from)
            .toList();
        List<FakePlayerView> fakeViews = FakePlayerManager.all(player.level().getServer()).stream()
            .limit(MAX_FAKE_PLAYERS)
            .map(fake -> {
                FakePlayerLoadPolicy policy = data.policy(fake.getUUID())
                    .orElse(new FakePlayerLoadPolicy(fake.getUUID(), false, 0));
                return new FakePlayerView(fake.getUUID(), fake.getGameProfile().name(),
                    fake.level().dimension().identifier().toString(), fake.getBlockX(), fake.getBlockY(),
                    fake.getBlockZ(), fake.getYRot(), true, policy.enabled(), policy.simulationDistance());
            }).toList();
        return new ChunkMapSnapshotPayload(openScreen, data.revision(), dimension,
            player.chunkPosition().x(), player.chunkPosition().z(), regionViews, fakeViews);
    }

    private static List<AnchorView> readRegions(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_REGIONS, "区域");
        List<AnchorView> values = new ArrayList<>(size);
        int chunks = 0;
        for (int index = 0; index < size; index++) {
            AnchorView value = new AnchorView(buffer);
            chunks = Math.addExact(chunks, value.chunks().size());
            if (chunks > MAX_SNAPSHOT_CHUNKS) throw new IllegalArgumentException("快照区块数量超过上限");
            values.add(value);
        }
        return values;
    }

    private static List<FakePlayerView> readFakePlayers(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_FAKE_PLAYERS, "假玩家");
        List<FakePlayerView> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) values.add(new FakePlayerView(buffer));
        return values;
    }

    private static int checkedSize(int size, int maximum, String type) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException(type + "数量超过上限");
        return size;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public List<AnchorView> anchors() {
        return regions;
    }

    public record AnchorView(UUID id, String name, ManualLoadMode mode, boolean enabled, Set<Long> chunks) {
        private AnchorView(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readUtf(32), buffer.readEnum(ManualLoadMode.class), buffer.readBoolean(),
                readChunks(buffer));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 32);
            buffer.writeEnum(mode);
            buffer.writeBoolean(enabled);
            buffer.writeVarInt(chunks.size());
            chunks.forEach(buffer::writeLong);
        }

        private static AnchorView from(ManualLoadRegion region) {
            return new AnchorView(region.id(), region.name(), region.mode(), region.enabled(), region.chunks());
        }

        private static Set<Long> readChunks(RegistryFriendlyByteBuf buffer) {
            int size = checkedSize(buffer.readVarInt(), ChunkLoaderSavedData.MAX_REGION_CHUNKS, "区域区块");
            java.util.HashSet<Long> values = new java.util.HashSet<>();
            for (int index = 0; index < size; index++) {
                if (!values.add(buffer.readLong())) throw new IllegalArgumentException("区域包含重复区块");
            }
            return Set.copyOf(values);
        }

        public boolean contains(int chunkX, int chunkZ) {
            return enabled && chunks.contains(ChunkPos.pack(chunkX, chunkZ));
        }

        public int chunkX() { return chunks.stream().mapToInt(ChunkPos::getX).min().orElse(0); }
        public int chunkZ() { return chunks.stream().mapToInt(ChunkPos::getZ).min().orElse(0); }
        public int radius() {
            int width = chunks.stream().mapToInt(ChunkPos::getX).max().orElse(0) - chunkX();
            int height = chunks.stream().mapToInt(ChunkPos::getZ).max().orElse(0) - chunkZ();
            return Math.max(width, height) / 2;
        }
        public boolean ticking() { return mode != ManualLoadMode.LOADED; }
    }

    public record FakePlayerView(UUID id, String name, String dimension, int x, int y, int z, float yaw,
                                 boolean online, boolean enabled, int simulationDistance) {
        private FakePlayerView(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(256), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt());
            if (!Float.isFinite(yaw)) throw new IllegalArgumentException("假玩家朝向非法");
            if (simulationDistance < 0 || simulationDistance > ChunkLoaderSavedData.MAX_SIMULATION_DISTANCE) {
                throw new IllegalArgumentException("假玩家模拟距离非法");
            }
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeUtf(name, 32); buffer.writeUtf(dimension, 256);
            buffer.writeInt(x); buffer.writeInt(y); buffer.writeInt(z);
            buffer.writeFloat(yaw);
            buffer.writeBoolean(online); buffer.writeBoolean(enabled); buffer.writeVarInt(simulationDistance);
        }
    }
}
