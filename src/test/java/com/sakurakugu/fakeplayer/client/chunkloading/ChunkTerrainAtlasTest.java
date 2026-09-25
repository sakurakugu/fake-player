package com.sakurakugu.fakeplayer.client.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 只测纯逻辑：图集本身要 GPU，测试环境里建不出来。 */
class ChunkTerrainAtlasTest {
    /** 渲染距离最大 32 时，客户端区块缓存的范围（见 ClientChunkCache.calculateStorageRange）。 */
    private static final int MAX_PATCH_CHUNKS = 2 * (32 + 4) + 1;

    @Test
    void cellCoversRoughlyOneScreenPixelPerTexel() {
        // 默认 0.75 像素/方块：一个方块比一个 texel 大，保持 1 区块一格
        assertEquals(1, ChunkTerrainAtlas.chooseCellChunks(0.75D, MAX_PATCH_CHUNKS));
        // 放大时更不该合并
        assertEquals(1, ChunkTerrainAtlas.chooseCellChunks(2.5D, MAX_PATCH_CHUNKS));
        // 缩到最小 0.35：一个 texel 大约跨 2.86 个方块，取 4
        assertEquals(4, ChunkTerrainAtlas.chooseCellChunks(0.35D, MAX_PATCH_CHUNKS));
        // 再小就继续翻倍，但上限是 SLOT_SIZE：再粗一个格就没有 texel 可用
        assertEquals(8, ChunkTerrainAtlas.chooseCellChunks(0.125D, MAX_PATCH_CHUNKS));
        assertEquals(ChunkTerrainAtlas.SLOT_SIZE,
            ChunkTerrainAtlas.chooseCellChunks(0.02D, MAX_PATCH_CHUNKS));
    }

    @Test
    void cellsAlwaysFitInTheAtlasAtRealPatchSizes() {
        for (double scale = 0.005D; scale <= 4.0D; scale += 0.005D) {
            int cellChunks = ChunkTerrainAtlas.chooseCellChunks(scale, MAX_PATCH_CHUNKS);
            int cells = (MAX_PATCH_CHUNKS + cellChunks - 1) / cellChunks;
            assertTrue(cells <= ChunkTerrainAtlas.slotLimitPerAxis(),
                "缩放 " + scale + " 时每边 " + cells + " 格，超过图集容量");
            // 每个子区块至少要占满 1 个 texel，否则 fillSubChunk 的循环一次都不走
            assertTrue(ChunkTerrainAtlas.SLOT_SIZE / cellChunks > 0,
                "缩放 " + scale + " 时每个子区块的 texel 边长是 0");
        }
    }

    @Test
    void slotPositionOnlyDependsOnCellCoordinates() {
        int limit = ChunkTerrainAtlas.slotLimitPerAxis();
        for (int cell = -2 * limit; cell <= 2 * limit; cell++) {
            assertTrue(ChunkTerrainAtlas.slotU(cell) >= 0
                && ChunkTerrainAtlas.slotU(cell) + ChunkTerrainAtlas.SLOT_SIZE <= ChunkTerrainAtlas.atlasWidth());
            assertTrue(ChunkTerrainAtlas.slotV(cell) >= 0
                && ChunkTerrainAtlas.slotV(cell) + ChunkTerrainAtlas.SLOT_SIZE <= ChunkTerrainAtlas.atlasHeight());
        }
    }
}
