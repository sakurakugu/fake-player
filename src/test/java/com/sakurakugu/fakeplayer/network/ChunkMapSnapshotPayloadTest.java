package com.sakurakugu.fakeplayer.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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

    private static ChunkMapSnapshotPayload.FakePlayerView view(boolean active, String dimension,
                                                                int chunkX, int chunkZ, int distance) {
        return new ChunkMapSnapshotPayload.FakePlayerView(UUID.randomUUID(), "Loader", "minecraft:overworld",
            0, 64, 0, 0.0F, true, true, distance, active, dimension, chunkX, chunkZ, distance);
    }
}
