package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.client.ClientGlobalSettings;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import com.sakurakugu.fakeplayer.network.RequestChunkMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** 保存服务端最近一次同步的加载点快照。 */
public final class ClientChunkLoadingState {
    private static ChunkMapSnapshotPayload snapshot;
    private static ClientLevel terrainLevel;
    private static ChunkTerrainAtlas terrainAtlas;

    private ClientChunkLoadingState() {
    }

    public static void accept(ChunkMapSnapshotPayload value) {
        ChunkMapSnapshotPayload previous = snapshot;
        // 服务端说区域没变时列表是空的，得把上一份拼回去
        boolean mergeable = value.regionsUnchanged() && previous != null
            && value.dimension().equals(previous.dimension());
        ChunkMapSnapshotPayload effective = mergeable ? value.withPreviousRegions(previous) : value;
        // 拼不出来（刚连接或刚换维度）就忘掉已知 revision，下一次请求要全量
        boolean lostRegions = value.regionsUnchanged() && !mergeable;
        snapshot = lostRegions ? null : effective;

        int transferSetting = FakePlayerConfig.GlobalSetting.CONTAINER_TRANSFER_BUTTONS.ordinal();
        ClientGlobalSettings.setContainerTransferButtons(
            (value.globalSettingsMask() & (1 << transferSetting)) != 0);
        if (value.openScreen()) {
            Minecraft.getInstance().setScreen(new ChunkMapScreen(
                effective, value.openManagement(), value.openSettings()));
        } else if (Minecraft.getInstance().screen instanceof ChunkMapScreen screen) {
            screen.update(effective);
        }
    }

    /** 按已知快照构造请求，让服务端能跳过区块列表。 */
    public static RequestChunkMapPayload request(boolean openScreen, boolean openManagement,
                                                 boolean openSettings) {
        ChunkMapSnapshotPayload known = snapshot;
        return known == null
            ? new RequestChunkMapPayload(openScreen, openManagement, openSettings)
            : new RequestChunkMapPayload(openScreen, openManagement, openSettings,
                known.revision(), known.dimension());
    }

    public static ChunkMapSnapshotPayload snapshot() {
        return snapshot;
    }

    /** 图集跟随连接存在：换维度时释放重建，同一个世界内数据一直有效。 */
    static ChunkTerrainAtlas terrainAtlas() {
        Minecraft minecraft = Minecraft.getInstance();
        if (terrainAtlas == null || terrainLevel != minecraft.level) {
            closeTerrainAtlas();
            terrainLevel = minecraft.level;
            terrainAtlas = new ChunkTerrainAtlas(minecraft);
        }
        return terrainAtlas;
    }

    public static void clear() {
        snapshot = null;
        ClientGlobalSettings.clear();
        closeTerrainAtlas();
    }

    private static void closeTerrainAtlas() {
        if (terrainAtlas != null) terrainAtlas.close();
        terrainAtlas = null;
        terrainLevel = null;
    }
}
