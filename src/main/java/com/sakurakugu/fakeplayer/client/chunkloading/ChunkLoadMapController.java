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
    /** 当前维度里属于已启用手动区域的区块（强加载）；按区域列表的对象身份缓存。 */
    private List<ChunkMapSnapshotPayload.AnchorView> strongSource;
    private Set<Long> strongChunks = Set.of();
    /** 显示用等级表，同样按对象身份缓存，见 {@link #levels()}。 */
    private Set<Long> levelsSource;
    private int levelsDraftVersion = -1;
    private boolean levelsShowWeak;
    private Map<Long, ChunkMapLoadLevel> levels = Map.of();
    /** 是否画出强加载区块外围的弱加载范围。 */
    private boolean showWeakLoading = true;

    public ChunkLoadMapController(ChunkMapSnapshotPayload snapshot) { this.snapshot = snapshot; }

    public ChunkMapSnapshotPayload snapshot() { return snapshot; }
    public ChunkMapEditMode mode() { return mode; }
    public void setMode(ChunkMapEditMode value) { mode = value; }
    public Set<Long> painted() { return paintedView; }
    public Set<Long> erased() { return erasedView; }
    /** 草稿版本号，只在草稿真的变化时自增。 */
    public int draftVersion() { return draftVersion; }
    public boolean dirty() { return !paintedView.isEmpty() || !erasedView.isEmpty(); }

    public void accept(ChunkMapSnapshotPayload value) {
        boolean acknowledged = awaitingApply && value.revision() != snapshot.revision();
        // 区域列表是按维度过滤后发过来的，换维度就必须重算强加载集合
        if (!value.dimension().equals(snapshot.dimension())) strongSource = null;
        snapshot = value;
        if (acknowledged) clearDraft();
        awaitingApply = false;
    }

    /**
     * 改一格草稿。
     *
     * @param erase 这一笔是擦除而不是强加载；由鼠标按键决定，左键涂、右键擦
     */
    public void edit(int chunkX, int chunkZ, boolean erase) {
        if (mode == ChunkMapEditMode.BROWSE) return;
        long chunk = ChunkKey.pack(chunkX, chunkZ);
        // 视图本身就是上一版快照，省掉一次 Set 拷贝
        DraftState before = new DraftState(paintedView, erasedView);
        boolean changed;
        if (erase) {
            // 擦除只认手动强加载区块：弱加载是票据自动传播出来的，
            // 服务端没有对应的区域区块，点上去不该留痕
            if (strongChunks().contains(chunk)) {
                changed = erased.add(chunk) | painted.remove(chunk);
            } else {
                // 只是撤掉草稿里自己画的那一笔，不必变成一条"删除区块"的意图
                changed = painted.remove(chunk);
            }
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
        } else {
            // 目标区域在别处被删掉或改过了，草稿已经没有可以提交的内容
            clearDraft();
        }
    }

    /** 地图显示用的加载等级表；草稿或区域数据变了才重算，绘制端按返回值的对象身份做缓存失效。 */
    public Map<Long, ChunkMapLoadLevel> levels() {
        Set<Long> strong = strongChunks();
        if (levelsSource == strong && levelsDraftVersion == draftVersion
            && levelsShowWeak == showWeakLoading) {
            return levels;
        }
        levelsSource = strong;
        levelsDraftVersion = draftVersion;
        levelsShowWeak = showWeakLoading;
        levels = Map.copyOf(overlayLevels(painted, erased, strong, showWeakLoading));
        return levels;
    }

    public void setShowWeakLoading(boolean value) {
        showWeakLoading = value;
    }

    /** 已启用手动区域包含的区块。区域数据每 10 tick 同步一次但内容极少变，所以按列表对象身份判断。 */
    private Set<Long> strongChunks() {
        List<ChunkMapSnapshotPayload.AnchorView> regions = snapshot.regions();
        if (regions == strongSource) return strongChunks;
        strongSource = regions;
        Set<Long> values = new HashSet<>();
        for (ChunkMapSnapshotPayload.AnchorView region : regions) {
            if (region.enabled() && region.dimension().equals(snapshot.dimension())) values.addAll(region.chunks());
        }
        strongChunks = Set.copyOf(values);
        return strongChunks;
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
     * 决定地图上每个区块画成什么颜色：强加载区块来自手动区域和草稿画出的区块，
     * 草稿擦除的区块连它带出来的那一圈一起消失。只返回有颜色的区块，
     * 绘制端遍历它而不是遍历屏幕上的区块。
     *
     * @param showWeak 是否画出强加载区块外围的弱加载范围（等级 32 的那一圈）
     */
    static Map<Long, ChunkMapLoadLevel> overlayLevels(Set<Long> painted, Set<Long> erased,
                                                      Set<Long> strongChunks, boolean showWeak) {
        Set<Long> strong = new HashSet<>(strongChunks);
        strong.removeAll(erased);
        strong.addAll(painted);
        Map<Long, ChunkMapLoadLevel> result = new HashMap<>(showWeak ? strong.size() * 9 : strong.size());
        for (long chunk : strong) {
            result.merge(chunk, ChunkMapLoadLevel.STRONG, ChunkLoadMapController::stronger);
            if (!showWeak) continue;
            int centerX = ChunkKey.x(chunk);
            int centerZ = ChunkKey.z(chunk);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    result.merge(ChunkKey.pack(centerX + dx, centerZ + dz), ChunkMapLoadLevel.WEAK,
                        ChunkLoadMapController::stronger);
                }
            }
        }
        return result;
    }

    /** 同一个区块被多个强加载源覆盖时取更强的那个等级。 */
    private static ChunkMapLoadLevel stronger(ChunkMapLoadLevel left, ChunkMapLoadLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private record DraftState(Set<Long> painted, Set<Long> erased) { }
}
