package com.sakurakugu.fakeplayer.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class ChunkLoadPlannerTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void preservesNonRectangularChunksAndOwner() {
        ManualLoadRegion region = ChunkLoaderSavedDataTest.region("shape");
        List<ChunkLoadClaim> claims = ChunkLoadPlanner.manualClaims(List.of(region));

        assertEquals(region.chunks().size(), claims.size());
        assertTrue(claims.stream().allMatch(claim -> claim.owner().id().equals(region.id())));
        assertEquals(region.chunks(), claims.stream().map(ChunkLoadClaim::chunk).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void disabledRegionsDoNotCreateClaims() {
        ManualLoadRegion disabled = region("disabled", ChunkKey.pack(4, 5)).withEnabled(false);
        assertTrue(ChunkLoadPlanner.manualClaims(List.of(disabled)).isEmpty());
    }

    @Test
    void diffOnlyContainsChangedClaims() {
        ManualLoadRegion oldRegion = region("owner", ChunkKey.pack(0, 0));
        ManualLoadRegion newRegion = oldRegion.withChunks(Set.of(ChunkKey.pack(0, 0), ChunkKey.pack(1, 0)));
        var diff = ChunkLoadPlanner.diff(ChunkLoadPlanner.manualClaims(List.of(oldRegion)),
            ChunkLoadPlanner.manualClaims(List.of(newRegion)));

        assertEquals(1, diff.added().size());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void computesSeparateBudgetsAndRejectsOverflow() {
        var first = region("first", ChunkKey.pack(0, 0));
        var second = region("second", ChunkKey.pack(1, 0));
        var usage = ChunkLoadPlanner.budget(List.of(first, second),
            List.of(new FakePlayerLoadPolicy(UUID.randomUUID(), true, 2)));
        assertEquals(2, usage.manualTotal());
        assertEquals(25, usage.player());
        assertThrows(ArithmeticException.class, () -> ChunkLoadPlanner.square(0, 0, Integer.MAX_VALUE));
    }

    private static ManualLoadRegion region(String name, long chunk) {
        return new ManualLoadRegion(UUID.nameUUIDFromBytes(name.getBytes()), name, OVERWORLD, Set.of(chunk), true);
    }
}
