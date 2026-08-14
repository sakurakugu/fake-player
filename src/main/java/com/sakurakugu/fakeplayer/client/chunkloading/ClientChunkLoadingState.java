package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.client.ClientGlobalSettings;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** 保存服务端最近一次同步的加载点快照。 */
public final class ClientChunkLoadingState {
    private static ChunkMapSnapshotPayload snapshot;
    private static ClientLevel terrainLevel;
    private static ChunkTerrainTileCache terrainTiles;

    private ClientChunkLoadingState() {
    }

    public static void accept(ChunkMapSnapshotPayload value) {
        snapshot = value;
        int transferSetting = FakePlayerConfig.GlobalSetting.CONTAINER_TRANSFER_BUTTONS.ordinal();
        ClientGlobalSettings.setContainerTransferButtons(
            (value.globalSettingsMask() & (1 << transferSetting)) != 0);
        if (value.openScreen()) {
            Minecraft.getInstance().setScreen(new ChunkMapScreen(
                value, value.openManagement(), value.openSettings()));
        } else if (Minecraft.getInstance().screen instanceof ChunkMapScreen screen) {
            screen.update(value);
        }
    }

    public static ChunkMapSnapshotPayload snapshot() {
        return snapshot;
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
        ClientGlobalSettings.clear();
        closeTerrainTiles();
    }

    private static void closeTerrainTiles() {
        if (terrainTiles != null) terrainTiles.close();
        terrainTiles = null;
        terrainLevel = null;
    }
}
