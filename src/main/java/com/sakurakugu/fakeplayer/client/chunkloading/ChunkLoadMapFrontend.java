package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;

/** 内置地图与第三方地图共享的最小前端契约。 */
public interface ChunkLoadMapFrontend {
    void acceptSnapshot(ChunkMapSnapshotPayload snapshot);
    void focus(String dimension, double blockX, double blockZ);
    void setEditMode(ChunkMapEditMode mode);
    void close();
}
