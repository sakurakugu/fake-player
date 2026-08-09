package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig.ProfileStrategy;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Util;

/** 按服务端策略解析假玩家身份，并阻止同名异步解析并发执行。 */
public final class ProfileResolver {
    private static final Pattern MOJANG_PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
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

        if (strategy == ProfileStrategy.CACHE_ONLY) {
            Optional<NameAndId> cached = server.services().nameToIdCache().get(name);
            return CompletableFuture.completedFuture(cached
                .map(identity -> Result.success(Status.RESOLVED_CACHE, toProfile(identity)))
                .orElseGet(() -> fallbackOffline(name, Status.NOT_FOUND)));
        }

        if (!MOJANG_PLAYER_NAME.matcher(name).matches()) {
            return CompletableFuture.completedFuture(fallbackOffline(name, Status.NOT_FOUND));
        }

        // 名称缓存可能包含离线 UUID；只复用已确认的正版身份，避免缓存污染。
        return CompletableFuture
            .supplyAsync(() -> resolveOnline(server, name), Util.nonCriticalIoPool())
            .thenApplyAsync(result -> {
                if (result.status() == Status.RESOLVED_ONLINE) {
                    server.services().nameToIdCache().add(new NameAndId(result.profile()));
                }
                return result;
            }, server);
    }

    private static Result resolveOnline(MinecraftServer server, String name) {
        Optional<NameAndId> cached;
        try {
            cached = server.services().nameToIdCache().get(name);
        } catch (RuntimeException exception) {
            return fallbackOffline(name, Status.SERVICE_UNAVAILABLE);
        }

        NameAndId identity;
        Status status;
        if (cached.isPresent() && isOnlineIdentity(cached.get())) {
            identity = cached.get();
            status = Status.RESOLVED_CACHE;
        } else {
            Optional<com.mojang.authlib.yggdrasil.response.NameAndId> queried;
            try {
                queried = server.services().profileRepository().findProfileByName(name);
            } catch (RuntimeException exception) {
                return fallbackOffline(name, Status.SERVICE_UNAVAILABLE);
            }
            if (queried.isEmpty()) {
                return fallbackOffline(name, Status.NOT_FOUND);
            }
            com.mojang.authlib.yggdrasil.response.NameAndId result = queried.get();
            identity = new NameAndId(new GameProfile(result.id(), result.name()));
            status = Status.RESOLVED_ONLINE;
        }

        GameProfile profile = toProfile(identity);
        try {
            profile = server.services().profileResolver().fetchById(identity.id()).orElse(profile);
        } catch (RuntimeException exception) {
            // 身份已经确认时不切换 UUID；皮肤服务恢复后重新生成即可再次补全。
            FakePlayerMod.LOGGER.warn("获取玩家 {} 的皮肤失败，将使用默认皮肤", identity.name(), exception);
        }
        return Result.success(status, profile);
    }

    private static boolean isOnlineIdentity(NameAndId identity) {
        return !identity.id().equals(UUIDUtil.createOfflinePlayerUUID(identity.name()));
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
