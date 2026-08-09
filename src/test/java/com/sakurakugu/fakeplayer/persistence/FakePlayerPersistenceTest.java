package com.sakurakugu.fakeplayer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FakePlayerPersistenceTest {
    @Test
    void movingPlayerProgressDataMovesStatsAndAdvancements(@TempDir Path directory) throws Exception {
        Path stats = Files.createDirectory(directory.resolve("stats"));
        Path advancements = Files.createDirectory(directory.resolve("advancements"));
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        Files.writeString(stats.resolve(oldUuid + ".json"), "stats");
        Files.writeString(advancements.resolve(oldUuid + ".json"), "advancements");

        FakePlayerPersistence.movePlayerProgressData(stats, advancements, oldUuid, newUuid);

        assertFalse(Files.exists(stats.resolve(oldUuid + ".json")));
        assertFalse(Files.exists(advancements.resolve(oldUuid + ".json")));
        assertEquals("stats", Files.readString(stats.resolve(newUuid + ".json")));
        assertEquals("advancements", Files.readString(advancements.resolve(newUuid + ".json")));
    }

    @Test
    void movingPlayerProgressDataAllowsMissingFiles(@TempDir Path directory) throws Exception {
        Path stats = Files.createDirectory(directory.resolve("stats"));
        Path advancements = Files.createDirectory(directory.resolve("advancements"));
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        Files.writeString(stats.resolve(oldUuid + ".json"), "stats");

        FakePlayerPersistence.movePlayerProgressData(stats, advancements, oldUuid, newUuid);

        assertEquals("stats", Files.readString(stats.resolve(newUuid + ".json")));
        assertFalse(Files.exists(advancements.resolve(newUuid + ".json")));
    }

    @Test
    void movingPlayerProgressDataDoesNotPartiallyMoveOnTargetConflict(@TempDir Path directory) throws Exception {
        Path stats = Files.createDirectory(directory.resolve("stats"));
        Path advancements = Files.createDirectory(directory.resolve("advancements"));
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        Path oldStats = Files.writeString(stats.resolve(oldUuid + ".json"), "old stats");
        Path oldAdvancements = Files.writeString(advancements.resolve(oldUuid + ".json"), "old advancements");
        Path newAdvancements = Files.writeString(advancements.resolve(newUuid + ".json"), "new advancements");

        assertThrows(IOException.class,
            () -> FakePlayerPersistence.movePlayerProgressData(stats, advancements, oldUuid, newUuid));

        assertEquals("old stats", Files.readString(oldStats));
        assertEquals("old advancements", Files.readString(oldAdvancements));
        assertEquals("new advancements", Files.readString(newAdvancements));
        assertFalse(Files.exists(stats.resolve(newUuid + ".json")));
    }

    @Test
    void deletingPlayerDataRemovesCurrentAndBackupFiles(@TempDir Path directory) throws Exception {
        UUID uuid = UUID.randomUUID();
        Path current = Files.createFile(directory.resolve(uuid + ".dat"));
        Path backup = Files.createFile(directory.resolve(uuid + ".dat_old"));
        Path unrelated = Files.createFile(directory.resolve(UUID.randomUUID() + ".dat"));

        FakePlayerPersistence.deletePlayerData(directory, uuid);

        assertFalse(Files.exists(current));
        assertFalse(Files.exists(backup));
        assertTrue(Files.exists(unrelated));
    }
}
