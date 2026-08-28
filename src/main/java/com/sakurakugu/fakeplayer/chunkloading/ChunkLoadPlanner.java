package com.sakurakugu.fakeplayer.chunkloading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 只计算声明、差集和预算，不触碰世界或票据 API。 */
public final class ChunkLoadPlanner {
    private ChunkLoadPlanner() {
    }

    public static List<ChunkLoadClaim> manualClaims(Collection<ManualLoadRegion> regions) {
        List<ChunkLoadClaim> claims = new ArrayList<>();
        for (ManualLoadRegion region : regions) {
            if (!region.enabled()) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, region.dimension());
            for (long chunk : region.chunks()) {
                claims.add(new ChunkLoadClaim(LoadOwner.manualRegion(region.id()), dimension, chunk));
            }
        }
        return List.copyOf(claims);
    }

    public static ClaimDiff diff(Collection<ChunkLoadClaim> previous, Collection<ChunkLoadClaim> next) {
        Set<ChunkLoadClaim> oldClaims = Set.copyOf(previous);
        Set<ChunkLoadClaim> newClaims = Set.copyOf(next);
        Set<ChunkLoadClaim> removed = new HashSet<>(oldClaims);
        removed.removeAll(newClaims);
        Set<ChunkLoadClaim> added = new HashSet<>(newClaims);
        added.removeAll(oldClaims);
        return new ClaimDiff(Set.copyOf(added), Set.copyOf(removed));
    }

    public static Set<Long> square(int centerX, int centerZ, int radius) {
        long diameter = Math.addExact(Math.multiplyExact((long) radius, 2L), 1L);
        int capacity = Math.toIntExact(Math.multiplyExact(diameter, diameter));
        Set<Long> chunks = HashSet.newHashSet(capacity);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                chunks.add(ChunkKey.pack(x, z));
            }
        }
        return Set.copyOf(chunks);
    }

    public static BudgetUsage budget(Collection<ManualLoadRegion> regions,
                                     Collection<FakePlayerLoadPolicy> policies) {
        long manual = 0;
        long player = 0;
        for (ManualLoadRegion region : regions) {
            if (region.enabled()) manual = Math.addExact(manual, region.chunks().size());
        }
        for (FakePlayerLoadPolicy policy : policies) {
            if (policy.enabled()) {
                long diameter = Math.addExact(Math.multiplyExact((long) policy.simulationDistance(), 2L), 1L);
                player = Math.addExact(player, Math.multiplyExact(diameter, diameter));
            }
        }
        return new BudgetUsage(manual, player);
    }

    public record ClaimDiff(Set<ChunkLoadClaim> added, Set<ChunkLoadClaim> removed) {
    }

    public record BudgetUsage(long manualTotal, long player) {
        public long manualTotal() {
            return manualTotal;
        }
    }
}
