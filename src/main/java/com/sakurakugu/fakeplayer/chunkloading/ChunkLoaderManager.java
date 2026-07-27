package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderSavedData.Anchor;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

/** 管理持久化区块票，每个加载点以自己的 UUID 隔离票据所有权。 */
public final class ChunkLoaderManager {
    public static final int ABSOLUTE_MAX_RADIUS = 32;
    private static final Identifier CONTROLLER_ID =
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_loaders");
    private static final TicketController CONTROLLER = new TicketController(CONTROLLER_ID,
        ChunkLoaderManager::validateStoredTickets);

    private ChunkLoaderManager() {
    }

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static ChunkLoaderSavedData data(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        ChunkLoaderSavedData loaded = storage.get(ChunkLoaderSavedData.TYPE);
        if (loaded != null) {
            return loaded;
        }
        // 主 SavedData 存在却无法解析时，从独立 JSON 备份自动恢复，避免空数据覆盖唯一副本。
        if (Files.exists(ChunkLoaderBackupStore.primaryDataPath(server))) {
            ChunkLoaderSavedData restored = ChunkLoaderBackupStore.loadLatest(server).orElse(null);
            if (restored != null) {
                storage.set(ChunkLoaderSavedData.TYPE, restored);
                FakePlayerMod.LOGGER.warn("区块加载点主存档损坏，已从最新可读 JSON 备份自动恢复");
                return restored;
            }
        }
        return storage.computeIfAbsent(ChunkLoaderSavedData.TYPE);
    }

    /** 服务端启动后补齐可能因异常停服而没有写入的票据。 */
    public static void reconcile(MinecraftServer server) {
        for (Anchor anchor : data(server).anchors()) {
            if (!anchor.enabled()) {
                continue;
            }
            ServerLevel level = level(server, anchor);
            if (level == null) {
                FakePlayerMod.LOGGER.warn("无法恢复区块加载点 {}：维度 {} 不存在", anchor.name(), anchor.dimension());
                continue;
            }
            try {
                setTickets(level, anchor, true);
            } catch (RuntimeException exception) {
                FakePlayerMod.LOGGER.error("恢复区块加载点 {} 失败", anchor.name(), exception);
            }
        }
    }

    public static Result add(
        MinecraftServer server,
        String name,
        ServerLevel level,
        BlockPos position,
        int radius,
        boolean ticking
    ) {
        if (!validName(name)) {
            return Result.failure("名称只能包含 1-32 个字母、数字、下划线或连字符");
        }
        if (radius < 0 || radius > ABSOLUTE_MAX_RADIUS) {
            return Result.failure("半径必须在 0-" + ABSOLUTE_MAX_RADIUS + " 之间");
        }
        Anchor anchor = new Anchor(UUID.randomUUID(), name, level.dimension().identifier(),
            position.immutable(), radius, true, ticking);
        ChunkLoaderSavedData data = data(server);
        if (!data.add(anchor)) {
            return Result.failure("同名加载点已存在");
        }
        try {
            setTickets(level, anchor, true);
            ChunkLoaderBackupStore.save(server, data);
            return Result.success(anchor);
        } catch (RuntimeException exception) {
            rollbackTickets(level, anchor, false);
            data.remove(name);
            FakePlayerMod.LOGGER.error("创建区块加载点 {} 失败", name, exception);
            return Result.failure("区块票创建失败");
        }
    }

    public static Result setEnabled(MinecraftServer server, String name, boolean enabled) {
        ChunkLoaderSavedData data = data(server);
        Anchor anchor = data.anchor(name).orElse(null);
        if (anchor == null) {
            return Result.failure("找不到加载点");
        }
        if (anchor.enabled() == enabled) {
            return Result.success(anchor);
        }
        ServerLevel level = level(server, anchor);
        if (level == null) {
            return Result.failure("目标维度不存在：" + anchor.dimension());
        }
        try {
            setTickets(level, anchor, enabled);
        } catch (RuntimeException exception) {
            // 启停中途失败时回到操作前的票据状态。
            rollbackTickets(level, anchor, !enabled);
            FakePlayerMod.LOGGER.error("切换区块加载点 {} 状态失败", name, exception);
            return Result.failure("更新区块票失败");
        }
        Anchor changed = anchor.withEnabled(enabled);
        data.put(changed);
        ChunkLoaderBackupStore.save(server, data);
        return Result.success(changed);
    }

    public static Result configure(MinecraftServer server, String name, int radius, boolean ticking) {
        if (radius < 0 || radius > ABSOLUTE_MAX_RADIUS) {
            return Result.failure("半径必须在 0-" + ABSOLUTE_MAX_RADIUS + " 之间");
        }
        ChunkLoaderSavedData data = data(server);
        Anchor anchor = data.anchor(name).orElse(null);
        if (anchor == null) {
            return Result.failure("找不到加载点");
        }
        ServerLevel level = level(server, anchor);
        if (level == null) {
            return Result.failure("目标维度不存在：" + anchor.dimension());
        }
        if (anchor.enabled()) {
            try {
                setTickets(level, anchor, false);
            } catch (RuntimeException exception) {
                rollbackTickets(level, anchor, true);
                FakePlayerMod.LOGGER.error("准备修改区块加载点 {} 时撤销旧票失败", name, exception);
                return Result.failure("撤销旧区块票失败");
            }
        }
        Anchor changed = anchor.withSettings(radius, ticking);
        try {
            if (changed.enabled()) {
                setTickets(level, changed, true);
            }
            data.put(changed);
            ChunkLoaderBackupStore.save(server, data);
            return Result.success(changed);
        } catch (RuntimeException exception) {
            // 新配置失败时尽量恢复旧票，避免存档与实际状态分离。
            rollbackTickets(level, changed, false);
            if (anchor.enabled()) {
                rollbackTickets(level, anchor, true);
            }
            FakePlayerMod.LOGGER.error("修改区块加载点 {} 失败", name, exception);
            return Result.failure("更新区块票失败");
        }
    }

