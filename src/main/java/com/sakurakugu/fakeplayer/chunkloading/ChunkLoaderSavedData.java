package com.sakurakugu.fakeplayer.chunkloading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** 保存手动区域与假人策略；活动票据始终由这些配置重新派生。 */
public final class ChunkLoaderSavedData extends SavedData {
    public static final int MAX_REGIONS = 1024;
    public static final int MAX_REGION_CHUNKS = 16384;
    public static final int MAX_POLICIES = 1024;
    public static final int MAX_SIMULATION_DISTANCE = 32;

    private static final Codec<Set<Long>> CHUNKS_CODEC = Codec.LONG.listOf(1, MAX_REGION_CHUNKS)
        .flatXmap(ChunkLoaderSavedData::uniqueChunks, chunks -> DataResult.success(List.copyOf(chunks)));
    public static final Codec<ManualLoadRegion> REGION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(ManualLoadRegion::id),
        Codec.STRING.validate(ChunkLoaderSavedData::validateName).fieldOf("name").forGetter(ManualLoadRegion::name),
        Identifier.CODEC.fieldOf("dimension").forGetter(ManualLoadRegion::dimension),
        CHUNKS_CODEC.fieldOf("chunks").forGetter(ManualLoadRegion::chunks),
        Codec.STRING.comapFlatMap(ChunkLoaderSavedData::decodeMode, ManualLoadMode::name)
            .fieldOf("mode").forGetter(ManualLoadRegion::mode),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ManualLoadRegion::enabled)
    ).apply(instance, ManualLoadRegion::new));
    public static final Codec<FakePlayerLoadPolicy> POLICY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("fake_player_id").forGetter(FakePlayerLoadPolicy::fakePlayerId),
        Codec.BOOL.optionalFieldOf("enabled", false).forGetter(FakePlayerLoadPolicy::enabled),
        Codec.intRange(0, MAX_SIMULATION_DISTANCE).fieldOf("simulation_distance")
            .forGetter(FakePlayerLoadPolicy::simulationDistance)
    ).apply(instance, FakePlayerLoadPolicy::new));

    private static final Codec<ChunkLoaderSavedData> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.optionalFieldOf("revision", 0L).forGetter(ChunkLoaderSavedData::revision),
        REGION_CODEC.listOf(0, MAX_REGIONS).optionalFieldOf("manual_regions", List.of())
            .forGetter(data -> List.copyOf(data.regions.values())),
        POLICY_CODEC.listOf(0, MAX_POLICIES).optionalFieldOf("fake_player_policies", List.of())
            .forGetter(data -> List.copyOf(data.policies.values()))
    ).apply(instance, ChunkLoaderSavedData::new));
    public static final Codec<ChunkLoaderSavedData> CODEC = RAW_CODEC.flatXmap(
        ChunkLoaderSavedData::validateData, DataResult::success);

    public static final SavedDataType<ChunkLoaderSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_loaders"),
        ChunkLoaderSavedData::new,
        CODEC,
        null
    );

    private long revision;
    private boolean duplicateIds;
    private final Map<UUID, ManualLoadRegion> regions = new LinkedHashMap<>();
    private final Map<UUID, FakePlayerLoadPolicy> policies = new LinkedHashMap<>();

    public ChunkLoaderSavedData() {
    }

    private ChunkLoaderSavedData(long revision, List<ManualLoadRegion> regions,
                                 List<FakePlayerLoadPolicy> policies) {
        this.revision = revision;
        duplicateIds = regions.stream().map(ManualLoadRegion::id).distinct().count() != regions.size()
            || policies.stream().map(FakePlayerLoadPolicy::fakePlayerId).distinct().count() != policies.size();
        regions.forEach(region -> this.regions.put(region.id(), region));
        policies.forEach(policy -> this.policies.put(policy.fakePlayerId(), policy));
    }

    public long revision() {
        return revision;
    }

    public Collection<ManualLoadRegion> regions() {
        return List.copyOf(regions.values());
    }

    public Collection<FakePlayerLoadPolicy> policies() {
        return List.copyOf(policies.values());
    }

    public Optional<ManualLoadRegion> region(UUID id) {
        return Optional.ofNullable(regions.get(id));
    }

    public Optional<ManualLoadRegion> region(String name) {
        return regions.values().stream().filter(region -> region.name().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<FakePlayerLoadPolicy> policy(UUID fakePlayerId) {
        return Optional.ofNullable(policies.get(fakePlayerId));
    }

    public boolean addRegion(ManualLoadRegion region) {
        if (regions.containsKey(region.id()) || region(region.name()).isPresent()) {
            return false;
        }
        regions.put(region.id(), region);
        changed();
        return true;
    }

    public void putRegion(ManualLoadRegion region) {
        regions.put(region.id(), region);
        changed();
    }

    public Optional<ManualLoadRegion> removeRegion(UUID id) {
        ManualLoadRegion removed = regions.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    public void putPolicy(FakePlayerLoadPolicy policy) {
        policies.put(policy.fakePlayerId(), policy);
        changed();
    }

    public Optional<FakePlayerLoadPolicy> removePolicy(UUID fakePlayerId) {
        FakePlayerLoadPolicy removed = policies.remove(fakePlayerId);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    public void replaceAll(ChunkLoaderSavedData replacement) {
        regions.clear();
        policies.clear();
        replacement.regions().forEach(region -> regions.put(region.id(), region));
        replacement.policies().forEach(policy -> policies.put(policy.fakePlayerId(), policy));
        changed();
    }

    public State snapshot() {
        return new State(revision, List.copyOf(regions.values()), List.copyOf(policies.values()));
    }

    /** 仅供应用服务事务回滚，恢复后仍标记存档需要写入。 */
    public void restore(State state) {
        regions.clear();
        policies.clear();
        state.regions().forEach(region -> regions.put(region.id(), region));
        state.policies().forEach(policy -> policies.put(policy.fakePlayerId(), policy));
        revision = state.revision();
        setDirty();
    }

    private void changed() {
        revision = Math.incrementExact(revision);
        setDirty();
    }

    private static DataResult<String> validateName(String name) {
        return name.matches("[A-Za-z0-9_-]{1,32}")
            ? DataResult.success(name)
            : DataResult.error(() -> "非法区域名称");
    }

    private static DataResult<ManualLoadMode> decodeMode(String value) {
        try {
            return DataResult.success(ManualLoadMode.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "未知手动加载模式: " + value);
        }
    }

    private static DataResult<Set<Long>> uniqueChunks(List<Long> chunks) {
        Set<Long> result = new HashSet<>(chunks);
        return result.size() == chunks.size()
            ? DataResult.success(Set.copyOf(result))
            : DataResult.error(() -> "区域区块列表包含重复值");
    }

    private static DataResult<ChunkLoaderSavedData> validateData(ChunkLoaderSavedData data) {
        if (data.revision < 0) {
            return DataResult.error(() -> "revision 不能为负数");
        }
        if (data.duplicateIds) {
            return DataResult.error(() -> "配置包含重复 UUID");
        }
        Set<String> names = new HashSet<>();
        for (ManualLoadRegion region : data.regions()) {
            if (!names.add(region.name().toLowerCase(java.util.Locale.ROOT))) {
                return DataResult.error(() -> "区域名称重复: " + region.name());
            }
        }
        return DataResult.success(data);
    }

    public record State(long revision, List<ManualLoadRegion> regions, List<FakePlayerLoadPolicy> policies) {
        public State {
            regions = List.copyOf(regions);
            policies = List.copyOf(policies);
        }
    }
}
