package com.sakurakugu.fakeplayer.client.chunkloading;

/** 地图展示的实际票据传播等级，枚举顺序表示覆盖优先级。 */
enum ChunkMapLoadLevel {
    WEAK,
    BLOCK_TICKING,
    STRONG
}
