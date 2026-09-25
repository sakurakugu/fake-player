package com.sakurakugu.fakeplayer.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import org.junit.jupiter.api.Test;

class ChunkMapSnapshotPayloadTest {
    @Test
    void fakePlayerViewOnlyLoadsChunksInsideItsActiveRange() {
        var active = view(true, "minecraft:overworld", 10, -5, 2);

        assertTrue(active.loadsChunk("minecraft:overworld", 8, -7));
        assertTrue(active.loadsChunk("minecraft:overworld", 12, -3));
        assertFalse(active.loadsChunk("minecraft:overworld", 13, -5));
        assertFalse(active.loadsChunk("minecraft:the_nether", 10, -5));
        assertFalse(view(false, "", 0, 0, 0).loadsChunk("minecraft:overworld", 0, 0));
    }

    @Test
    void regionsAreSkippedOnlyWhenRevisionAndDimensionBothMatch() {
        assertTrue(ChunkMapSnapshotPayload.canSkipRegions(7L, "minecraft:overworld", 7L, "minecraft:overworld"));
        assertFalse(ChunkMapSnapshotPayload.canSkipRegions(7L, "minecraft:overworld", 8L, "minecraft:overworld"));
        // 同一份 revision 下换维度，区域列表是按维度过滤的，不能沿用
        assertFalse(ChunkMapSnapshotPayload.canSkipRegions(7L, "minecraft:overworld", 7L, "minecraft:the_nether"));
        assertFalse(ChunkMapSnapshotPayload.canSkipRegions(RequestChunkMapPayload.NO_REVISION, "",
            7L, "minecraft:overworld"));
    }

    @Test
    void skippingRegionsKeepsThePreviousChunkLists() {
        var chunk = ChunkKey.pack(4, -9);
        var region = new ChunkMapSnapshotPayload.AnchorView(UUID.randomUUID(), "main",
            "minecraft:overworld", true, Set.of(chunk));
        var previous = payload(3L, List.of(region));
        // 服务端说区域没变时列表是空的，客户端得把上一份拼回去
        var incremental = payload(4L, List.of());

        var merged = incremental.withPreviousRegions(previous);

        assertEquals(previous.regions(), merged.regions());
        assertEquals(4L, merged.revision());
        assertFalse(merged.regionsUnchanged());
        assertEquals(previous.playerChunkX(), merged.playerChunkX());
    }

    private static ChunkMapSnapshotPayload payload(long revision,
                                                   List<ChunkMapSnapshotPayload.AnchorView> regions) {
        return new ChunkMapSnapshotPayload(false, false, false, 0, 32, revision, true,
            "minecraft:overworld", 12, -3, regions, List.of(), List.of());
    }

    private static ChunkMapSnapshotPayload.FakePlayerView view(boolean active, String dimension,
                                                                int chunkX, int chunkZ, int distance) {
        return new ChunkMapSnapshotPayload.FakePlayerView(UUID.randomUUID(), "Loader", "minecraft:overworld",
            0, 64, 0, 0.0F, true, true, distance, active, dimension, chunkX, chunkZ, distance);
    }
}
