package com.sakurakugu.fakeplayer.client.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkLoadMapControllerTest {
    @Test
    void nextRegionNameUsesFirstAvailableSuffixIgnoringCase() {
        assertEquals("region_3", ChunkLoadMapController.nextRegionName(
            List.of("region_1", "REGION_2", "main_base")));
    }

    @Test
    void strongTicketsExposeBothAutomaticPropagationRings() {
        long center = ChunkKey.pack(10, 20);
        var region = new ChunkMapSnapshotPayload.AnchorView(UUID.randomUUID(), "main",
            "minecraft:overworld", true, Set.of(center));
        var levels = ChunkLoadMapController.propagatedLevels(List.of(region), "minecraft:overworld");

        assertEquals(ChunkMapLoadLevel.STRONG, levels.get(center));
        assertEquals(ChunkMapLoadLevel.BLOCK_TICKING, levels.get(ChunkKey.pack(11, 20)));
        assertEquals(ChunkMapLoadLevel.WEAK, levels.get(ChunkKey.pack(12, 20)));
        assertEquals(null, levels.get(ChunkKey.pack(13, 20)));
    }
}
