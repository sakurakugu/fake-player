package com.sakurakugu.fakeplayer.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class ChunkLoaderSavedDataTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void regionsUseUuidAndCaseInsensitiveUniqueNames() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        ManualLoadRegion region = region("Spawn", ManualLoadMode.TICKING);

        assertTrue(data.addRegion(region));
        assertFalse(data.addRegion(region("sPaWn", ManualLoadMode.FULL)));
        assertEquals(region, data.region(region.id()).orElseThrow());
        assertEquals(region, data.region("SPAWN").orElseThrow());
        assertEquals(1, data.revision());
    }

    @Test
    void policiesAndRegionsRoundTrip() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        ManualLoadRegion region = region("Spawn", ManualLoadMode.FULL).withEnabled(false);
        original.addRegion(region);
        original.putPolicy(new FakePlayerLoadPolicy(UUID.randomUUID(), true, 7));

        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        ChunkLoaderSavedData decoded = ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(original.revision(), decoded.revision());
        assertEquals(original.regions(), decoded.regions());
        assertEquals(original.policies(), decoded.policies());
    }

    @Test
    void codecRejectsDuplicateChunksAndUnknownMode() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.addRegion(region("Spawn", ManualLoadMode.TICKING));
        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var region = json.getAsJsonObject().getAsJsonArray("manual_regions").get(0).getAsJsonObject();
        region.getAsJsonArray("chunks").add(region.getAsJsonArray("chunks").get(0));
        assertTrue(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());

        region.getAsJsonArray("chunks").remove(region.getAsJsonArray("chunks").size() - 1);
        region.addProperty("mode", "MAGIC");
        assertTrue(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void codecRejectsDuplicateRegionUuid() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.addRegion(region("Spawn", ManualLoadMode.TICKING));
        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        json.getAsJsonObject().getAsJsonArray("manual_regions")
            .add(json.getAsJsonObject().getAsJsonArray("manual_regions").get(0).deepCopy());

        assertTrue(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void snapshotsAreImmutableAndCanRestoreRevision() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        data.addRegion(region("one", ManualLoadMode.TICKING));
        ChunkLoaderSavedData.State state = data.snapshot();
        data.addRegion(region("two", ManualLoadMode.FULL));

        data.restore(state);

        assertEquals(1, data.revision());
        assertEquals(1, data.regions().size());
        assertThrows(UnsupportedOperationException.class, () -> state.regions().clear());
    }

    static ManualLoadRegion region(String name, ManualLoadMode mode) {
        return new ManualLoadRegion(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, OVERWORLD,
            Set.of(ChunkKey.pack(1, 2), ChunkKey.pack(2, 2), ChunkKey.pack(2, 3)), mode, true);
    }
}