    public static Result remove(MinecraftServer server, String name) {
        ChunkLoaderSavedData data = data(server);
        Anchor anchor = data.anchor(name).orElse(null);
        if (anchor == null) {
            return Result.failure("找不到加载点");
        }
        ServerLevel level = level(server, anchor);
        if (anchor.enabled() && level == null) {
            return Result.failure("目标维度不存在，无法安全撤销票：" + anchor.dimension());
        }
        if (anchor.enabled()) {
            try {
                setTickets(level, anchor, false);
            } catch (RuntimeException exception) {
                rollbackTickets(level, anchor, true);
                FakePlayerMod.LOGGER.error("删除区块加载点 {} 失败", name, exception);
                return Result.failure("撤销区块票失败");
            }
        }
        data.remove(name);
        ChunkLoaderBackupStore.save(server, data);
        return Result.success(anchor);
    }

    public static boolean backup(MinecraftServer server) {
        return ChunkLoaderBackupStore.save(server, data(server));
    }

    /** 撤销当前票据后从最新可读备份恢复，再重新建立票据。 */
    public static Result restoreLatestBackup(MinecraftServer server) {
        ChunkLoaderSavedData current = data(server);
        ChunkLoaderSavedData restored = ChunkLoaderBackupStore.loadLatest(server).orElse(null);
        if (restored == null) {
            return Result.failure("没有可用的备份");
        }
        for (Anchor anchor : current.anchors()) {
            ServerLevel level = level(server, anchor);
            if (anchor.enabled() && level != null) {
                rollbackTickets(level, anchor, false);
            }
        }
        current.replaceAll(restored.anchors());
        reconcile(server);
        ChunkLoaderBackupStore.save(server, current);
        return Result.success();
    }

    private static ServerLevel level(MinecraftServer server, Anchor anchor) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, anchor.dimension());
        return server.getLevel(dimension);
    }

    private static void setTickets(ServerLevel level, Anchor anchor, boolean add) {
        ChunkPos center = new ChunkPos(anchor.position().getX() >> 4, anchor.position().getZ() >> 4);
        for (int x = center.x() - anchor.radius(); x <= center.x() + anchor.radius(); x++) {
            for (int z = center.z() - anchor.radius(); z <= center.z() + anchor.radius(); z++) {
                CONTROLLER.forceChunk(level, anchor.uuid(), x, z, add, anchor.ticking());
            }
        }
    }

    private static void rollbackTickets(ServerLevel level, Anchor anchor, boolean add) {
        try {
            setTickets(level, anchor, add);
        } catch (RuntimeException rollbackException) {
            FakePlayerMod.LOGGER.error("回滚区块加载点 {} 的票据失败", anchor.name(), rollbackException);
        }
    }

    private static void validateStoredTickets(ServerLevel level, TicketHelper helper) {
        Identifier dimension = level.dimension().identifier();
        for (var entry : helper.getEntityTickets().entrySet()) {
            UUID owner = entry.getKey();
            Anchor anchor = data(level.getServer()).anchors().stream()
                .filter(candidate -> candidate.uuid().equals(owner))
                .findFirst()
                .orElse(null);
            if (anchor == null || !anchor.enabled() || !anchor.dimension().equals(dimension)) {
                helper.removeAllTickets(owner);
                continue;
            }

            Set<Long> expected = expectedChunks(anchor);
            TicketSet tickets = entry.getValue();
            for (long chunk : tickets.normal()) {
                if (anchor.ticking() || !expected.contains(chunk)) {
                    helper.removeTicket(owner, chunk, false);
                }
            }
            for (long chunk : tickets.naturalSpawning()) {
                if (!anchor.ticking() || !expected.contains(chunk)) {
                    helper.removeTicket(owner, chunk, true);
                }
            }
        }
    }

    private static Set<Long> expectedChunks(Anchor anchor) {
        int centerX = anchor.position().getX() >> 4;
        int centerZ = anchor.position().getZ() >> 4;
        Set<Long> chunks = new HashSet<>();
        for (int x = centerX - anchor.radius(); x <= centerX + anchor.radius(); x++) {
            for (int z = centerZ - anchor.radius(); z <= centerZ + anchor.radius(); z++) {
                chunks.add(ChunkPos.pack(x, z));
            }
        }
        return chunks;
    }

    private static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public record Result(boolean successful, Optional<Anchor> anchor, String reason) {
        public static Result success(Anchor anchor) {
            return new Result(true, Optional.of(anchor), "");
        }

        public static Result success() {
            return new Result(true, Optional.empty(), "");
        }

        public static Result failure(String reason) {
            return new Result(false, Optional.empty(), reason);
        }
    }
}
