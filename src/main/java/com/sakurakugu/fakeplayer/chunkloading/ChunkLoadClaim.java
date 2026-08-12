package com.sakurakugu.fakeplayer.chunkloading;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 从持久化配置派生的单区块加载声明。 */
public record ChunkLoadClaim(
    LoadOwner owner,
    ResourceKey<Level> dimension,
    long chunk,
    LoadStrength strength
) {
}
