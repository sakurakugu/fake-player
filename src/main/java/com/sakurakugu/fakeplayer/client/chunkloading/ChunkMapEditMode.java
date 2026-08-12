package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;

public enum ChunkMapEditMode {
    BROWSE,
    LOADED,
    TICKING,
    FULL,
    ERASE;

    public ManualLoadMode manualMode() {
        return switch (this) {
            case LOADED -> ManualLoadMode.LOADED;
            case TICKING -> ManualLoadMode.TICKING;
            case FULL -> ManualLoadMode.FULL;
            default -> throw new IllegalStateException("当前不是画笔模式");
        };
    }
}
