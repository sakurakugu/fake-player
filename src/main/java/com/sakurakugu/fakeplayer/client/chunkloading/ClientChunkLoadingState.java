package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** 保存服务端最近一次同步的加载点快照。 */
public final class ClientChunkLoadingState {
    private static ChunkMapSnapshotPayload snapshot;
    private static boolean hudEnabled = true;
    private static ClientLevel terrainLevel;
    private static ChunkTerrainTileCache terrainTiles;

    private ClientChunkLoadingState() {
    }

    public static void accept(ChunkMapSnapshotPayload value) {
        snapshot = value;
        if (value.openScreen()) {
            Minecraft.getInstance().setScreen(new ChunkMapScreen(value));
        } else if (Minecraft.getInstance().screen instanceof ChunkMapScreen screen) {
            screen.update(value);
        }
    }

    public static ChunkMapSnapshotPayload snapshot() {
        return snapshot;
    }

    public static boolean hudEnabled() {
        return hudEnabled;
    }

    public static void toggleHud() {
        hudEnabled = !hudEnabled;
    }

    static ChunkTerrainTileCache terrainTiles() {
        Minecraft minecraft = Minecraft.getInstance();
        if (terrainTiles == null || terrainLevel != minecraft.level) {
            closeTerrainTiles();
            terrainLevel = minecraft.level;
            terrainTiles = new ChunkTerrainTileCache(minecraft);
        }
        return terrainTiles;
    }

    public static void clear() {
        snapshot = null;
        closeTerrainTiles();
    }

    private static void closeTerrainTiles() {
        if (terrainTiles != null) terrainTiles.close();
        terrainTiles = null;
        terrainLevel = null;
    }
}
