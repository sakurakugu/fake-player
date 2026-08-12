package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload.AnchorView;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** 无第三方地图时可独立完成查看、绘制、擦除、撤销和批量应用。 */
public final class ChunkMapScreen extends Screen implements ChunkLoadMapFrontend {
    private static final int MAP_RADIUS = 12;
    private final ChunkLoadMapController controller;
    private int centerChunkX;
    private int centerChunkZ;
    private int cellSize = 12;
    private boolean dragging;
    private int[][] terrainColors;

    public ChunkMapScreen(ChunkMapSnapshotPayload snapshot) {
        super(Component.translatable("gui.fakeplayer.chunkloader.map_title"));
        controller = new ChunkLoadMapController(snapshot);
        centerChunkX = snapshot.playerChunkX();
        centerChunkZ = snapshot.playerChunkZ();
        rebuildTerrainColors();
    }

    public void update(ChunkMapSnapshotPayload value) { acceptSnapshot(value); }

    @Override
    protected void init() {
        int buttonWidth = Math.max(44, Math.min(62, (width - 20) / 7));
        int total = buttonWidth * 7;
        int x = (width - total) / 2;
        for (ChunkMapEditMode mode : ChunkMapEditMode.values()) {
            addRenderableWidget(Button.builder(Component.literal(label(mode)), button -> setEditMode(mode))
                .bounds(x, 24, buttonWidth, 20).build());
            x += buttonWidth;
        }
        addRenderableWidget(Button.builder(Component.literal("撤销"), button -> controller.undo())
            .bounds(width / 2 - 102, height - 26, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("应用"), button -> controller.apply())
            .bounds(width / 2 - 32, height - 26, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 + 38, height - 26, 64, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int mapSize = (MAP_RADIUS * 2 + 1) * cellSize;
        int left = width / 2 - mapSize / 2;
        int top = Math.max(50, (height - mapSize) / 2);
        graphics.fill(left - 3, top - 3, left + mapSize + 3, top + mapSize + 3, 0xEE171A1D);

        for (int oz = -MAP_RADIUS; oz <= MAP_RADIUS; oz++) {
            for (int ox = -MAP_RADIUS; ox <= MAP_RADIUS; ox++) {
                int chunkX = centerChunkX + ox;
                int chunkZ = centerChunkZ + oz;
                int color = terrainColors[oz + MAP_RADIUS][ox + MAP_RADIUS];
                for (AnchorView region : controller.snapshot().regions()) {
                    if (region.contains(chunkX, chunkZ)) color = blend(color, modeColor(region.mode()));
                }
                ManualLoadMode draft = controller.painted().get(ChunkKey.pack(chunkX, chunkZ));
                if (draft != null) color = blend(color, modeColor(draft));
                int x = left + (ox + MAP_RADIUS) * cellSize;
                int y = top + (oz + MAP_RADIUS) * cellSize;
                graphics.fill(x, y, x + cellSize - 1, y + cellSize - 1, color);
            }
        }

        drawFakePlayers(graphics, left, top);
        int playerX = controller.snapshot().playerChunkX() - centerChunkX;
        int playerZ = controller.snapshot().playerChunkZ() - centerChunkZ;
        if (visible(playerX, playerZ)) graphics.outline(left + (playerX + MAP_RADIUS) * cellSize,
            top + (playerZ + MAP_RADIUS) * cellSize, cellSize, cellSize, 0xFFFFFFFF);

        graphics.centeredText(font, Component.literal(title.getString() + "  [" + label(controller.mode()) + "]"),
            width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.map_position",
            centerChunkX, centerChunkZ, controller.snapshot().dimension()), width / 2, top + mapSize + 7, 0xFFC8D6CF);
        int[] hovered = chunkAt(mouseX, mouseY, left, top, mapSize);
        if (hovered != null) {
            String names = controller.snapshot().regions().stream().filter(region -> region.contains(hovered[0], hovered[1]))
                .map(region -> region.name() + ":" + region.mode().name().toLowerCase(Locale.ROOT))
                .reduce((a, b) -> a + ", " + b).orElse("-");
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.map_hover", hovered[0], hovered[1], names),
                left + 2, top - 14, 0xFFFFFFFF);
        }
    }

