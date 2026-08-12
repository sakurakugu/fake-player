package com.sakurakugu.fakeplayer.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkLoaderBackupStoreTest {
    @TempDir
    Path directory;

    @Test
    void retainsFiveNewestBackups() throws Exception {
        for (int index = 0; index < 7; index++) {
            ChunkLoaderBackupStore.save(directory, data("anchor" + index), Instant.ofEpochMilli(index));
        }

        try (var files = Files.list(directory)) {
            assertEquals(5, files.filter(path -> path.toString().endsWith(".json")).count());
        }
        assertEquals("anchor6", ChunkLoaderBackupStore.loadLatest(directory).orElseThrow()
            .regions().iterator().next().name());
    }

    @Test
    void skipsCorruptNewestBackup() throws Exception {
        ChunkLoaderBackupStore.save(directory, data("valid"), Instant.ofEpochMilli(1));
        Path newest = ChunkLoaderBackupStore.save(directory, data("newest"), Instant.ofEpochMilli(2));
        Files.writeString(newest, "not json", StandardCharsets.UTF_8);

        var restored = ChunkLoaderBackupStore.loadLatest(directory).orElseThrow();

        assertTrue(restored.region("valid").isPresent());
    }

    private static ChunkLoaderSavedData data(String name) {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        data.addRegion(new ManualLoadRegion(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),
            name, Identifier.withDefaultNamespace("overworld"), Set.of(ChunkKey.pack(0, 0)),
            ManualLoadMode.TICKING, true));
        return data;
    }
}
