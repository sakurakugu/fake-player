package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.network.ApplyChunkLoadEditsPayload;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 保存权威快照和未提交草稿，前端只处理坐标、绘制和输入。 */
public final class ChunkLoadMapController {
    private ChunkMapSnapshotPayload snapshot;
    private ChunkMapEditMode mode = ChunkMapEditMode.BROWSE;
    private final Map<Long, ManualLoadMode> painted = new HashMap<>();
    private final Set<Long> erased = new HashSet<>();
    private final Deque<DraftState> undo = new ArrayDeque<>();
    private boolean awaitingApply;

    public ChunkLoadMapController(ChunkMapSnapshotPayload snapshot) { this.snapshot = snapshot; }

    public ChunkMapSnapshotPayload snapshot() { return snapshot; }
    public ChunkMapEditMode mode() { return mode; }
    public void setMode(ChunkMapEditMode value) { mode = value; }
    public Map<Long, ManualLoadMode> painted() { return Map.copyOf(painted); }
    public boolean dirty() { return !painted.isEmpty() || !erased.isEmpty(); }

    public void accept(ChunkMapSnapshotPayload value) {
        boolean acknowledged = awaitingApply && value.revision() != snapshot.revision();
        snapshot = value;
        if (acknowledged) clearDraft();
        awaitingApply = false;
    }

    public void edit(int chunkX, int chunkZ) {
        if (mode == ChunkMapEditMode.BROWSE) return;
        long chunk = ChunkKey.pack(chunkX, chunkZ);
        DraftState before = state();
        if (mode == ChunkMapEditMode.ERASE) {
            painted.remove(chunk);
            erased.add(chunk);
        } else {
            painted.put(chunk, mode.manualMode());
            erased.remove(chunk);
        }
        if (!before.equals(state())) undo.push(before);
    }

    public void undo() {
        if (undo.isEmpty()) return;
        DraftState state = undo.pop();
        painted.clear(); painted.putAll(state.painted());
        erased.clear(); erased.addAll(state.erased());
    }

    public void apply() {
        if (!dirty()) return;
        List<ApplyChunkLoadEditsPayload.Edit> edits = new ArrayList<>();
        for (ManualLoadMode loadMode : ManualLoadMode.values()) {
            List<Long> chunks = painted.entrySet().stream().filter(entry -> entry.getValue() == loadMode)
                .map(Map.Entry::getKey).toList();
            if (!chunks.isEmpty()) edits.add(new ApplyChunkLoadEditsPayload.Edit(
                ApplyChunkLoadEditsPayload.Action.CREATE_REGION, UUID.randomUUID(),
                "map_" + Long.toUnsignedString(System.nanoTime(), 36), loadMode, true, 0, chunks));
        }
        for (ChunkMapSnapshotPayload.AnchorView region : snapshot.regions()) {
            List<Long> chunks = erased.stream().filter(region.chunks()::contains).toList();
            if (chunks.isEmpty()) continue;
            boolean delete = chunks.size() == region.chunks().size();
            edits.add(new ApplyChunkLoadEditsPayload.Edit(delete
                ? ApplyChunkLoadEditsPayload.Action.DELETE_REGION
                : ApplyChunkLoadEditsPayload.Action.REMOVE_CHUNKS,
                region.id(), "", region.mode(), true, 0, delete ? List.of() : chunks));
        }
        if (!edits.isEmpty()) {
            awaitingApply = true;
            ClientPacketDistributor.sendToServer(
                new ApplyChunkLoadEditsPayload(snapshot.revision(), snapshot.dimension(), edits));
        }
    }

    private DraftState state() { return new DraftState(Map.copyOf(painted), Set.copyOf(erased)); }
    private void clearDraft() { painted.clear(); erased.clear(); undo.clear(); }
    private record DraftState(Map<Long, ManualLoadMode> painted, Set<Long> erased) { }
}
