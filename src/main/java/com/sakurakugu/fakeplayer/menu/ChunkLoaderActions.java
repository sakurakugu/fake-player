package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.network.ChunkLoaderActionPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 在服务端执行区块加载点界面操作并返回最新快照。 */
public final class ChunkLoaderActions {
    private ChunkLoaderActions() {
    }

    public static void handle(ServerPlayer viewer, ChunkLoaderActionPayload payload) {
        ChunkLoaderManager.Result result = switch (payload.action()) {
            case ADD -> add(viewer, payload);
            case CONFIGURE -> configure(viewer, payload);
            case ENABLE -> ChunkLoaderManager.setEnabled(server(viewer), payload.name(), true);
            case DISABLE -> ChunkLoaderManager.setEnabled(server(viewer), payload.name(), false);
            case REMOVE -> ChunkLoaderManager.remove(server(viewer), payload.name());
            case BACKUP -> ChunkLoaderManager.backup(server(viewer))
                ? ChunkLoaderManager.Result.success()
                : ChunkLoaderManager.Result.failure("创建备份失败，请查看服务端日志");
            case RESTORE -> ChunkLoaderManager.restoreLatestBackup(server(viewer));
        };
        if (!result.successful()) {
            viewer.sendSystemMessage(Component.translatable(
                "commands.fakeplayer.chunkloader.failed", result.reason()).withColor(0xFF5555));
        } else {
            viewer.sendSystemMessage(Component.translatable(
                "gui.fakeplayer.chunkloader.action_success." + payload.action().name().toLowerCase(java.util.Locale.ROOT)));
        }
        FakePlayerMenuOpener.openChunkLoaders(viewer);
    }

    private static ChunkLoaderManager.Result add(ServerPlayer viewer, ChunkLoaderActionPayload payload) {
        ChunkLoaderManager.Result validation = validateRadius(payload.radius());
        return validation == null
            ? ChunkLoaderManager.add(server(viewer), payload.name(), viewer.level(), viewer.blockPosition(),
                payload.radius(), payload.ticking() ? ManualLoadMode.TICKING : ManualLoadMode.LOADED)
            : validation;
    }

    private static ChunkLoaderManager.Result configure(ServerPlayer viewer, ChunkLoaderActionPayload payload) {
        ChunkLoaderManager.Result validation = validateRadius(payload.radius());
        return validation == null
            ? ChunkLoaderManager.configure(server(viewer), payload.name(), payload.radius(),
                payload.ticking() ? ManualLoadMode.TICKING : ManualLoadMode.LOADED)
            : validation;
    }

    private static ChunkLoaderManager.Result validateRadius(int radius) {
        return radius >= 0 && radius <= FakePlayerConfig.maxChunkLoadingRadius()
            ? null
            : ChunkLoaderManager.Result.failure("半径必须在 0-" + FakePlayerConfig.maxChunkLoadingRadius() + " 之间");
    }

    private static net.minecraft.server.MinecraftServer server(ServerPlayer viewer) {
        return viewer.level().getServer();
    }
}
