package com.sakurakugu.fakeplayer.client.chunkloading;

import com.mojang.blaze3d.platform.NativeImage;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.MapColor;

/** 将客户端已经收到的区块转换为方块级地图纹理。 */
final class ChunkTerrainTileCache implements AutoCloseable {
    private static final int TILE_SIZE = 16;
    private static final int MAX_TILES = 2048;
    private static final int MAX_CREATED_PER_FRAME = 4;
    private static final int RELEASE_DELAY_FRAMES = 2;

    private final Minecraft minecraft;
    private final Map<TileKey, Tile> tiles = new LinkedHashMap<>(64, 0.75F, true);
    private final Deque<RetiredTile> retiredTiles = new ArrayDeque<>();
    private int creationBudget;
    private long frame;
    private long textureSequence;

    ChunkTerrainTileCache(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    void beginFrame() {
        frame++;
        releaseRetiredTiles();
        creationBudget = MAX_CREATED_PER_FRAME;
    }

    Identifier texture(int chunkX, int chunkZ, int sampleY) {
        ClientLevel level = minecraft.level;
        if (level == null) return null;
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return null;

        int layer = level.dimensionType().hasCeiling() ? Math.floorDiv(sampleY, 16) : 0;
        TileKey key = new TileKey(chunkX, chunkZ, layer);
        Tile cached = tiles.get(key);
        if (cached != null) return cached.identifier();
        if (creationBudget-- <= 0) return null;

        Identifier identifier = Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID,
            "chunk_map/" + Long.toUnsignedString(ChunkPos.pack(chunkX, chunkZ), 36) + "_" + layer
                + "_" + Long.toUnsignedString(textureSequence++, 36));
        NativeImage image = createImage(level, chunk, sampleY);
        minecraft.getTextureManager().register(identifier,
            new DynamicTexture(() -> "fakeplayer chunk map tile", image));
        tiles.put(key, new Tile(identifier));
        trim();
        return identifier;
    }

    private NativeImage createImage(ClientLevel level, LevelChunk chunk, int sampleY) {
        NativeImage image = new NativeImage(TILE_SIZE, TILE_SIZE, false);
        boolean ceiling = level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < TILE_SIZE; localZ++) {
            for (int localX = 0; localX < TILE_SIZE; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                int worldZ = chunk.getPos().getMinBlockZ() + localZ;
                int height = surfaceHeight(level, chunk, localX, localZ, sampleY, ceiling);
                position.set(worldX, height, worldZ);
                MapColor color = chunk.getBlockState(position).getMapColor(level, position);
                MapColor.Brightness brightness = brightness(level, chunk, localX, localZ, height, sampleY, ceiling);
                image.setPixel(localX, localZ, color == MapColor.NONE
                    ? 0xFF202428 : color.calculateARGBColor(brightness));
            }
        }
        return image;
    }

    private static int surfaceHeight(ClientLevel level, LevelChunk chunk, int localX, int localZ,
                                     int sampleY, boolean ceiling) {
        if (!ceiling) return chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
            localX, localZ) - 1;
        int worldX = chunk.getPos().getMinBlockX() + localX;
        int worldZ = chunk.getPos().getMinBlockZ() + localZ;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(worldX,
            Math.min(level.getMaxY() - 1, sampleY + 8), worldZ);
        while (cursor.getY() > level.getMinY() && level.getBlockState(cursor).isAir()) cursor.move(0, -1, 0);
        return cursor.getY();
    }

    private static MapColor.Brightness brightness(ClientLevel level, LevelChunk chunk, int localX, int localZ,
                                                   int height, int sampleY, boolean ceiling) {
        int northHeight = localZ > 0
            ? surfaceHeight(level, chunk, localX, localZ - 1, sampleY, ceiling)
            : height;
        if (height > northHeight) return MapColor.Brightness.HIGH;
        if (height < northHeight) return MapColor.Brightness.LOW;
        return MapColor.Brightness.NORMAL;
    }

    private void trim() {
        Iterator<Map.Entry<TileKey, Tile>> iterator = tiles.entrySet().iterator();
        while (tiles.size() > MAX_TILES && iterator.hasNext()) {
            Tile tile = iterator.next().getValue();
            // GUI 在提取完绘制状态后才真正提交 GPU，不能在当前帧立即释放纹理。
            retiredTiles.addLast(new RetiredTile(tile.identifier(), frame + RELEASE_DELAY_FRAMES));
            iterator.remove();
        }
    }

    private void releaseRetiredTiles() {
        while (!retiredTiles.isEmpty() && retiredTiles.peekFirst().releaseFrame() <= frame) {
            minecraft.getTextureManager().release(retiredTiles.removeFirst().identifier());
        }
    }

    @Override
    public void close() {
        tiles.values().forEach(tile -> minecraft.getTextureManager().release(tile.identifier()));
        retiredTiles.forEach(tile -> minecraft.getTextureManager().release(tile.identifier()));
        tiles.clear();
        retiredTiles.clear();
    }

    private record TileKey(int chunkX, int chunkZ, int layer) { }
    private record Tile(Identifier identifier) { }
    private record RetiredTile(Identifier identifier, long releaseFrame) { }
}
