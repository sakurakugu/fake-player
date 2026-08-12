package com.sakurakugu.fakeplayer.chunkloading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
                claims.add(new ChunkLoadClaim(LoadOwner.manualRegion(region.id()), dimension, chunk,
                    region.mode().strength()));
            }
        }
        return List.copyOf(claims);
    }

    public static Map<DimensionChunk, LoadStrength> effectiveManualStrength(Collection<ChunkLoadClaim> claims) {
        Map<DimensionChunk, LoadStrength> result = new HashMap<>();
        for (ChunkLoadClaim claim : claims) {
            if (claim.owner().type() != LoadOwner.Type.MANUAL_REGION) {
                continue;
            }
            DimensionChunk key = new DimensionChunk(claim.dimension(), claim.chunk());
            result.merge(key, claim.strength(), LoadStrength::strongest);
        }
        return Map.copyOf(result);
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
        long loaded = 0;
        long ticking = 0;
        long full = 0;
        long player = 0;
        for (ManualLoadRegion region : regions) {
            if (!region.enabled()) {
                continue;
            }
            switch (region.mode()) {
                case LOADED -> loaded = Math.addExact(loaded, region.chunks().size());
                case TICKING -> ticking = Math.addExact(ticking, region.chunks().size());
                case FULL -> full = Math.addExact(full, region.chunks().size());
            }
        }
        for (FakePlayerLoadPolicy policy : policies) {
            if (policy.enabled()) {
                long diameter = Math.addExact(Math.multiplyExact((long) policy.simulationDistance(), 2L), 1L);
                player = Math.addExact(player, Math.multiplyExact(diameter, diameter));
            }
        }
        return new BudgetUsage(loaded, ticking, full, player);
    }

    public record DimensionChunk(ResourceKey<Level> dimension, long chunk) {
    }

    public record ClaimDiff(Set<ChunkLoadClaim> added, Set<ChunkLoadClaim> removed) {
    }

    public record BudgetUsage(long loaded, long ticking, long full, long player) {
        public long manualTotal() {
            return Math.addExact(Math.addExact(loaded, ticking), full);
        }
    }
}
