package com.sakurakugu.fakeplayer.chunkloading;

import net.minecraft.server.level.ServerLevel;

/** 加载器无关的票据操作边界。 */
public interface ChunkTicketService {
    void add(ServerLevel level, ChunkLoadClaim claim);

    void remove(ServerLevel level, ChunkLoadClaim claim);

    boolean supports(LoadStrength strength);
}
