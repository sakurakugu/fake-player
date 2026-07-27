package com.sakurakugu.fakeplayer.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class ChunkLoaderSavedDataTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void namesAreCaseInsensitiveAndKeepOriginalSpelling() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        ChunkLoaderSavedData.Anchor anchor = anchor("Spawn", 2, true, false);

        assertTrue(data.add(anchor));
        assertFalse(data.add(anchor("sPaWn", 3, true, true)));
        assertEquals(anchor, data.anchor("SPAWN").orElseThrow());
        assertTrue(data.isDirty());
    }

    @Test
    void putReplacesAnchorAndRemoveReturnsIt() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        ChunkLoaderSavedData.Anchor original = anchor("Spawn", 2, true, false);
        ChunkLoaderSavedData.Anchor replacement = original.withSettings(5, true);

        data.put(original);
        data.put(replacement);

        assertEquals(replacement, data.remove("spawn").orElseThrow());
        assertTrue(data.anchor("Spawn").isEmpty());
        assertTrue(data.remove("missing").isEmpty());
    }

    @Test
    void anchorsReturnsAnImmutableSnapshot() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        data.add(anchor("one", 1, true, false));
        Collection<ChunkLoaderSavedData.Anchor> snapshot = data.anchors();

        data.add(anchor("two", 2, false, true));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void anchorCopiesChangeOnlyRequestedSettings() {
        ChunkLoaderSavedData.Anchor anchor = anchor("Spawn", 2, true, false);

        ChunkLoaderSavedData.Anchor disabled = anchor.withEnabled(false);
        ChunkLoaderSavedData.Anchor configured = anchor.withSettings(6, true);

        assertFalse(disabled.enabled());
        assertEquals(anchor.radius(), disabled.radius());
        assertEquals(6, configured.radius());
        assertTrue(configured.ticking());
        assertTrue(configured.enabled());
        assertNotSame(anchor, disabled);
    }

    @Test
    void codecRoundTripsAllAnchorFields() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.add(anchor("Spawn", 4, false, true));

        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        ChunkLoaderSavedData decoded = ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(original.anchors(), decoded.anchors());
    }

    private static ChunkLoaderSavedData.Anchor anchor(
        String name,
        int radius,
        boolean enabled,
        boolean ticking
    ) {
        return new ChunkLoaderSavedData.Anchor(
            UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            name,
            OVERWORLD,
            new BlockPos(32, 64, -16),
            radius,
            enabled,
            ticking
        );
    }
}
