package com.sakurakugu.fakeplayer.chunkloading;

import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** 管理员显式选择的一组同模式区块。 */
public record ManualLoadRegion(
    UUID id,
    String name,
    Identifier dimension,
    Set<Long> chunks,
    ManualLoadMode mode,
    boolean enabled
) {
    public ManualLoadRegion {
        chunks = Set.copyOf(chunks);
    }

    public ManualLoadRegion withEnabled(boolean value) {
        return new ManualLoadRegion(id, name, dimension, chunks, mode, value);
    }

    public ManualLoadRegion withChunks(Set<Long> value) {
        return new ManualLoadRegion(id, name, dimension, value, mode, enabled);
    }

    public ManualLoadRegion withMode(ManualLoadMode value) {
        return new ManualLoadRegion(id, name, dimension, chunks, value, enabled);
    }
}
