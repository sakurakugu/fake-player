package com.sakurakugu.fakeplayer.client.chunkloading;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * 地形底图图集：整张地图共用一张贴图，每个已加载区块占一个 16×16 的 slot。
 *
 * <p>slot 由区块格坐标直接映射（{@link #slotIndex}），既不维护分配器也不维护 LRU：
 * 客户端已经收到的区块只可能是以玩家为中心的一个方形区域，
 * 其边长（{@code 2 * (渲染距离 + 4) + 1}，最大 73）小于图集一边的 slot 数，
 * 所以可见的格之间永远不会互相覆盖 slot。
 *
 * <p>每帧的工作量只跟"已加载区块"有关，跟屏幕分辨率、缩放比例、区域数据量都无关：
 * 遍历已加载区域，命中已烘焙的 slot 就画一次 blit，没烘焙的按预算烘焙。
 * 一个格覆盖 {@code cellChunks} 个区块（按缩放取 1/2/4/8/16），
 * 缩得越小格越粗，使一个 texel 大约落在 1~2 个屏幕像素上。
 *
 * <p>换档（{@code cellChunks} 变化，即缩放跨过档位）会让所有格坐标失效、整张图集重烘，
 * 这时按 {@link #LOD_BURST_FRAMES} 帧的加速预算补齐，避免屏幕上的地形闪一下。
 */
final class ChunkTerrainAtlas implements AutoCloseable {
    /** 一个 slot 的 texel 边长。 */
    static final int SLOT_SIZE = 16;
    private static final int SLOTS_X = 96;
    private static final int SLOTS_Y = 96;
    private static final int SLOT_COUNT = SLOTS_X * SLOTS_Y;
    private static final int ATLAS_WIDTH = SLOTS_X * SLOT_SIZE;
    private static final int ATLAS_HEIGHT = SLOTS_Y * SLOT_SIZE;
    /**
     * 一个格最多覆盖的区块数：一是避免渲染距离被调到异常大时 slot 互相覆盖，
     * 二是再粗下去 {@code SLOT_SIZE / cellChunks} 会变成 0（一个 texel 都没有）。
     */
    private static final int MAX_CELL_CHUNKS = SLOT_SIZE;
    /** 每帧最多烘焙的格数（每格 16×16 个采样点）。 */
    private static final int MAX_BAKES_PER_FRAME = 64;
    /** 每帧最多上传的子矩形数与 texel 数。 */
    private static final int MAX_UPLOADS_PER_FRAME = 12;
    private static final int MAX_UPLOAD_TEXELS = 1 << 19;
    /** 判定为"没有内容"的格，隔多少帧复查一次。 */
    private static final int EMPTY_RECHECK_FRAMES = 10;
    /**
     * 缩放跨过 LOD 档位后，"加速补齐"持续的帧数。
     * 换档会让整张图集的格坐标全部改变、必须整体重烘，按平时的预算要几十帧才补完，
     * 那段时间屏幕上会看见底色闪一下，所以换档后几帧按 {@link #BURST_MULTIPLIER} 倍预算烘。
     */
    private static final int LOD_BURST_FRAMES = 3;
    private static final int BURST_MULTIPLIER = 6;
    /** 每帧滚动校验的已烘焙格数，用于发现区块被卸载或重新下发。 */
    private static final int MAX_VERIFICATIONS_PER_FRAME = 256;
    /** 从没探测过的格。 */
    private static final long NEVER_PROBED = Long.MIN_VALUE / 4;
    /** 空列（空气）的颜色。 */
    private static final int EMPTY_COLUMN_PIXEL = 0xFF202428;
    /** 该 texel 没有对应的已加载区块；参与亮度比较时按"和北边一样高"处理。 */
    private static final int MISSING_HEIGHT = Integer.MIN_VALUE;

    private final Minecraft minecraft;
    private final Identifier identifier =
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_map/terrain");
    private final DynamicTexture texture;
    private final NativeImage pixels;
    /** 该 slot 是否放着一格的已烘焙地形。不能用坐标当哨兵：(0,0) 号格的 pack 值就是 0。 */
    private final boolean[] occupied = new boolean[SLOT_COUNT];
    /** 烘焙时的附加参数：cellChunks | layer << 8，变化即整张图失效。 */
    private final int[] ownerSpecs = new int[SLOT_COUNT];
    /** 上次探测/烘焙该 slot 的帧号。 */
    private final long[] probedFrames = new long[SLOT_COUNT];
    /**
     * 上次在该 slot 上探测过的格（见 {@link #cellKey}）。
     * 只有"同一个格"才需要靠 {@link #EMPTY_RECHECK_FRAMES} 节流：slot 换了别的格时
     * 必须立刻重烘，否则缩放换档后要等十帧才补齐，屏幕中间会闪一下。
     */
    private final long[] probedCells = new long[SLOT_COUNT];
    /** 供滚动校验反查区块用的格坐标。 */
    private final int[] ownerCellX = new int[SLOT_COUNT];
    private final int[] ownerCellZ = new int[SLOT_COUNT];
    private final WeakReference<?>[] sources = new WeakReference<?>[SLOT_COUNT];
    /** 代表该格的区块坐标；滚动校验就查这一个，必须和 sources 指的是同一个区块。 */
    private final int[] sourceChunkX = new int[SLOT_COUNT];
    private final int[] sourceChunkZ = new int[SLOT_COUNT];
    /** 烘焙了但还没上传的 slot：这时 GPU 上还是上一格的内容，不能画。 */
    private final boolean[] pendingUpload = new boolean[SLOT_COUNT];
    /** 每个 slot 行的待上传列范围（像素），min > max 表示该行干净。 */
    private final int[] rowMinX = new int[SLOTS_Y];
    private final int[] rowMaxX = new int[SLOTS_Y];
    /** 烘焙时的临时高度缓存，下标是格内 texel 序号。 */
    private final int[] heights = new int[SLOT_SIZE * SLOT_SIZE];

    private int cellChunks = 1;
    private int layer;
    private int sampleY = 64;
    private int patchMinChunkX;
    private int patchMaxChunkX;
    private int patchMinChunkZ;
    private int patchMaxChunkZ;
    private int minCellX;
    private int maxCellX;
    private int minCellZ;
    private int maxCellZ;
    private int bakesLeft;
    private int verifyCursor;
    private int dirtyRows;
    /** 上一帧用的格粗度，用来发现 LOD 换档。负数表示还没定过档。 */
    private int previousCellChunks = -1;
    /** 还剩几帧走加速预算。 */
    private int burstFrames;
    /** 本帧是否走加速预算（endFrame 上传时也要用同一个判断）。 */
    private boolean burstThisFrame;
    private long frame;
    private boolean closed;

    ChunkTerrainAtlas(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.texture = new DynamicTexture("fakeplayer chunk map terrain", ATLAS_WIDTH, ATLAS_HEIGHT, true);
        this.pixels = texture.getPixels();
        java.util.Arrays.fill(rowMinX, Integer.MAX_VALUE);
        java.util.Arrays.fill(rowMaxX, -1);
        java.util.Arrays.fill(probedFrames, NEVER_PROBED);
        minecraft.getTextureManager().register(identifier, texture);
    }

    /**
     * 选择每格覆盖的区块数：取最接近 {@code 1 / pixelsPerBlock} 的 2 的幂，
     * 但要保证已加载区域能放进图集（放不下就继续加粗）。
     */
    static int chooseCellChunks(double pixelsPerBlock, int patchChunks) {
        int result = 1;
        double blocksPerTexel = 1.0D / Math.max(pixelsPerBlock, 1.0E-4D);
        while (result < MAX_CELL_CHUNKS && blocksPerTexel >= result * Math.sqrt(2.0D)) result <<= 1;
        int limit = Math.min(SLOTS_X, SLOTS_Y);
        while (result < MAX_CELL_CHUNKS && (patchChunks + result - 1) / result > limit) result <<= 1;
        return result;
    }

    /** 是否已经释放；释放后不能再碰贴图。 */
    boolean isClosed() { return closed; }

    /** 开始一帧：按当前缩放确定格粗度，并重新计算已加载区域。 */
    void beginFrame(double pixelsPerBlock, int sampleY) {
        if (closed) return;
        frame++;
        this.sampleY = sampleY;
        ClientLevel level = minecraft.level;
        this.layer = level != null && level.dimensionType().hasCeiling() ? Math.floorDiv(sampleY, 16) : 0;
        bakesLeft = MAX_BAKES_PER_FRAME;
        // 客户端区块缓存的范围，见 ClientChunkCache.calculateStorageRange
        int radius = Math.max(2, minecraft.options.getEffectiveRenderDistance()) + 4;
        int patchChunks = 2 * radius + 1;
        int chosen = chooseCellChunks(pixelsPerBlock, patchChunks);
        if (previousCellChunks < 0) {
            // 第一帧不算换档，免得打开地图时多花一帧预算
            previousCellChunks = chosen;
        } else if (chosen != previousCellChunks) {
            previousCellChunks = chosen;
            burstFrames = LOD_BURST_FRAMES;
        }
        this.cellChunks = chosen;
        burstThisFrame = burstFrames > 0;
        if (burstThisFrame) burstFrames--;
        bakesLeft = burstThisFrame ? MAX_BAKES_PER_FRAME * BURST_MULTIPLIER : MAX_BAKES_PER_FRAME;
        verifyRolling();
        if (minecraft.player == null) {
            patchMinChunkX = patchMaxChunkX = patchMinChunkZ = patchMaxChunkZ = 0;
        } else {
            int chunkX = minecraft.player.chunkPosition().x();
            int chunkZ = minecraft.player.chunkPosition().z();
            patchMinChunkX = chunkX - radius;
            patchMaxChunkX = chunkX + radius;
            patchMinChunkZ = chunkZ - radius;
            patchMaxChunkZ = chunkZ + radius;
        }
        minCellX = Math.floorDiv(patchMinChunkX, cellChunks);
        maxCellX = Math.floorDiv(patchMaxChunkX, cellChunks);
        minCellZ = Math.floorDiv(patchMinChunkZ, cellChunks);
        maxCellZ = Math.floorDiv(patchMaxChunkZ, cellChunks);
    }

    /** 一个格覆盖的方块边长。 */
    int cellSpanBlocks() {
        return SLOT_SIZE * cellChunks;
    }

    int minCellX() { return minCellX; }
    int maxCellX() { return maxCellX; }
    int minCellZ() { return minCellZ; }
    int maxCellZ() { return maxCellZ; }

    /** 供合并绘制使用的贴图绑定，等价于原版 {@code blit(Identifier, ...)} 内部的构造方式。 */
    TextureSetup textureSetup() {
        return TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
    }

    /** 图集一边最多能放多少个格；超过就会互相覆盖 slot。 */
    static int slotLimitPerAxis() { return Math.min(SLOTS_X, SLOTS_Y); }

    /** slot 在图集里的像素位置。 */
    static int slotU(int cellX) { return Math.floorMod(cellX, SLOTS_X) * SLOT_SIZE; }
    static int slotV(int cellZ) { return Math.floorMod(cellZ, SLOTS_Y) * SLOT_SIZE; }
    static int atlasWidth() { return ATLAS_WIDTH; }
    static int atlasHeight() { return ATLAS_HEIGHT; }

    /**
     * 确保该格可以绘制：已烘焙就直接画，否则按预算烘焙。
     *
     * @return 该 slot 是否已经放着这一格的地形
     */
    boolean prepare(int cellX, int cellZ) {
        if (closed) return false;
        int slot = slotIndex(cellX, cellZ);
        int spec = spec();
        if (occupied[slot] && ownerCellX[slot] == cellX && ownerCellZ[slot] == cellZ
            && ownerSpecs[slot] == spec) {
            // 烘焙完还没上传的 slot，GPU 上仍是上一格的内容，本帧先不画
            return !pendingUpload[slot];
        }
        // 只对"刚探测过、结果还是没有内容"的同一个格节流
        long key = cellKey(cellX, cellZ, spec);
        if (probedCells[slot] == key && frame - probedFrames[slot] < EMPTY_RECHECK_FRAMES) return false;
        probedCells[slot] = key;
        probedFrames[slot] = frame;
        if (bakesLeft <= 0) {
            // 本帧预算用完了，下一帧再试
            probedFrames[slot] = NEVER_PROBED;
            return false;
        }
        return bake(slot, cellX, cellZ, spec);
    }

    /** 把格坐标与格粗度打包成 key；区块坐标远小于 2^24，拼起来不会撞。 */
    private static long cellKey(int cellX, int cellZ, int spec) {
        // spec 至少是 1（cellChunks >= 1），所以合法 key 不会是 0
        return ((long) (cellX & 0xFFFFFF) << 40) | ((long) (cellZ & 0xFFFFFF) << 16) | (spec & 0xFFFF);
    }

    /** 把本帧烘焙的 slot 上传到 GPU，按图集行合并成少数几个子矩形。 */
    void endFrame() {
        // 没有脏行就别开命令编码器
        if (closed || dirtyRows == 0) return;
        int uploads = 0;
        int texels = MAX_UPLOAD_TEXELS;
        // 换档那几帧会一次脏很多行，按平时的行数上限会把地形分几帧才补上去
        int uploadLimit = burstThisFrame ? MAX_UPLOADS_PER_FRAME * 4 : MAX_UPLOADS_PER_FRAME;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        while (uploads < uploadLimit && texels > 0) {
            int best = -1;
            int bestTexels = 0;
            for (int row = 0; row < SLOTS_Y; row++) {
                if (rowMinX[row] > rowMaxX[row]) continue;
                int rowTexels = (rowMaxX[row] - rowMinX[row] + SLOT_SIZE) * SLOT_SIZE;
                if (rowTexels <= texels && rowTexels > bestTexels) {
                    best = row;
                    bestTexels = rowTexels;
                }
            }
            if (best < 0) break;
            // rowMinX/rowMaxX 存的已经是像素坐标（slotU 的返回值），宽度别再多乘一次 SLOT_SIZE
            int x = rowMinX[best];
            int y = best * SLOT_SIZE;
            int width = rowMaxX[best] - rowMinX[best] + SLOT_SIZE;
            encoder.writeToTexture(texture.getTexture(), pixels, 0, 0, x, y, width, SLOT_SIZE, x, y);
            for (int column = 0; column < SLOTS_X; column++) {
                pendingUpload[best * SLOTS_X + column] = false;
            }
            rowMinX[best] = Integer.MAX_VALUE;
            rowMaxX[best] = -1;
            texels -= bestTexels;
            uploads++;
            dirtyRows--;
        }
    }

    /** 发现区块被卸载或重新下发时丢掉对应 slot，避免一直画旧地形。 */
    private void verifyRolling() {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        int spec = spec();
        int remaining = MAX_VERIFICATIONS_PER_FRAME;
        int walked = 0;
        while (walked < SLOT_COUNT && remaining > 0) {
            int slot = verifyCursor;
            verifyCursor = verifyCursor + 1 >= SLOT_COUNT ? 0 : verifyCursor + 1;
            walked++;
            if (!occupied[slot]) continue;
            remaining--;
            if (ownerSpecs[slot] != spec) {
                invalidate(slot);
                continue;
            }
            // 必须查烘焙时真正取样的那个区块：cellChunks > 1 时它不一定在格中心，
            // 查错了会每帧都判定失效，图集缓存等于没有
            LevelChunk chunk = chunkAt(sourceChunkX[slot], sourceChunkZ[slot]);
            if (chunk == null || chunk != sources[slot].get()) invalidate(slot);
        }
    }

    private boolean bake(int slot, int cellX, int cellZ, int spec) {
        bakesLeft--;
        int slotX = slotU(cellX);
        int slotY = slotV(cellZ);
        int span = cellSpanBlocks();
        int baseX = cellX * span;
        int baseZ = cellZ * span;
        int texels = SLOT_SIZE / cellChunks;
        // 未加载的部分保持透明，直接露出背景色
        pixels.fillRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE, 0);
        LevelChunk source = null;
        int sourceX = 0;
        int sourceZ = 0;
        for (int subZ = 0; subZ < cellChunks; subZ++) {
            for (int subX = 0; subX < cellChunks; subX++) {
                int chunkX = cellX * cellChunks + subX;
                int chunkZ = cellZ * cellChunks + subZ;
                LevelChunk chunk = chunkAt(chunkX, chunkZ);
                if (chunk == null) {
                    // 没加载的子区块也要写高度哨兵，否则亮度比较会读到上一格留下的残留值
                    clearHeights(subX, subZ, texels);
                    continue;
                }
                if (source == null) {
                    source = chunk;
                    sourceX = chunkX;
                    sourceZ = chunkZ;
                }
                fillSubChunk(chunk, slotX, slotY, subX, subZ, texels, baseX, baseZ);
            }
        }
        if (source == null) {
            // 这一格还没收到任何区块，先留空，过一会再探测
            occupied[slot] = false;
            return false;
        }
        occupied[slot] = true;
        pendingUpload[slot] = true;
        ownerSpecs[slot] = spec;
        ownerCellX[slot] = cellX;
        ownerCellZ[slot] = cellZ;
        sourceChunkX[slot] = sourceX;
        sourceChunkZ[slot] = sourceZ;
        sources[slot] = new WeakReference<>(source);
        probedFrames[slot] = frame;
        int row = slotY / SLOT_SIZE;
        if (rowMinX[row] > rowMaxX[row]) {
            rowMinX[row] = slotX;
            rowMaxX[row] = slotX;
            dirtyRows++;
        } else {
            rowMinX[row] = Math.min(rowMinX[row], slotX);
            rowMaxX[row] = Math.max(rowMaxX[row], slotX);
        }
        return true;
    }

    /** 把一个子区块对应的 texel 写进 slot。 */
    private void fillSubChunk(LevelChunk chunk, int slotX, int slotY, int subX, int subZ,
                              int texels, int baseX, int baseZ) {
        boolean ceiling = minecraft.level != null && minecraft.level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int firstTexelX = subX * texels;
        int firstTexelZ = subZ * texels;
        for (int tz = 0; tz < texels; tz++) {
            for (int tx = 0; tx < texels; tx++) {
                int blockX = baseX + (firstTexelX + tx) * cellChunks + (cellChunks >> 1);
                int blockZ = baseZ + (firstTexelZ + tz) * cellChunks + (cellChunks >> 1);
                heights[(firstTexelZ + tz) * SLOT_SIZE + firstTexelX + tx] =
                    surfaceHeight(chunk, blockX, blockZ, ceiling);
            }
        }
        for (int tz = 0; tz < texels; tz++) {
            for (int tx = 0; tx < texels; tx++) {
                int texelIndex = (firstTexelZ + tz) * SLOT_SIZE + firstTexelX + tx;
                int blockX = baseX + (firstTexelX + tx) * cellChunks + (cellChunks >> 1);
                int blockZ = baseZ + (firstTexelZ + tz) * cellChunks + (cellChunks >> 1);
                int height = heights[texelIndex];
                position.set(blockX, height, blockZ);
                MapColor color = chunk.getBlockState(position).getMapColor(minecraft.level, position);
                MapColor.Brightness brightness = brightnessOf(texelIndex, height,
                    tz > 0 || firstTexelZ > 0);
                pixels.setPixel(slotX + firstTexelX + tx, slotY + firstTexelZ + tz,
                    color == MapColor.NONE ? EMPTY_COLUMN_PIXEL : color.calculateARGBColor(brightness));
            }
        }
    }

    /** 原版地图的明暗：比北边高一格就更亮，低一格就更暗；缺数据的按持平处理。 */
    private MapColor.Brightness brightnessOf(int texelIndex, int height, boolean hasNorthTexel) {
        if (height == MISSING_HEIGHT) return MapColor.Brightness.NORMAL;
        int northHeight = hasNorthTexel ? heights[texelIndex - SLOT_SIZE] : height;
        if (northHeight == MISSING_HEIGHT) return MapColor.Brightness.NORMAL;
        return height > northHeight ? MapColor.Brightness.HIGH
            : height < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    /** 把子区块占用的 texel 范围标成"没有数据"，避免南北亮度读到上一个格留下的值。 */
    private void clearHeights(int subX, int subZ, int texels) {
        for (int tz = 0; tz < texels; tz++) {
            for (int tx = 0; tx < texels; tx++) {
                heights[(subZ * texels + tz) * SLOT_SIZE + subX * texels + tx] = MISSING_HEIGHT;
            }
        }
    }

    private int surfaceHeight(LevelChunk chunk, int blockX, int blockZ, boolean ceiling) {
        if (!ceiling) {
            // getHeight 返回的已经是最上层实体方块（原版地图也取这一层），别再减一，
            // 否则草地会取到下面的泥土、颜色整体发暗
            return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15);
        }
        ClientLevel level = minecraft.level;
        if (level == null) return sampleY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(blockX,
            Math.min(level.getMaxY() - 1, sampleY + 8), blockZ);
        while (cursor.getY() > level.getMinY() && level.getBlockState(cursor).isAir()) cursor.move(0, -1, 0);
        return cursor.getY();
    }

    private void invalidate(int slot) {
        occupied[slot] = false;
        probedFrames[slot] = NEVER_PROBED;
        probedCells[slot] = 0;
        sources[slot] = null;
    }

    private LevelChunk chunkAt(int chunkX, int chunkZ) {
        ClientLevel level = minecraft.level;
        if (level == null) return null;
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private int spec() { return cellChunks | (layer << 8); }

    private static int slotIndex(int cellX, int cellZ) {
        return Math.floorMod(cellX, SLOTS_X) + Math.floorMod(cellZ, SLOTS_Y) * SLOTS_X;
    }

    @Override
    public void close() {
        // 重复调用要幂等：释放之后 NativeImage 也跟着关了，再用就会抛异常
        if (closed) return;
        closed = true;
        minecraft.getTextureManager().release(identifier);
    }
}
