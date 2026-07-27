package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import net.minecraft.client.Minecraft;

/** 保存服务端最近一次同步的加载点快照。 */
public final class ClientChunkLoadingState {
    private static ChunkMapSnapshotPayload snapshot;
    private static boolean hudEnabled = true;

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

    public static void clear() {
        snapshot = null;
    }
}
