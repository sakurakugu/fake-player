package com.sakurakugu.fakeplayer.client.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void strongChunksExposeOneWeakLoadingRing() {
        long center = ChunkKey.pack(10, 20);
        var levels = ChunkLoadMapController.overlayLevels(Set.of(), Set.of(), Set.of(center), true);

        assertEquals(ChunkMapLoadLevel.STRONG, levels.get(center));
        assertEquals(ChunkMapLoadLevel.WEAK, levels.get(ChunkKey.pack(11, 20)));
        // 等级 33 那一圈什么都不做，不再展示
        assertNull(levels.get(ChunkKey.pack(12, 20)));
        assertEquals(9, levels.size());
    }

    @Test
    void hidingTheWeakRangeLeavesOnlyStrongChunks() {
        long center = ChunkKey.pack(10, 20);
        var levels = ChunkLoadMapController.overlayLevels(Set.of(), Set.of(), Set.of(center), false);

        assertEquals(ChunkMapLoadLevel.STRONG, levels.get(center));
        assertNull(levels.get(ChunkKey.pack(11, 20)));
        assertEquals(1, levels.size());
    }

    @Test
    void draftPaintAddsAStrongChunkAndEraseRemovesItWithItsRing() {
        long regionChunk = ChunkKey.pack(10, 20);
        long paintedChunk = ChunkKey.pack(0, 0);

        var levels = ChunkLoadMapController.overlayLevels(
            Set.of(paintedChunk), Set.of(regionChunk), Set.of(regionChunk), true);

        // 擦掉的强加载区块连它带出来的弱加载圈一起消失
        assertNull(levels.get(regionChunk));
        assertNull(levels.get(ChunkKey.pack(11, 20)));
        // 草稿画出来的区块按强加载显示，并同步长出弱加载圈
        assertEquals(ChunkMapLoadLevel.STRONG, levels.get(paintedChunk));
        assertEquals(ChunkMapLoadLevel.WEAK, levels.get(ChunkKey.pack(1, 0)));
        assertEquals(9, levels.size());
    }

    @Test
    void eraseOnlyAcceptsChunksThatBelongToAnEnabledRegion() {
        var region = new ChunkMapSnapshotPayload.AnchorView(UUID.randomUUID(), "main",
            "minecraft:overworld", true, Set.of(ChunkKey.pack(10, 20)));
        var controller = new ChunkLoadMapController(snapshot(1L, List.of(region)));
        controller.setMode(ChunkMapEditMode.EDIT);

        // 擦除落在自动传播出来的弱加载圈上：不是手动区块，点了不该留痕
        controller.edit(11, 20, true);
        controller.edit(12, 20, true);
        assertTrue(controller.erased().isEmpty());
        assertFalse(controller.dirty());
        assertEquals(ChunkMapLoadLevel.WEAK, controller.levels().get(ChunkKey.pack(11, 20)));

        controller.edit(10, 20, true);
        assertTrue(controller.erased().contains(ChunkKey.pack(10, 20)));
        assertTrue(controller.dirty());
        // 手动区块被擦后不再显示，弱加载圈也随之收回
        assertNull(controller.levels().get(ChunkKey.pack(10, 20)));
        assertNull(controller.levels().get(ChunkKey.pack(11, 20)));
    }

    @Test
    void togglingTheWeakRangeInvalidatesTheCachedLevels() {
        var region = new ChunkMapSnapshotPayload.AnchorView(UUID.randomUUID(), "main",
            "minecraft:overworld", true, Set.of(ChunkKey.pack(10, 20)));
        var controller = new ChunkLoadMapController(snapshot(1L, List.of(region)));

        assertNotNull(controller.levels().get(ChunkKey.pack(11, 20)));
        controller.setShowWeakLoading(false);
        assertNull(controller.levels().get(ChunkKey.pack(11, 20)));
        assertEquals(ChunkMapLoadLevel.STRONG, controller.levels().get(ChunkKey.pack(10, 20)));
    }

    @Test
    void erasingOwnPaintJustTakesTheStrokeBack() {
        var controller = new ChunkLoadMapController(snapshot(1L));
        controller.setMode(ChunkMapEditMode.EDIT);
        controller.edit(5, 5, false);

        // 同一格换成右键，把刚涂的那一笔撤回来
        controller.edit(5, 5, true);

        // 没进过服务端的新区块谈不上"删除"，草稿回到干净状态而不是留下一条空编辑
        assertTrue(controller.painted().isEmpty());
        assertTrue(controller.erased().isEmpty());
        assertFalse(controller.dirty());
        assertTrue(controller.levels().isEmpty());
    }

    @Test
    void disabledRegionsAreNeitherDrawnNorErasable() {
        var region = new ChunkMapSnapshotPayload.AnchorView(UUID.randomUUID(), "main",
            "minecraft:overworld", false, Set.of(ChunkKey.pack(10, 20)));
        var controller = new ChunkLoadMapController(snapshot(1L, List.of(region)));
        controller.setMode(ChunkMapEditMode.EDIT);

        controller.edit(10, 20, true);

        assertTrue(controller.levels().isEmpty());
        assertFalse(controller.dirty());
    }

    @Test
    void applyWithNothingLeftToSubmitDropsTheDraft() {
        var region = new ChunkMapSnapshotPayload.AnchorView(UUID.randomUUID(), "main",
            "minecraft:overworld", true, Set.of(ChunkKey.pack(10, 20)));
        var controller = new ChunkLoadMapController(snapshot(1L, List.of(region)));
        controller.setMode(ChunkMapEditMode.EDIT);
        controller.edit(10, 20, true);
        assertTrue(controller.dirty());

        // 区域在别处被删掉了，草稿里的擦除已经没有落点
        controller.accept(snapshot(2L, List.of()));
        controller.apply();

        assertFalse(controller.dirty());
    }

    @Test
    void draftVersionOnlyMovesWhenTheDraftChanges() {
        var controller = new ChunkLoadMapController(snapshot(1L));
        int initial = controller.draftVersion();

        controller.edit(3, 4, false);
        assertEquals(initial, controller.draftVersion(), "浏览模式下不该改草稿");

        controller.setMode(ChunkMapEditMode.EDIT);
        controller.edit(3, 4, false);
        int afterPaint = controller.draftVersion();
        assertTrue(afterPaint > initial);
        assertTrue(controller.painted().contains(ChunkKey.pack(3, 4)));

        // 同一格重复涂改不算变化，绘制缓存不必失效
        controller.edit(3, 4, false);
        assertEquals(afterPaint, controller.draftVersion());

        controller.undo();
        assertTrue(controller.draftVersion() > afterPaint);
        assertTrue(controller.painted().isEmpty());
    }

    static ChunkMapSnapshotPayload snapshot(long revision) {
        return snapshot(revision, List.of());
    }

    static ChunkMapSnapshotPayload snapshot(long revision,
                                            List<ChunkMapSnapshotPayload.AnchorView> regions) {
        return new ChunkMapSnapshotPayload(false, false, false, 0, 32, revision, false,
            "minecraft:overworld", 0, 0, regions, List.of(), List.of());
    }
}