    private void drawFakePlayers(GuiGraphicsExtractor graphics, int left, int top) {
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            int ox = (fake.x() >> 4) - centerChunkX;
            int oz = (fake.z() >> 4) - centerChunkZ;
            if (fake.enabled()) {
                outlineRange(graphics, left, top, ox, oz, fake.simulationDistance(), 0xFF4EC9E8);
                outlineRange(graphics, left, top, ox, oz, 2, 0xFFE8C34E);
                outlineRange(graphics, left, top, ox, oz, 8, 0xFFE86B62);
            }
            if (visible(ox, oz)) {
                int x = left + (ox + MAP_RADIUS) * cellSize;
                int y = top + (oz + MAP_RADIUS) * cellSize;
                graphics.fill(x + cellSize / 2 - 1, y + cellSize / 2 - 1,
                    x + cellSize / 2 + 2, y + cellSize / 2 + 2, 0xFFFFFFFF);
            }
        }
    }

    private void outlineRange(GuiGraphicsExtractor graphics, int left, int top, int ox, int oz, int radius, int color) {
        int minX = Math.max(-MAP_RADIUS, ox - radius);
        int minZ = Math.max(-MAP_RADIUS, oz - radius);
        int maxX = Math.min(MAP_RADIUS, ox + radius);
        int maxZ = Math.min(MAP_RADIUS, oz + radius);
        if (minX > maxX || minZ > maxZ) return;
        graphics.outline(left + (minX + MAP_RADIUS) * cellSize, top + (minZ + MAP_RADIUS) * cellSize,
            (maxX - minX + 1) * cellSize, (maxZ - minZ + 1) * cellSize, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int mapSize = (MAP_RADIUS * 2 + 1) * cellSize;
        int left = width / 2 - mapSize / 2;
        int top = Math.max(50, (height - mapSize) / 2);
        int[] chunk = chunkAt(event.x(), event.y(), left, top, mapSize);
        if (chunk == null) return false;
        dragging = true;
        if (event.button() == 0 && controller.mode() != ChunkMapEditMode.BROWSE) controller.edit(chunk[0], chunk[1]);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY);
        if (controller.mode() == ChunkMapEditMode.BROWSE || event.button() == 1) {
            centerChunkX -= (int) Math.round(deltaX / cellSize);
            centerChunkZ -= (int) Math.round(deltaY / cellSize);
            rebuildTerrainColors();
        } else {
            int mapSize = (MAP_RADIUS * 2 + 1) * cellSize;
            int[] chunk = chunkAt(event.x(), event.y(), width / 2 - mapSize / 2,
                Math.max(50, (height - mapSize) / 2), mapSize);
            if (chunk != null) controller.edit(chunk[0], chunk[1]);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return super.mouseReleased(event); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int next = Math.max(6, Math.min(18, cellSize + (verticalAmount > 0 ? 2 : -2)));
        if (next != cellSize) { cellSize = next; rebuildTerrainColors(); }
        return true;
    }

    private int[] chunkAt(double mouseX, double mouseY, int left, int top, int mapSize) {
        if (mouseX < left || mouseY < top || mouseX >= left + mapSize || mouseY >= top + mapSize) return null;
        return new int[]{centerChunkX + (int) ((mouseX - left) / cellSize) - MAP_RADIUS,
            centerChunkZ + (int) ((mouseY - top) / cellSize) - MAP_RADIUS};
    }

    private void rebuildTerrainColors() {
        terrainColors = new int[MAP_RADIUS * 2 + 1][MAP_RADIUS * 2 + 1];
        var level = minecraft == null ? net.minecraft.client.Minecraft.getInstance().level : minecraft.level;
        for (int oz = -MAP_RADIUS; oz <= MAP_RADIUS; oz++) for (int ox = -MAP_RADIUS; ox <= MAP_RADIUS; ox++) {
            int chunkX = centerChunkX + ox, chunkZ = centerChunkZ + oz;
            int color = ((chunkX + chunkZ) & 1) == 0 ? 0xFF30373B : 0xFF293034;
            if (level != null) {
                var chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk != null) {
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) - 1;
                    BlockPos position = new BlockPos((chunkX << 4) + 8, y, (chunkZ << 4) + 8);
                    MapColor mapColor = chunk.getBlockState(position).getMapColor(level, position);
                    if (mapColor != MapColor.NONE) color = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
                }
            }
            terrainColors[oz + MAP_RADIUS][ox + MAP_RADIUS] = color;
        }
    }

    private static int modeColor(ManualLoadMode mode) { return switch (mode) {
        case LOADED -> 0xFF287E8E; case TICKING -> 0xFFD18B35; case FULL -> 0xFFC94D55;
    }; }
    private static String label(ChunkMapEditMode mode) { return switch (mode) {
        case BROWSE -> "浏览"; case LOADED -> "弱"; case TICKING -> "强"; case FULL -> "完整"; case ERASE -> "擦除";
    }; }
    private static boolean visible(int x, int z) { return Math.abs(x) <= MAP_RADIUS && Math.abs(z) <= MAP_RADIUS; }
    private static int blend(int a, int b) { return 0xFF000000 | (((a >> 16 & 255) + (b >> 16 & 255)) / 2) << 16
        | (((a >> 8 & 255) + (b >> 8 & 255)) / 2) << 8 | ((a & 255) + (b & 255)) / 2; }

    @Override public void acceptSnapshot(ChunkMapSnapshotPayload snapshot) { controller.accept(snapshot); rebuildTerrainColors(); }
    @Override public void focus(String dimension, double blockX, double blockZ) {
        if (dimension.equals(controller.snapshot().dimension())) { centerChunkX = (int) blockX >> 4; centerChunkZ = (int) blockZ >> 4; rebuildTerrainColors(); }
    }
    @Override public void setEditMode(ChunkMapEditMode mode) { controller.setMode(mode); }
    @Override public void close() { onClose(); }
}
