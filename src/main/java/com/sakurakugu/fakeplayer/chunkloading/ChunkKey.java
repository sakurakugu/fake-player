package com.sakurakugu.fakeplayer.chunkloading;

/** 与原版 ChunkPos 长整型布局一致，但不触发游戏注册表初始化。 */
public final class ChunkKey {
    private ChunkKey() {
    }

    public static long pack(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    public static int x(long packed) {
        return (int) packed;
    }

    public static int z(long packed) {
        return (int) (packed >>> 32);
    }
}
