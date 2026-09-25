package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.network.ApplyChunkLoadEditsPayload;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 保存权威快照和未提交草稿，前端只处理坐标、绘制和输入。 */
public final class ChunkLoadMapController {
    private ChunkMapSnapshotPayload snapshot;
    private ChunkMapEditMode mode = ChunkMapEditMode.BROWSE;
    private final Set<Long> painted = new HashSet<>();
    private final Set<Long> erased = new HashSet<>();
    private final Deque<DraftState> undo = new ArrayDeque<>();
    /** 不可变视图，随草稿一起更新；绘制端每帧都要读，不能每次现拷。 */
    private Set<Long> paintedView = Set.of();
    private Set<Long> erasedView = Set.of();
    private int draftVersion;
    private boolean awaitingApply;

    public ChunkLoadMapController(ChunkMapSnapshotPayload snapshot) { this.snapshot = snapshot; }

    public ChunkMapSnapshotPayload snapshot() { return snapshot; }
    public ChunkMapEditMode mode() { return mode; }
    public void setMode(ChunkMapEditMode value) { mode = value; }
    public Set<Long> painted() { return paintedView; }
    public Set<Long> erased() { return erasedView; }
    /** 草稿版本号，只在草稿真的变化时自增，供绘制端做缓存失效。 */
    public int draftVersion() { return draftVersion; }
    public boolean dirty() { return !paintedView.isEmpty() || !erasedView.isEmpty(); }

    public void accept(ChunkMapSnapshotPayload value) {
        boolean acknowledged = awaitingApply && value.revision() != snapshot.revision();
        snapshot = value;
        if (acknowledged) clearDraft();
        awaitingApply = false;
    }

    public void edit(int chunkX, int chunkZ) {
        if (mode == ChunkMapEditMode.BROWSE) return;
        long chunk = ChunkKey.pack(chunkX, chunkZ);
        // 视图本身就是上一版快照，省掉一次 Set 拷贝
        DraftState before = new DraftState(paintedView, erasedView);
        boolean changed;
        if (mode == ChunkMapEditMode.ERASE) {
            changed = painted.remove(chunk) | erased.add(chunk);
        } else {
            changed = painted.add(chunk) | erased.remove(chunk);
        }
        if (changed) {
            markDraftChanged();
            undo.push(before);
        }
    }

    public void undo() {
        if (undo.isEmpty()) return;
        DraftState state = undo.pop();
        painted.clear(); painted.addAll(state.painted());
        erased.clear(); erased.addAll(state.erased());
        markDraftChanged();
    }

    public void apply() {
        if (!dirty()) return;
        List<ApplyChunkLoadEditsPayload.Edit> edits = new ArrayList<>();
        Set<String> occupiedNames = new HashSet<>();
        snapshot.managementRegions().forEach(region -> occupiedNames.add(region.name().toLowerCase(Locale.ROOT)));
        if (!painted.isEmpty()) {
            String name = nextRegionName(occupiedNames);
            edits.add(new ApplyChunkLoadEditsPayload.Edit(
                ApplyChunkLoadEditsPayload.Action.CREATE_REGION, UUID.randomUUID(),
                name, true, 0, List.copyOf(painted)));
        }
        for (ChunkMapSnapshotPayload.AnchorView region : snapshot.regions()) {
            if (!region.dimension().equals(snapshot.dimension())) continue;
            List<Long> chunks = erased.stream().filter(region.chunks()::contains).toList();
            if (chunks.isEmpty()) continue;
            boolean delete = chunks.size() == region.chunks().size();
            edits.add(new ApplyChunkLoadEditsPayload.Edit(delete
                ? ApplyChunkLoadEditsPayload.Action.DELETE_REGION
                : ApplyChunkLoadEditsPayload.Action.REMOVE_CHUNKS,
                region.id(), "", true, 0, delete ? List.of() : chunks));
        }
        if (!edits.isEmpty()) {
            awaitingApply = true;
            ClientPacketDistributor.sendToServer(
                new ApplyChunkLoadEditsPayload(snapshot.revision(), snapshot.dimension(), edits));
        }
    }

    private void markDraftChanged() {
        draftVersion++;
        paintedView = Set.copyOf(painted);
        erasedView = Set.copyOf(erased);
    }

    private void clearDraft() {
        boolean changed = dirty();
        painted.clear();
        erased.clear();
        undo.clear();
        if (changed) markDraftChanged();
    }

    static String nextRegionName(Collection<String> existingNames) {
        Set<String> normalized = new HashSet<>();
        existingNames.forEach(name -> normalized.add(name.toLowerCase(Locale.ROOT)));
        for (long suffix = 1; suffix < Long.MAX_VALUE; suffix++) {
            String candidate = "region_" + suffix;
            if (!normalized.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("无法生成加载区域名称");
    }

    /**
     * 决定地图上每个区块画成什么颜色：草稿优先于权威数据，草稿里擦除的区块不再回落到权威等级。
     * 只返回有颜色的区块，绘制端遍历它而不是遍历屏幕上的区块。
     */
    static Map<Long, ChunkMapLoadLevel> overlayLevels(Set<Long> painted, Set<Long> erased,
                                                      Map<Long, ChunkMapLoadLevel> authoritative) {
        Map<Long, ChunkMapLoadLevel> result = new HashMap<>(painted.size() + authoritative.size());
        for (long chunk : painted) result.put(chunk, ChunkMapLoadLevel.STRONG);
        for (var entry : authoritative.entrySet()) {
            if (erased.contains(entry.getKey()) || painted.contains(entry.getKey())) continue;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** 等级 31 的强加载票据向外传播为一圈方块刻和一圈仅加载。 */
    static Map<Long, ChunkMapLoadLevel> propagatedLevels(
        Collection<ChunkMapSnapshotPayload.AnchorView> regions, String dimension) {
        Map<Long, ChunkMapLoadLevel> result = new HashMap<>();
        for (var region : regions) {
            if (!region.enabled() || !region.dimension().equals(dimension)) continue;
            for (long chunk : region.chunks()) {
                int centerX = ChunkKey.x(chunk);
                int centerZ = ChunkKey.z(chunk);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int distance = Math.max(Math.abs(dx), Math.abs(dz));
                        ChunkMapLoadLevel level = switch (distance) {
                            case 0 -> ChunkMapLoadLevel.STRONG;
                            case 1 -> ChunkMapLoadLevel.BLOCK_TICKING;
                            default -> ChunkMapLoadLevel.WEAK;
                        };
                        result.merge(ChunkKey.pack(centerX + dx, centerZ + dz), level,
                            (left, right) -> left.ordinal() >= right.ordinal() ? left : right);
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    private record DraftState(Set<Long> painted, Set<Long> erased) { }
}
