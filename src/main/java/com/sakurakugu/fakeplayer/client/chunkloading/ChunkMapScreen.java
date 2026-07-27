package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload.AnchorView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** 以玩家所在区块为中心显示加载点范围和区块坐标。 */
public final class ChunkMapScreen extends Screen {
    private static final int CELL_SIZE = 10;
    private static final int MAP_RADIUS = 12;
    private ChunkMapSnapshotPayload snapshot;
    private int[][] terrainColors;

    public ChunkMapScreen(ChunkMapSnapshotPayload snapshot) {
        super(Component.translatable("gui.fakeplayer.chunkloader.map_title"));
        this.snapshot = snapshot;
        rebuildTerrainColors();
    }

    public void update(ChunkMapSnapshotPayload value) {
        snapshot = value;
        rebuildTerrainColors();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 - 45, height - 30, 90, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int cells = MAP_RADIUS * 2 + 1;
        int mapSize = cells * CELL_SIZE;
        int left = width / 2 - mapSize / 2;
        int top = Math.max(28, (height - mapSize) / 2 - 2);
        graphics.fill(left - 3, top - 3, left + mapSize + 3, top + mapSize + 3, 0xEE171A1D);

        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                int chunkX = snapshot.playerChunkX() + offsetX;
                int chunkZ = snapshot.playerChunkZ() + offsetZ;
                int color = terrainColors[offsetZ + MAP_RADIUS][offsetX + MAP_RADIUS];
                for (AnchorView anchor : snapshot.anchors()) {
                    if (anchor.contains(chunkX, chunkZ)) {
                        color = blend(color, anchor.ticking() ? 0xFFB46F2D : 0xFF2D9060);
                    }
                }
                int x = left + (offsetX + MAP_RADIUS) * CELL_SIZE;
                int y = top + (offsetZ + MAP_RADIUS) * CELL_SIZE;
                graphics.fill(x, y, x + CELL_SIZE - 1, y + CELL_SIZE - 1, color);
            }
        }

        int center = MAP_RADIUS * CELL_SIZE;
        graphics.outline(left + center, top + center, CELL_SIZE, CELL_SIZE, 0xFFFFFFFF);
        for (AnchorView anchor : snapshot.anchors()) {
            int offsetX = anchor.chunkX() - snapshot.playerChunkX();
            int offsetZ = anchor.chunkZ() - snapshot.playerChunkZ();
            if (Math.abs(offsetX) <= MAP_RADIUS && Math.abs(offsetZ) <= MAP_RADIUS) {
                int x = left + (offsetX + MAP_RADIUS) * CELL_SIZE;
                int y = top + (offsetZ + MAP_RADIUS) * CELL_SIZE;
                graphics.outline(x, y, CELL_SIZE, CELL_SIZE, anchor.enabled() ? 0xFFFFFF55 : 0xFF777777);
            }
        }

        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.map_position",
            snapshot.playerChunkX(), snapshot.playerChunkZ(), snapshot.dimension()), width / 2, top + mapSize + 7,
            0xFFC8D6CF);

        int hoveredX = (mouseX - left) / CELL_SIZE - MAP_RADIUS;
        int hoveredZ = (mouseY - top) / CELL_SIZE - MAP_RADIUS;
        if (mouseX >= left && mouseY >= top && mouseX < left + mapSize && mouseY < top + mapSize) {
            int chunkX = snapshot.playerChunkX() + hoveredX;
            int chunkZ = snapshot.playerChunkZ() + hoveredZ;
            String names = snapshot.anchors().stream().filter(anchor -> anchor.contains(chunkX, chunkZ))
                .map(AnchorView::name).reduce((leftName, rightName) -> leftName + ", " + rightName).orElse("-");
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.map_hover", chunkX, chunkZ, names),
                left + 2, top - 14, 0xFFFFFFFF);
        }
    }

    private void rebuildTerrainColors() {
        terrainColors = new int[MAP_RADIUS * 2 + 1][MAP_RADIUS * 2 + 1];
        var level = minecraft == null ? net.minecraft.client.Minecraft.getInstance().level : minecraft.level;
        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                int chunkX = snapshot.playerChunkX() + offsetX;
                int chunkZ = snapshot.playerChunkZ() + offsetZ;
                int fallback = ((chunkX + chunkZ) & 1) == 0 ? 0xFF30373B : 0xFF293034;
                int color = fallback;
                if (level != null) {
                    var chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                    if (chunk != null) {
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) - 1;
                        BlockPos position = new BlockPos((chunkX << 4) + 8, y, (chunkZ << 4) + 8);
                        MapColor mapColor = chunk.getBlockState(position).getMapColor(level, position);
                        if (mapColor != MapColor.NONE) {
                            color = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
                        }
                    }
                }
                terrainColors[offsetZ + MAP_RADIUS][offsetX + MAP_RADIUS] = color;
            }
        }
    }

    private static int blend(int background, int overlay) {
        int red = (((background >> 16) & 255) + ((overlay >> 16) & 255)) / 2;
        int green = (((background >> 8) & 255) + ((overlay >> 8) & 255)) / 2;
        int blue = ((background & 255) + (overlay & 255)) / 2;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
