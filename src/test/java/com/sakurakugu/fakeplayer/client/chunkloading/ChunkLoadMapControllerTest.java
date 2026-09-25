package com.sakurakugu.fakeplayer.client.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import java.util.List;
import java.util.Map;
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

    @Test
    void draftOverridesAuthoritativeAndEraseHidesIt() {
        long painted = ChunkKey.pack(0, 0);
        long erased = ChunkKey.pack(1, 0);
        long untouched = ChunkKey.pack(2, 0);
        var authoritative = Map.of(painted, ChunkMapLoadLevel.WEAK,
            erased, ChunkMapLoadLevel.BLOCK_TICKING, untouched, ChunkMapLoadLevel.WEAK);

        var levels = ChunkLoadMapController.overlayLevels(
            Set.of(painted), Set.of(erased), authoritative);

        // 草稿画过的按强加载显示，草稿擦除的不再回落到权威等级
        assertEquals(ChunkMapLoadLevel.STRONG, levels.get(painted));
        assertEquals(null, levels.get(erased));
        assertEquals(ChunkMapLoadLevel.WEAK, levels.get(untouched));
        assertEquals(2, levels.size());
    }

    @Test
    void draftVersionOnlyMovesWhenTheDraftChanges() {
        var controller = new ChunkLoadMapController(snapshot(1L));
        int initial = controller.draftVersion();

        controller.edit(3, 4);
        assertEquals(initial, controller.draftVersion(), "浏览模式下不该改草稿");

        controller.setMode(ChunkMapEditMode.STRONG);
        controller.edit(3, 4);
        int afterPaint = controller.draftVersion();
        assertTrue(afterPaint > initial);
        assertTrue(controller.painted().contains(ChunkKey.pack(3, 4)));

        // 同一格重复涂改不算变化，绘制缓存不必失效
        controller.edit(3, 4);
        assertEquals(afterPaint, controller.draftVersion());

        controller.undo();
        assertTrue(controller.draftVersion() > afterPaint);
        assertTrue(controller.painted().isEmpty());
    }

    static ChunkMapSnapshotPayload snapshot(long revision) {
        return new ChunkMapSnapshotPayload(false, false, false, 0, 32, revision, false,
            "minecraft:overworld", 0, 0, List.of(), List.of(), List.of());
    }
}
