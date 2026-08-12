package com.sakurakugu.fakeplayer.chunkloading;

import java.nio.file.Files;
import net.minecraft.server.MinecraftServer;

/** 隔离 SavedData 的获取与异常备份恢复。 */
public final class ChunkLoadRepository {
    public ChunkLoaderSavedData get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        ChunkLoaderSavedData loaded = storage.get(ChunkLoaderSavedData.TYPE);
        if (loaded != null) {
            return loaded;
        }
        if (Files.exists(ChunkLoaderBackupStore.primaryDataPath(server))) {
            ChunkLoaderSavedData restored = ChunkLoaderBackupStore.loadLatest(server).orElse(null);
            if (restored != null) {
                storage.set(ChunkLoaderSavedData.TYPE, restored);
                return restored;
            }
        }
        return storage.computeIfAbsent(ChunkLoaderSavedData.TYPE);
    }
}
