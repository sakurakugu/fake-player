package com.sakurakugu.fakeplayer.client.chunkloading;

/**
 * 地图展示的实际票据传播等级，枚举顺序表示覆盖优先级。
 *
 * <p>强加载票据的等级是 31，向外每远一格加一级：距离 1 是等级 32（方块刻但无实体刻，
 * 也就是通常说的弱加载），距离 2 是等级 33（仅加载，连方块刻都没有）。
 * 等级 33 什么都不做，画出来只是噪声，因此不做展示。
 */
enum ChunkMapLoadLevel {
    WEAK,
    STRONG
}
