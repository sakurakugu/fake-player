package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** 以客户端已加载地形为背景的区块加载编辑地图。 */
public final class ChunkMapScreen extends Screen implements ChunkLoadMapFrontend {
    private static final int MAP_TOP = 50;
    private static final int MAP_BOTTOM_MARGIN = 32;
    private static final double MIN_SCALE = 0.35D;
    private static final double MAX_SCALE = 2.5D;
    private static final int UNLOADED_A = 0xFF20262A;
    private static final int UNLOADED_B = 0xFF252C30;

    private final ChunkLoadMapController controller;
    private final ChunkTerrainTileCache terrainTiles;
    private final Map<Long, ManualLoadMode> authoritativeModes = new HashMap<>();
    private double centerBlockX;
    private double centerBlockZ;
    private double pixelsPerBlock = 0.75D;
    private boolean dragging;

    public ChunkMapScreen(ChunkMapSnapshotPayload snapshot) {
        super(Component.translatable("gui.fakeplayer.chunkloader.map_title"));
        controller = new ChunkLoadMapController(snapshot);
        terrainTiles = new ChunkTerrainTileCache(net.minecraft.client.Minecraft.getInstance());
        centerBlockX = snapshot.playerChunkX() * 16.0D + 8.0D;
        centerBlockZ = snapshot.playerChunkZ() * 16.0D + 8.0D;
        rebuildAuthoritativeModes();
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
        int bottom = mapBottom();
        graphics.fill(0, 0, width, height, 0xFF111518);
        graphics.enableScissor(0, MAP_TOP, width, bottom);
        drawTerrain(graphics, bottom);
        drawChunkOverlays(graphics, bottom);
        drawFakePlayers(graphics, bottom);
        drawPlayer(graphics, bottom);
        drawHoveredChunk(graphics, mouseX, mouseY, bottom);
        graphics.disableScissor();

        graphics.fill(0, 0, width, MAP_TOP, 0xE8171A1D);
        graphics.fill(0, bottom, width, height, 0xE8171A1D);
        graphics.centeredText(font, Component.literal(title.getString() + "  [" + label(controller.mode()) + "]"),
            width / 2, 8, 0xFFFFFFFF);
        int centerChunkX = Mth.floor(centerBlockX) >> 4;
        int centerChunkZ = Mth.floor(centerBlockZ) >> 4;
        Component status = Component.translatable("gui.fakeplayer.chunkloader.map_position",
            centerChunkX, centerChunkZ, controller.snapshot().dimension()).copy()
            .append("  ").append(Math.round(pixelsPerBlock * 100.0D) + "%");
        graphics.centeredText(font, status, width / 2, bottom + 10, 0xFFC8D6CF);
    }

