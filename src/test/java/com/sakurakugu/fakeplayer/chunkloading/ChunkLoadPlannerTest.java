package com.sakurakugu.fakeplayer.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ChunkLoadPlannerTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void preservesNonRectangularChunksAndOwner() {
        ManualLoadRegion region = ChunkLoaderSavedDataTest.region("shape", ManualLoadMode.TICKING);
        List<ChunkLoadClaim> claims = ChunkLoadPlanner.manualClaims(List.of(region));

        assertEquals(region.chunks().size(), claims.size());
        assertTrue(claims.stream().allMatch(claim -> claim.owner().id().equals(region.id())));
        assertEquals(region.chunks(), claims.stream().map(ChunkLoadClaim::chunk).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void strongestManualModeWinsAndFallsBackAfterRemoval() {
        long chunk = ChunkKey.pack(4, 5);
        ManualLoadRegion loaded = region("loaded", chunk, ManualLoadMode.LOADED);
        ManualLoadRegion ticking = region("ticking", chunk, ManualLoadMode.TICKING);
        ManualLoadRegion full = region("full", chunk, ManualLoadMode.FULL);
        var key = new ChunkLoadPlanner.DimensionChunk(ResourceKey.create(Registries.DIMENSION, OVERWORLD), chunk);

        assertEquals(LoadStrength.FULL, ChunkLoadPlanner.effectiveManualStrength(
            ChunkLoadPlanner.manualClaims(List.of(loaded, ticking, full))).get(key));
        assertEquals(LoadStrength.TICKING, ChunkLoadPlanner.effectiveManualStrength(
            ChunkLoadPlanner.manualClaims(List.of(loaded, ticking))).get(key));
    }

    @Test
    void diffOnlyContainsChangedClaims() {
        ManualLoadRegion oldRegion = region("owner", ChunkKey.pack(0, 0), ManualLoadMode.TICKING);
        ManualLoadRegion newRegion = oldRegion.withChunks(Set.of(ChunkKey.pack(0, 0), ChunkKey.pack(1, 0)));
        var diff = ChunkLoadPlanner.diff(ChunkLoadPlanner.manualClaims(List.of(oldRegion)),
            ChunkLoadPlanner.manualClaims(List.of(newRegion)));

        assertEquals(1, diff.added().size());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void playerClaimsStayOutOfManualAggregation() {
        long chunk = ChunkKey.pack(0, 0);
        var dimension = ResourceKey.create(Registries.DIMENSION, OVERWORLD);
        ChunkLoadClaim player = new ChunkLoadClaim(LoadOwner.fakePlayer(UUID.randomUUID()), dimension, chunk,
            LoadStrength.TICKING);
        assertFalse(ChunkLoadPlanner.effectiveManualStrength(List.of(player))
            .containsKey(new ChunkLoadPlanner.DimensionChunk(dimension, chunk)));
    }

    @Test
    void computesSeparateBudgetsAndRejectsOverflow() {
        var loaded = region("loaded", ChunkKey.pack(0, 0), ManualLoadMode.LOADED);
        var full = region("full", ChunkKey.pack(1, 0), ManualLoadMode.FULL);
        var usage = ChunkLoadPlanner.budget(List.of(loaded, full),
            List.of(new FakePlayerLoadPolicy(UUID.randomUUID(), true, 2)));
        assertEquals(1, usage.loaded());
        assertEquals(1, usage.full());
        assertEquals(25, usage.player());
        assertThrows(ArithmeticException.class, () -> ChunkLoadPlanner.square(0, 0, Integer.MAX_VALUE));
    }

    private static ManualLoadRegion region(String name, long chunk, ManualLoadMode mode) {
        return new ManualLoadRegion(UUID.nameUUIDFromBytes(name.getBytes()), name, OVERWORLD, Set.of(chunk), mode, true);
    }
}
