package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

/** NeoForge 26.1 票据映射；不支持的能力绝不静默升级。 */
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

    @Override
    public boolean supports(LoadStrength strength) {
        return strength != LoadStrength.LOADED;
    }

    private static void set(ServerLevel level, ChunkLoadClaim claim, boolean add) {
        if (claim.strength() == LoadStrength.LOADED) {
            throw new UnsupportedOperationException("NeoForge 26.1 暂无经过验证的仅加载票据实现");
        }
        int x = ChunkPos.getX(claim.chunk());
        int z = ChunkPos.getZ(claim.chunk());
        if (claim.strength() == LoadStrength.TICKING) {
            // UUID 重载的 false 表示普通完整模拟票据，不强制自然刷怪。
            CONTROLLER.forceChunk(level, claim.owner().id(), x, z, add, false);
        } else {
            // BlockPos 重载的 true 表示同时允许无玩家自然刷怪。
            CONTROLLER.forceChunk(level, blockOwner(claim.owner().id()), x, z, add, true);
        }
    }

    private static BlockPos blockOwner(UUID id) {
        int packedY = (int) (id.getLeastSignificantBits() & 0xFFF);
        int y = packedY >= 0x800 ? packedY - 0x1000 : packedY;
        return new BlockPos((int) (id.getMostSignificantBits() >> 32), y, (int) id.getMostSignificantBits());
    }

    static boolean sameBlockOwner(UUID left, UUID right) {
        return blockOwner(left).equals(blockOwner(right));
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        ChunkLoaderSavedData data = ChunkLoaderManager.data(level.getServer());
        Map<UUID, ManualLoadRegion> regions = new HashMap<>();
        data.regions().forEach(region -> regions.put(region.id(), region));
        for (Map.Entry<UUID, TicketSet> entry : helper.getEntityTickets().entrySet()) {
            ManualLoadRegion region = regions.get(entry.getKey());
            if (region == null || !valid(level, region, ManualLoadMode.TICKING)) {
                helper.removeAllTickets(entry.getKey());
                continue;
            }
            removeUnexpected(helper, entry.getKey(), entry.getValue(), region);
        }
        for (Map.Entry<BlockPos, TicketSet> entry : helper.getBlockTickets().entrySet()) {
            ManualLoadRegion region = data.regions().stream()
                .filter(value -> blockOwner(value.id()).equals(entry.getKey())).findFirst().orElse(null);
            if (region == null || !valid(level, region, ManualLoadMode.FULL)) {
                helper.removeAllTickets(entry.getKey());
                continue;
            }
            for (long chunk : entry.getValue().normal()) {
                helper.removeTicket(entry.getKey(), chunk, false);
            }
            for (long chunk : entry.getValue().naturalSpawning()) {
                if (!region.chunks().contains(chunk)) {
                    helper.removeTicket(entry.getKey(), chunk, true);
                }
            }
        }
    }

    private static boolean valid(ServerLevel level, ManualLoadRegion region, ManualLoadMode mode) {
        return region.enabled() && region.mode() == mode
            && region.dimension().equals(level.dimension().identifier());
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
