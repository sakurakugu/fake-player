package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.network.ApplyChunkLoadEditsPayload;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** 将命令和地图编辑汇入同一条服务端权威操作路径。 */
public final class ChunkLoadApplicationService {
    private ChunkLoadApplicationService() {
    }

    public static ApplyResult apply(ServerPlayer viewer, ApplyChunkLoadEditsPayload payload) {
        var server = viewer.level().getServer();
        ChunkLoaderSavedData data = ChunkLoaderManager.data(server);
        if (payload.expectedRevision() != data.revision()) return ApplyResult.conflict(data.revision());
        Identifier dimension;
        try { dimension = Identifier.parse(payload.dimension()); }
        catch (RuntimeException exception) { return ApplyResult.failure("维度标识非法", data.revision()); }
        if (!dimension.equals(viewer.level().dimension().identifier())) {
            return ApplyResult.failure("只能编辑当前所在维度", data.revision());
        }

        ChunkLoaderSavedData.State before = data.snapshot();
        for (ApplyChunkLoadEditsPayload.Edit edit : payload.edits()) {
            ChunkLoaderManager.Result result = applyOne(server, data, dimension, edit);
            if (!result.successful()) {
                ChunkLoaderManager.restoreState(server, before);
                return ApplyResult.failure(result.reason(), data.revision());
            }
        }
        return ApplyResult.success(data.revision());
    }

    private static ChunkLoaderManager.Result applyOne(net.minecraft.server.MinecraftServer server,
                                                       ChunkLoaderSavedData data, Identifier dimension,
                                                       ApplyChunkLoadEditsPayload.Edit edit) {
        return switch (edit.action()) {
            case CREATE_REGION -> create(server, data, dimension, edit);
            case ADD_CHUNKS -> changeChunks(server, data, edit.targetId(), edit.chunks(), true);
            case REMOVE_CHUNKS -> changeChunks(server, data, edit.targetId(), edit.chunks(), false);
            case SET_MODE -> update(server, data, edit.targetId(), region -> region.withMode(edit.mode()));
            case SET_ENABLED -> update(server, data, edit.targetId(), region -> region.withEnabled(edit.enabled()));
            case DELETE_REGION -> data.region(edit.targetId()).map(region -> ChunkLoaderManager.remove(server, region.name()))
                .orElseGet(() -> ChunkLoaderManager.Result.failure("区域不存在"));
            case SET_FAKE_POLICY -> FakePlayerSimulationService.setPolicy(server, edit.targetId(), edit.enabled(),
                edit.simulationDistance());
        };
    }

    private static ChunkLoaderManager.Result create(net.minecraft.server.MinecraftServer server,
                                                     ChunkLoaderSavedData data, Identifier dimension,
                                                     ApplyChunkLoadEditsPayload.Edit edit) {
        if (edit.chunks().isEmpty()) return ChunkLoaderManager.Result.failure("新区域不能为空");
        ManualLoadRegion region = new ManualLoadRegion(edit.targetId(), edit.name(), dimension,
            Set.copyOf(edit.chunks()), edit.mode(), edit.enabled());
        return ChunkLoaderManager.createRegion(server, region);
    }

    private static ChunkLoaderManager.Result changeChunks(net.minecraft.server.MinecraftServer server,
                                                           ChunkLoaderSavedData data, UUID id,
                                                           java.util.List<Long> chunks, boolean add) {
        return update(server, data, id, region -> {
            Set<Long> changed = new HashSet<>(region.chunks());
            if (add) changed.addAll(chunks); else changed.removeAll(chunks);
            return region.withChunks(changed);
        });
    }

    private static ChunkLoaderManager.Result update(net.minecraft.server.MinecraftServer server,
                                                     ChunkLoaderSavedData data, UUID id,
                                                     java.util.function.UnaryOperator<ManualLoadRegion> operation) {
        ManualLoadRegion region = data.region(id).orElse(null);
        return region == null ? ChunkLoaderManager.Result.failure("区域不存在")
            : ChunkLoaderManager.replace(server, region, operation.apply(region));
    }

    public record ApplyResult(boolean successful, boolean conflict, long revision, String reason) {
        public static ApplyResult success(long revision) { return new ApplyResult(true, false, revision, ""); }
        public static ApplyResult conflict(long revision) { return new ApplyResult(false, true, revision, "快照版本冲突"); }
        public static ApplyResult failure(String reason, long revision) { return new ApplyResult(false, false, revision, reason); }
    }
}