    private void drawTerrain(GuiGraphicsExtractor graphics, int bottom) {
        int minChunkX = Mth.floor(screenToWorldX(0)) >> 4;
        int maxChunkX = Mth.floor(screenToWorldX(width - 1)) >> 4;
        int minChunkZ = Mth.floor(screenToWorldZ(MAP_TOP)) >> 4;
        int maxChunkZ = Mth.floor(screenToWorldZ(bottom - 1)) >> 4;
        int sampleY = minecraft.player == null ? 64 : minecraft.player.getBlockY();
        terrainTiles.beginFrame();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int left = worldToScreenX(chunkX * 16.0D);
                int top = worldToScreenZ(chunkZ * 16.0D);
                int right = worldToScreenX((chunkX + 1) * 16.0D);
                int tileBottom = worldToScreenZ((chunkZ + 1) * 16.0D);
                int color = ((chunkX + chunkZ) & 1) == 0 ? UNLOADED_A : UNLOADED_B;
                graphics.fill(left, top, right, tileBottom, color);
                Identifier texture = terrainTiles.texture(chunkX, chunkZ, sampleY);
                if (texture != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0.0F, 0.0F,
                        Math.max(1, right - left), Math.max(1, tileBottom - top), 16, 16, 16, 16);
                }
            }
        }
    }

    private void drawChunkOverlays(GuiGraphicsExtractor graphics, int bottom) {
        int minChunkX = Mth.floor(screenToWorldX(0)) >> 4;
        int maxChunkX = Mth.floor(screenToWorldX(width - 1)) >> 4;
        int minChunkZ = Mth.floor(screenToWorldZ(MAP_TOP)) >> 4;
        int maxChunkZ = Mth.floor(screenToWorldZ(bottom - 1)) >> 4;
        Map<Long, ManualLoadMode> painted = controller.painted();
        var erased = controller.erased();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long key = ChunkKey.pack(chunkX, chunkZ);
                ManualLoadMode mode = painted.get(key);
                if (mode == null && !erased.contains(key)) mode = authoritativeModes.get(key);
                int left = worldToScreenX(chunkX * 16.0D);
                int top = worldToScreenZ(chunkZ * 16.0D);
                int right = worldToScreenX((chunkX + 1) * 16.0D);
                int tileBottom = worldToScreenZ((chunkZ + 1) * 16.0D);
                if (mode != null) graphics.fill(left, top, right, tileBottom, modeColor(mode));
                if (pixelsPerBlock >= 0.65D) graphics.outline(left, top,
                    Math.max(1, right - left), Math.max(1, tileBottom - top), 0x283A4449);
            }
        }
    }

    private void drawFakePlayers(GuiGraphicsExtractor graphics, int bottom) {
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            double blockX = fake.x() + 0.5D;
            double blockZ = fake.z() + 0.5D;
            if (fake.enabled()) {
                outlineRange(graphics, blockX, blockZ, fake.simulationDistance(), 0xFF4EC9E8);
                outlineRange(graphics, blockX, blockZ, 2, 0xFFE8C34E);
                outlineRange(graphics, blockX, blockZ, 8, 0xFFE86B62);
            }
            int x = worldToScreenX(blockX);
            int y = worldToScreenZ(blockZ);
            if (x >= 0 && x < width && y >= MAP_TOP && y < bottom) {
                graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
                graphics.outline(x - 3, y - 3, 7, 7, 0xFF202428);
            }
        }
    }

    private void outlineRange(GuiGraphicsExtractor graphics, double blockX, double blockZ, int radius, int color) {
        int centerChunkX = Mth.floor(blockX) >> 4;
        int centerChunkZ = Mth.floor(blockZ) >> 4;
        int left = worldToScreenX((centerChunkX - radius) * 16.0D);
        int top = worldToScreenZ((centerChunkZ - radius) * 16.0D);
        int right = worldToScreenX((centerChunkX + radius + 1) * 16.0D);
        int bottom = worldToScreenZ((centerChunkZ + radius + 1) * 16.0D);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), color);
    }

    private void drawPlayer(GuiGraphicsExtractor graphics, int bottom) {
        double x = controller.snapshot().playerChunkX() * 16.0D + 8.0D;
        double z = controller.snapshot().playerChunkZ() * 16.0D + 8.0D;
        int screenX = worldToScreenX(x);
        int screenY = worldToScreenZ(z);
        if (screenX >= 0 && screenX < width && screenY >= MAP_TOP && screenY < bottom) {
            graphics.fill(screenX - 2, screenY - 5, screenX + 3, screenY + 6, 0xFFFFFFFF);
            graphics.fill(screenX - 5, screenY - 2, screenX + 6, screenY + 3, 0xFFFFFFFF);
            graphics.outline(screenX - 6, screenY - 6, 13, 13, 0xFF202428);
        }
    }

    private void drawHoveredChunk(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int bottom) {
        int[] chunk = chunkAt(mouseX, mouseY);
        if (chunk == null) return;
        int left = worldToScreenX(chunk[0] * 16.0D);
        int top = worldToScreenZ(chunk[1] * 16.0D);
        int right = worldToScreenX((chunk[0] + 1) * 16.0D);
        int tileBottom = worldToScreenZ((chunk[1] + 1) * 16.0D);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, tileBottom - top), 0xFFFFFFFF);
        String names = controller.snapshot().regions().stream()
            .filter(region -> region.contains(chunk[0], chunk[1]))
            .map(region -> region.name() + ":" + region.mode().name().toLowerCase(Locale.ROOT))
            .reduce((a, b) -> a + ", " + b).orElse("-");
        Component hover = Component.translatable("gui.fakeplayer.chunkloader.map_hover", chunk[0], chunk[1], names);
        int textX = Math.max(4, Math.min(width - font.width(hover) - 4, mouseX + 10));
        int textY = Math.max(MAP_TOP + 4, Math.min(bottom - 13, mouseY + 10));
        graphics.fill(textX - 2, textY - 2, textX + font.width(hover) + 2, textY + 11, 0xD9111518);
        graphics.text(font, hover, textX, textY, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int[] chunk = chunkAt(event.x(), event.y());
        if (chunk == null) return false;
        dragging = true;
        if (event.button() == 0 && controller.mode() != ChunkMapEditMode.BROWSE) {
            controller.edit(chunk[0], chunk[1]);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY);
        if (controller.mode() == ChunkMapEditMode.BROWSE || event.button() == 1) {
            centerBlockX -= deltaX / pixelsPerBlock;
            centerBlockZ -= deltaY / pixelsPerBlock;
        } else {
            int[] chunk = chunkAt(event.x(), event.y());
            if (chunk != null) controller.edit(chunk[0], chunk[1]);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!insideMap(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        double worldX = screenToWorldX(mouseX);
        double worldZ = screenToWorldZ(mouseY);
        double next = Mth.clamp(pixelsPerBlock * Math.pow(1.2D, verticalAmount), MIN_SCALE, MAX_SCALE);
        if (next != pixelsPerBlock) {
            pixelsPerBlock = next;
            centerBlockX = worldX - (mouseX - width / 2.0D) / pixelsPerBlock;
            centerBlockZ = worldZ - (mouseY - mapCenterY()) / pixelsPerBlock;
        }
        return true;
    }

    private int[] chunkAt(double mouseX, double mouseY) {
        if (!insideMap(mouseX, mouseY)) return null;
        return new int[]{Mth.floor(screenToWorldX(mouseX)) >> 4, Mth.floor(screenToWorldZ(mouseY)) >> 4};
    }

    private boolean insideMap(double x, double y) {
        return x >= 0 && x < width && y >= MAP_TOP && y < mapBottom();
    }

    private int worldToScreenX(double worldX) {
        return Mth.floor(width / 2.0D + (worldX - centerBlockX) * pixelsPerBlock);
    }

    private int worldToScreenZ(double worldZ) {
        return Mth.floor(mapCenterY() + (worldZ - centerBlockZ) * pixelsPerBlock);
    }

    private double screenToWorldX(double screenX) {
        return centerBlockX + (screenX - width / 2.0D) / pixelsPerBlock;
    }

    private double screenToWorldZ(double screenY) {
        return centerBlockZ + (screenY - mapCenterY()) / pixelsPerBlock;
    }

    private int mapBottom() { return Math.max(MAP_TOP + 1, height - MAP_BOTTOM_MARGIN); }
    private double mapCenterY() { return (MAP_TOP + mapBottom()) / 2.0D; }

    private void rebuildAuthoritativeModes() {
        authoritativeModes.clear();
        for (var region : controller.snapshot().regions()) {
            if (!region.enabled()) continue;
            for (long chunk : region.chunks()) {
                authoritativeModes.merge(chunk, region.mode(), (left, right) ->
                    left.ordinal() >= right.ordinal() ? left : right);
            }
        }
    }

    private static int modeColor(ManualLoadMode mode) { return switch (mode) {
        case LOADED -> 0x66287E8E; case TICKING -> 0x66D18B35; case FULL -> 0x66C94D55;
    }; }

    private static String label(ChunkMapEditMode mode) { return switch (mode) {
        case BROWSE -> "浏览"; case LOADED -> "弱"; case TICKING -> "强"; case FULL -> "完整"; case ERASE -> "擦除";
    }; }

    @Override
    public void acceptSnapshot(ChunkMapSnapshotPayload snapshot) {
        controller.accept(snapshot);
        rebuildAuthoritativeModes();
    }

    @Override
    public void focus(String dimension, double blockX, double blockZ) {
        if (dimension.equals(controller.snapshot().dimension())) {
            centerBlockX = blockX;
            centerBlockZ = blockZ;
        }
    }

    @Override public void setEditMode(ChunkMapEditMode mode) { controller.setMode(mode); }
    @Override public void close() { onClose(); }

    @Override
    public void removed() {
        terrainTiles.close();
    }
}
