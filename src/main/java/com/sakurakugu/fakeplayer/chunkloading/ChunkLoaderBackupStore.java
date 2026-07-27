package com.sakurakugu.fakeplayer.chunkloading;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** 为区块加载点维护独立于 SavedData 的 JSON 备份。 */
public final class ChunkLoaderBackupStore {
    private static final int MAX_BACKUPS = 5;
    private static final String PREFIX = "chunk_loaders_";
    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss_SSS").withZone(ZoneOffset.UTC);

    private ChunkLoaderBackupStore() {
    }

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("fakeplayer").resolve("backups");
    }

    public static Path primaryDataPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FakePlayerMod.MOD_ID)
            .resolve("chunk_loaders.dat");
    }

    public static boolean save(MinecraftServer server, ChunkLoaderSavedData data) {
        try {
            save(directory(server), data, Instant.now());
            return true;
        } catch (IOException | RuntimeException exception) {
            FakePlayerMod.LOGGER.error("备份区块加载点失败", exception);
            return false;
        }
    }

    static Path save(Path directory, ChunkLoaderSavedData data, Instant timestamp) throws IOException {
        Files.createDirectories(directory);
        String json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data)
            .getOrThrow().toString();
        Path target = directory.resolve(PREFIX + FILE_TIME.format(timestamp) + ".json");
        Path temporary = directory.resolve(target.getFileName() + ".tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        cleanup(directory);
        return target;
    }

    public static Optional<ChunkLoaderSavedData> loadLatest(MinecraftServer server) {
        return loadLatest(directory(server));
    }

    static Optional<ChunkLoaderSavedData> loadLatest(Path directory) {
        for (Path backup : backups(directory)) {
            try {
                String json = Files.readString(backup, StandardCharsets.UTF_8);
                return Optional.of(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(json)).getOrThrow());
            } catch (IOException | RuntimeException exception) {
                FakePlayerMod.LOGGER.warn("忽略损坏的区块加载点备份 {}", backup.getFileName(), exception);
            }
        }
        return Optional.empty();
    }

    private static void cleanup(Path directory) throws IOException {
        List<Path> backups = backups(directory);
        for (int index = MAX_BACKUPS; index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index));
        }
    }

    private static List<Path> backups(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith(PREFIX) && name.endsWith(".json");
                })
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .toList();
        } catch (IOException exception) {
            FakePlayerMod.LOGGER.warn("读取区块加载点备份目录失败：{}", directory, exception);
            return List.of();
        }
    }
}
