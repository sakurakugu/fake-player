package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

/** 将所有手动区域映射为不强制自然刷怪的完整模拟票据。 */
public final class NeoForgeChunkTicketService implements ChunkTicketService {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_load_regions");
    private static final TicketController CONTROLLER = new TicketController(ID, NeoForgeChunkTicketService::validate);

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    @Override
    public void add(ServerLevel level, ChunkLoadClaim claim) {
        set(level, claim, true);
    }

    @Override
    public void remove(ServerLevel level, ChunkLoadClaim claim) {
        set(level, claim, false);
    }

    private static void set(ServerLevel level, ChunkLoadClaim claim, boolean add) {
        int x = ChunkPos.getX(claim.chunk());
        int z = ChunkPos.getZ(claim.chunk());
        CONTROLLER.forceChunk(level, claim.owner().id(), x, z, add, false);
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        ChunkLoaderSavedData data = ChunkLoaderManager.data(level.getServer());
        Map<UUID, ManualLoadRegion> regions = new HashMap<>();
        data.regions().forEach(region -> regions.put(region.id(), region));
        for (Map.Entry<UUID, TicketSet> entry : helper.getEntityTickets().entrySet()) {
            ManualLoadRegion region = regions.get(entry.getKey());
            if (region == null || !valid(level, region)) {
                helper.removeAllTickets(entry.getKey());
                continue;
            }
            removeUnexpected(helper, entry.getKey(), entry.getValue(), region);
        }
        for (var owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
    }

    private static boolean valid(ServerLevel level, ManualLoadRegion region) {
        return region.enabled() && region.dimension().equals(level.dimension().identifier());
    }

    private static void removeUnexpected(TicketHelper helper, UUID owner, TicketSet tickets,
                                         ManualLoadRegion region) {
        for (long chunk : tickets.normal()) {
            if (!region.chunks().contains(chunk)) {
                helper.removeTicket(owner, chunk, false);
            }
        }
        for (long chunk : tickets.naturalSpawning()) {
            helper.removeTicket(owner, chunk, true);
        }
    }
}
