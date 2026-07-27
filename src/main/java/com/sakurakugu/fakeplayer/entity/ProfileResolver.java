package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig.ProfileStrategy;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Util;

/** 按服务端策略解析假玩家身份，并阻止同名异步解析并发执行。 */
public final class ProfileResolver {
    private static final Set<String> RESOLVING_NAMES = ConcurrentHashMap.newKeySet();

    private ProfileResolver() {
    }

    public static CompletableFuture<Result> resolve(MinecraftServer server, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!RESOLVING_NAMES.add(key)) {
            return CompletableFuture.completedFuture(Result.failure(Status.BUSY));
        }

        CompletableFuture<Result> result;
        try {
            result = resolveInternal(server, name);
        } catch (RuntimeException exception) {
            RESOLVING_NAMES.remove(key);
            return CompletableFuture.failedFuture(exception);
        }
        return result.whenComplete((ignored, throwable) -> RESOLVING_NAMES.remove(key));
    }

    private static CompletableFuture<Result> resolveInternal(MinecraftServer server, String name) {
        ProfileStrategy strategy = FakePlayerConfig.profileStrategy();
        if (strategy == ProfileStrategy.OFFLINE_ONLY) {
            return CompletableFuture.completedFuture(offline(name));
        }

        Optional<NameAndId> cached = server.services().nameToIdCache().get(name);
        if (strategy == ProfileStrategy.CACHE_ONLY) {
            return CompletableFuture.completedFuture(cached
                .map(identity -> Result.success(Status.RESOLVED_CACHE, toProfile(identity)))
                .orElseGet(() -> fallbackOffline(name, Status.NOT_FOUND)));
        }

        if (!server.usesAuthentication()) {
            return CompletableFuture.completedFuture(fallbackOffline(name, Status.NOT_FOUND));
        }

        // 在线服务器不能采用以前由离线模式写入缓存的同名 UUID。
        if (cached.isPresent()
            && !cached.get().id().equals(UUIDUtil.createOfflinePlayerUUID(cached.get().name()))) {
            NameAndId identity = cached.get();
            return CompletableFuture.completedFuture(Result.success(Status.RESOLVED_CACHE, toProfile(identity)));
        }

        return CompletableFuture
            .supplyAsync(() -> server.services().profileResolver().fetchByName(name), Util.backgroundExecutor())
            .handle((profile, throwable) -> {
                if (throwable != null) {
                    return fallbackOffline(name, Status.SERVICE_UNAVAILABLE);
                }
                if (profile.isEmpty()) {
                    return fallbackOffline(name, Status.NOT_FOUND);
                }
                GameProfile resolved = profile.get();
                return Result.success(Status.RESOLVED_ONLINE, resolved);
            })
            .thenApplyAsync(result -> {
                if (result.status() == Status.RESOLVED_ONLINE) {
                    server.services().nameToIdCache().add(new NameAndId(result.profile()));
                }
                return result;
            }, server);
    }

    private static Result fallbackOffline(String name, Status failure) {
        return FakePlayerConfig.allowOfflineProfiles() ? offline(name) : Result.failure(failure);
    }

    private static Result offline(String name) {
        return Result.success(
            Status.RESOLVED_OFFLINE,
            new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name));
    }

    private static GameProfile toProfile(NameAndId identity) {
        return new GameProfile(identity.id(), identity.name());
    }

    public enum Status {
        RESOLVED_ONLINE,
        RESOLVED_CACHE,
        RESOLVED_OFFLINE,
        NOT_FOUND,
        SERVICE_UNAVAILABLE,
        BUSY
    }

    public record Result(Status status, GameProfile profile) {
        public static Result success(Status status, GameProfile profile) {
            return new Result(status, profile);
        }

        public static Result failure(Status status) {
            return new Result(status, null);
        }

        public boolean successful() {
            return profile != null;
        }
    }
}
