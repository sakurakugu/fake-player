package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;

/** 区块加载应用服务门面，统一负责校验、预算、票据事务和持久化。 */
public final class ChunkLoaderManager {
    public static final int ABSOLUTE_MAX_RADIUS = 32;
    private static final ChunkLoadRepository REPOSITORY = new ChunkLoadRepository();
    private static final ChunkTicketService TICKETS = new NeoForgeChunkTicketService();

    private ChunkLoaderManager() {
    }

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        NeoForgeChunkTicketService.register(event);
    }

    public static ChunkLoaderSavedData data(MinecraftServer server) {
        return REPOSITORY.get(server);
    }

    public static void reconcile(MinecraftServer server) {
        for (ManualLoadRegion region : data(server).regions()) {
            if (!region.enabled()) {
                continue;
            }
            String invalid = validate(server, data(server), region);
            if (invalid != null) {
                data(server).putRegion(region.withEnabled(false));
                FakePlayerMod.LOGGER.warn("区块加载区域 {} 已禁用：{}", region.name(), invalid);
                continue;
            }
            ServerLevel level = level(server, region);
            if (level != null) {
                applyAll(level, claims(region), true);
            }
        }
        ChunkLoaderBackupStore.save(server, data(server));
    }

    /** 兼容现有命令：以命令位置为中心创建方形区域。 */
    public static Result add(MinecraftServer server, String name, ServerLevel level, BlockPos position,
                             int radius, ManualLoadMode mode) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            return Result.failure("名称只能包含 1-32 个字母、数字、下划线或连字符");
        }
        if (radius < 0 || radius > FakePlayerConfig.maxChunkLoadingRadius()) {
            return Result.failure("半径必须在 0-" + FakePlayerConfig.maxChunkLoadingRadius() + " 之间");
        }
        ManualLoadRegion region = new ManualLoadRegion(UUID.randomUUID(), name, level.dimension().identifier(),
            ChunkLoadPlanner.square(position.getX() >> 4, position.getZ() >> 4, radius), mode, true);
        return createRegion(server, region);
    }

    public static Result createRegion(MinecraftServer server, ManualLoadRegion region) {
        ChunkLoaderSavedData data = data(server);
        ChunkLoaderSavedData.State before = data.snapshot();
        String invalid = validate(server, data, region);
        if (invalid != null) {
            return Result.failure(invalid);
        }
        if (!data.addRegion(region)) {
            return Result.failure("同名加载区域已存在");
        }
        try {
            ServerLevel target = level(server, region);
            if (region.enabled() && target != null) applyAll(target, claims(region), true);
            ChunkLoaderBackupStore.save(server, data);
            return Result.success(region);
        } catch (RuntimeException exception) {
            ServerLevel target = level(server, region);
            if (target != null) rollback(target, claims(region), false);
            data.restore(before);
            return Result.failure(exception.getMessage());
        }
    }

    public static Result setEnabled(MinecraftServer server, String name, boolean enabled) {
        ManualLoadRegion region = data(server).region(name).orElse(null);
        if (region == null) return Result.failure("找不到加载区域");
        if (region.enabled() == enabled) return Result.success(region);
        ManualLoadRegion changed = region.withEnabled(enabled);
        String invalid = enabled ? validate(server, data(server), changed) : null;
        if (invalid != null) return Result.failure(invalid);
        ServerLevel level = level(server, region);
        if (level == null) return Result.failure("目标维度不存在");
        try {
            applyAll(level, claims(region), enabled);
            data(server).putRegion(changed);
            ChunkLoaderBackupStore.save(server, data(server));
            return Result.success(changed);
        } catch (RuntimeException exception) {
            rollback(level, claims(region), !enabled);
            return Result.failure(exception.getMessage());
        }
    }

    public static Result configure(MinecraftServer server, String name, int radius, ManualLoadMode mode) {
        ManualLoadRegion region = data(server).region(name).orElse(null);
        if (region == null) return Result.failure("找不到加载区域");
        if (radius < 0 || radius > FakePlayerConfig.maxChunkLoadingRadius()) return Result.failure("半径超出限制");
        int minX = region.chunks().stream().mapToInt(ChunkKey::x).min().orElse(0);
        int maxX = region.chunks().stream().mapToInt(ChunkKey::x).max().orElse(0);
        int minZ = region.chunks().stream().mapToInt(ChunkKey::z).min().orElse(0);
        int maxZ = region.chunks().stream().mapToInt(ChunkKey::z).max().orElse(0);
        int centerX = Math.toIntExact(Math.floorDiv((long) minX + maxX, 2L));
        int centerZ = Math.toIntExact(Math.floorDiv((long) minZ + maxZ, 2L));
        ManualLoadRegion changed = new ManualLoadRegion(region.id(), region.name(), region.dimension(),
            ChunkLoadPlanner.square(centerX, centerZ, radius),
            mode, region.enabled());
        return replace(server, region, changed);
    }

    public static Result replace(MinecraftServer server, ManualLoadRegion oldRegion, ManualLoadRegion changed) {
        String invalid = validate(server, data(server), changed);
        if (invalid != null) return Result.failure(invalid);
        ServerLevel level = level(server, oldRegion);
        if (level == null || !oldRegion.dimension().equals(changed.dimension())) return Result.failure("不支持跨维度修改区域");
        List<ChunkLoadClaim> oldClaims = oldRegion.enabled() ? claims(oldRegion) : List.of();
        List<ChunkLoadClaim> newClaims = changed.enabled() ? claims(changed) : List.of();
        ChunkLoadPlanner.ClaimDiff diff = ChunkLoadPlanner.diff(oldClaims, newClaims);
        try {
            applyAll(level, diff.removed(), false);
            applyAll(level, diff.added(), true);
            data(server).putRegion(changed);
            ChunkLoaderBackupStore.save(server, data(server));
            return Result.success(changed);
        } catch (RuntimeException exception) {
            rollback(level, diff.added(), false);
            rollback(level, diff.removed(), true);
            return Result.failure(exception.getMessage());
        }
    }

    public static Result remove(MinecraftServer server, String name) {
        ManualLoadRegion region = data(server).region(name).orElse(null);
        if (region == null) return Result.failure("找不到加载区域");
        ServerLevel level = level(server, region);
        try {
            if (region.enabled() && level != null) applyAll(level, claims(region), false);
            data(server).removeRegion(region.id());
            ChunkLoaderBackupStore.save(server, data(server));
            return Result.success(region);
        } catch (RuntimeException exception) {
            if (level != null) rollback(level, claims(region), true);
            return Result.failure(exception.getMessage());
        }
    }

    public static boolean backup(MinecraftServer server) { return ChunkLoaderBackupStore.save(server, data(server)); }

    public static Result restoreLatestBackup(MinecraftServer server) {
        ChunkLoaderSavedData restored = ChunkLoaderBackupStore.loadLatest(server).orElse(null);
        if (restored == null) return Result.failure("没有可用的备份");
        for (ManualLoadRegion region : data(server).regions()) {
            ServerLevel level = level(server, region);
            if (region.enabled() && level != null) rollback(level, claims(region), false);
        }
        data(server).replaceAll(restored);
        reconcile(server);
        return Result.success();
    }

    /** 批量编辑失败时恢复整包操作前的配置与票据。 */
    public static void restoreState(MinecraftServer server, ChunkLoaderSavedData.State state) {
        ChunkLoaderSavedData current = data(server);
        for (ManualLoadRegion region : current.regions()) {
            ServerLevel level = level(server, region);
            if (region.enabled() && level != null) rollback(level, claims(region), false);
        }
        current.restore(state);
        for (ManualLoadRegion region : state.regions()) {
            ServerLevel level = level(server, region);
            if (region.enabled() && level != null && TICKETS.supports(region.mode().strength())) {
                rollback(level, claims(region), true);
            }
        }
        FakePlayerSimulationService.reconcile(server);
        ChunkLoaderBackupStore.save(server, current);
    }

    private static String validate(MinecraftServer server, ChunkLoaderSavedData current, ManualLoadRegion replacement) {
        if (replacement.chunks().isEmpty() || replacement.chunks().size() > ChunkLoaderSavedData.MAX_REGION_CHUNKS) return "区域区块数量非法";
        if (!replacement.name().matches("[A-Za-z0-9_-]{1,32}")) return "区域名称非法";
        if (level(server, replacement) == null) return "目标维度不存在";
        if (replacement.enabled() && !TICKETS.supports(replacement.mode().strength())) return "当前 NeoForge 版本不支持弱加载票据";
        if (replacement.mode() == ManualLoadMode.FULL && current.regions().stream()
            .anyMatch(region -> !region.id().equals(replacement.id()) && region.mode() == ManualLoadMode.FULL
                && NeoForgeChunkTicketService.sameBlockOwner(region.id(), replacement.id()))) {
            return "完整加载区域的票据所有者发生碰撞";
        }
        Collection<ManualLoadRegion> candidates = new ArrayList<>(current.regions());
        candidates.removeIf(region -> region.id().equals(replacement.id()));
        candidates.add(replacement);
        var usage = ChunkLoadPlanner.budget(candidates, current.policies());
        if (usage.manualTotal() > FakePlayerConfig.maxForcedChunks()) return "手动加载总预算超限";
        if (usage.ticking() + usage.full() > FakePlayerConfig.maxTickingChunks()) return "模拟区块预算超限";
        return null;
    }

    private static List<ChunkLoadClaim> claims(ManualLoadRegion region) {
        return ChunkLoadPlanner.manualClaims(List.of(region));
    }

    private static ServerLevel level(MinecraftServer server, ManualLoadRegion region) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, region.dimension()));
    }

    private static void applyAll(ServerLevel level, Collection<ChunkLoadClaim> claims, boolean add) {
        List<ChunkLoadClaim> completed = new ArrayList<>();
        try {
            for (ChunkLoadClaim claim : claims) {
                if (add) TICKETS.add(level, claim); else TICKETS.remove(level, claim);
                completed.add(claim);
            }
        } catch (RuntimeException exception) {
            rollback(level, completed, !add);
            throw exception;
        }
    }

    private static void rollback(ServerLevel level, Collection<ChunkLoadClaim> claims, boolean add) {
        for (ChunkLoadClaim claim : claims) {
            try { if (add) TICKETS.add(level, claim); else TICKETS.remove(level, claim); }
            catch (RuntimeException exception) { FakePlayerMod.LOGGER.error("回滚区块票据失败", exception); }
        }
    }

    public record Result(boolean successful, Optional<ManualLoadRegion> region, String reason) {
        public static Result success(ManualLoadRegion region) { return new Result(true, Optional.of(region), ""); }
        public static Result success() { return new Result(true, Optional.empty(), ""); }
        public static Result failure(String reason) { return new Result(false, Optional.empty(), reason == null ? "未知错误" : reason); }
    }
}
