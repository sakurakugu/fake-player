package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.network.PossessionStatePayload;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.PossessionRecovery;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;

/** 维护真实玩家与假人之间的身体交换会话和服务端访问锁。 */
public final class FakePlayerPossession {
    private static final Map<UUID, Session> BY_VIEWER = new HashMap<>();
    private static final Map<UUID, Session> BY_TARGET = new HashMap<>();
    private static final Map<UUID, PossessionRecovery> PENDING_RECOVERIES = new HashMap<>();
    private static final Set<UUID> RECOVERY_LOCKS = new HashSet<>();
    private static final Set<UUID> RESTORED_RECOVERY_TARGETS = new HashSet<>();

    private FakePlayerPossession() {
    }

    public static boolean start(ServerPlayer viewer, FakeServerPlayer target) {
        if (isControlling(viewer, target)) {
            return true;
        }
        if (BY_VIEWER.containsKey(viewer.getUUID()) || PENDING_RECOVERIES.containsKey(viewer.getUUID())) {
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_unavailable"));
            return false;
        }
        if (!canStart(viewer, target)) {
            return false;
        }
        if (isPossessed(target)) {
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_occupied"));
            return false;
        }

        prepareForExchange(viewer);
        prepareForExchange(target);
        PossessionBodyState viewerOriginal = PossessionBodyState.capture(viewer);
        PossessionBodyState targetOriginal = PossessionBodyState.capture(target);
        try {
            // 先落恢复记录，再修改任一实体；正常退出后才提交并删除记录。
            FakePlayerPersistence.beginPossessionRecovery(viewer, target);
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("写入附身恢复记录失败", exception);
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_failed"));
            return false;
        }
        Session session = new Session(
            viewer, target, viewerOriginal, targetOriginal, target.actions().snapshot(), State.STARTING
        );
        BY_VIEWER.put(viewer.getUUID(), session);
        BY_TARGET.put(target.getUUID(), session);
        target.actions().stop();

        try {
            targetOriginal.apply(viewer);
            viewerOriginal.apply(target);
            session.state = State.ACTIVE;
            broadcast(session, true);
            return true;
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("开始附身身体交换失败，正在恢复 {} 与 {}",
                viewer.getGameProfile().name(), target.getGameProfile().name(), exception);
            if (recoverOriginal(session)) {
                FakePlayerPersistence.completePossessionRecovery(viewer.level().getServer(), viewer.getUUID());
                removeSession(session);
            }
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_failed"));
            return false;
        }
    }

    public static boolean stop(ServerPlayer viewer) {
        Session session = BY_VIEWER.get(viewer.getUUID());
        return session != null && stop(session);
    }

    public static boolean stopTarget(FakeServerPlayer target) {
        Session session = BY_TARGET.get(target.getUUID());
        if (session != null) {
            stop(session);
        }
        return !isPossessed(target);
    }

    public static void stopAll() {
        for (Session session : BY_VIEWER.values().toArray(Session[]::new)) {
            stop(session);
        }
    }

    private static boolean stop(Session session) {
        if (session.state != State.ACTIVE) {
            return false;
        }
        session.state = State.STOPPING;
        prepareForExchange(session.viewer);
        prepareForExchange(session.target);
        try {
            PossessionBodyState activeBody = PossessionBodyState.capture(session.viewer);
            PossessionBodyState shellBody = PossessionBodyState.capture(session.target);
            shellBody.apply(session.viewer);
            activeBody.apply(session.target);
            finish(session);
            return true;
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("退出附身身体交换失败，正在恢复进入附身前的状态", exception);
            session.state = State.RECOVERING;
            if (recoverOriginal(session)) {
                finish(session);
                return true;
            }
            return false;
        }
    }

    private static void finish(Session session) {
        session.target.actions().restore(session.targetActions);
        FakePlayerPersistence.track(session.target);
        FakePlayerPersistence.completePossessionRecovery(
            session.viewer.level().getServer(), session.viewer.getUUID()
        );
        removeSession(session);
        broadcast(session, false);
    }

    private static boolean recoverOriginal(Session session) {
        try {
            session.viewerOriginal.apply(session.viewer);
            session.targetOriginal.apply(session.target);
            session.target.actions().restore(session.targetActions);
            return true;
        } catch (RuntimeException recoveryException) {
            FakePlayerMod.LOGGER.error("恢复附身前状态失败；为防止复制，保留会话锁", recoveryException);
            session.state = State.RECOVERING;
            return false;
        }
    }

    /** 活动身体死亡时取消真实玩家死亡，并让承载该状态的假人按原版流程死亡。 */
    public static boolean handleActiveBodyDeath(ServerPlayer viewer, DamageSource source) {
        Session session = BY_VIEWER.get(viewer.getUUID());
        if (session == null || session.state != State.ACTIVE) {
            return false;
        }
        FakeServerPlayer target = session.target;
        if (!stop(session)) {
            return false;
        }
        target.die(source);
        return true;
    }

    /** 躯壳死亡时恢复交换，并把死亡结果转移给真实玩家原来的身体。 */
    public static boolean handleShellDeath(FakeServerPlayer target, DamageSource source) {
        Session session = BY_TARGET.get(target.getUUID());
        if (session == null || session.state != State.ACTIVE) {
            return false;
        }
        ServerPlayer viewer = session.viewer;
        if (!stop(session)) {
            return false;
        }
        viewer.setHealth(0.0F);
        viewer.die(source);
        return true;
    }

    public static boolean isControlling(ServerPlayer viewer, FakeServerPlayer target) {
        Session session = BY_VIEWER.get(viewer.getUUID());
        return session != null && session.target == target && session.state == State.ACTIVE;
    }

    public static boolean isPossessing(ServerPlayer viewer) {
        return BY_VIEWER.containsKey(viewer.getUUID());
    }

    public static boolean isPossessed(FakeServerPlayer target) {
        return BY_TARGET.containsKey(target.getUUID()) || RECOVERY_LOCKS.contains(target.getUUID());
    }

    public static PlayerEnderChestContainer possessedEnderChest(ServerPlayer viewer) {
        Session session = BY_VIEWER.get(viewer.getUUID());
        return session != null && session.state == State.ACTIVE
            ? session.target.getEnderChestInventory()
            : null;
    }

    public static void tickTarget(FakeServerPlayer target) {
        Session session = BY_TARGET.get(target.getUUID());
        if (session == null || session.state != State.ACTIVE) {
            return;
        }
        if (session.viewer.hasDisconnected() || target.hasDisconnected()
            || session.viewer.level() != target.level()
            || !FakePlayerConfig.canUseCommands(session.viewer.createCommandSourceStack())) {
            stop(session);
        }
    }

    public static void syncTo(ServerPlayer player) {
        if (player instanceof FakeServerPlayer) {
            return;
        }
        for (Session session : BY_VIEWER.values()) {
            sendAppearanceState(player, PossessionStatePayload.started(
                session.viewer.getId(), session.target.getId()
            ));
        }
    }

    /** 世界启动时先恢复假人，并把真实玩家快照保留到该玩家下次登录。 */
    public static void recoverSavedSessions(MinecraftServer server) {
        PENDING_RECOVERIES.clear();
        RECOVERY_LOCKS.clear();
        RESTORED_RECOVERY_TARGETS.clear();
        for (PossessionRecovery recovery : FakePlayerPersistence.data(server).possessionRecoveries()) {
            PENDING_RECOVERIES.put(recovery.operatorUuid(), recovery);
            RECOVERY_LOCKS.add(recovery.targetUuid());
            FakeServerPlayer target = FakePlayerManager.find(
                server, new GameProfile(recovery.targetUuid(), recovery.targetName())
            );
            if (target == null) {
                FakePlayerMod.LOGGER.warn("附身恢复记录中的假人 {} 尚未恢复，继续保留访问锁", recovery.targetName());
                continue;
            }
            try {
                FakePlayerPersistence.applyPlayerData(target, recovery.targetData());
                syncAfterPersistentRecovery(target);
                FakePlayerPersistence.track(target);
                RESTORED_RECOVERY_TARGETS.add(recovery.targetUuid());
            } catch (RuntimeException exception) {
                FakePlayerMod.LOGGER.error("恢复附身假人 {} 的原状态失败", recovery.targetName(), exception);
            }
        }
    }

    public static void recoverPlayer(ServerPlayer player) {
        PossessionRecovery recovery = PENDING_RECOVERIES.get(player.getUUID());
        if (recovery == null) {
            return;
        }
        try {
            FakePlayerPersistence.applyPlayerData(player, recovery.operatorData());
            syncAfterPersistentRecovery(player);
            if (RESTORED_RECOVERY_TARGETS.contains(recovery.targetUuid())) {
                FakePlayerPersistence.completePossessionRecovery(player.level().getServer(), player.getUUID());
                PENDING_RECOVERIES.remove(player.getUUID());
                RECOVERY_LOCKS.remove(recovery.targetUuid());
                RESTORED_RECOVERY_TARGETS.remove(recovery.targetUuid());
            }
            player.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_recovered"));
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("恢复玩家 {} 的附身前状态失败", player.getGameProfile().name(), exception);
            player.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_recovery_failed"));
        }
    }

    private static void syncAfterPersistentRecovery(ServerPlayer player) {
        player.connection.teleport(
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
        );
        player.onUpdateAbilities();
        player.inventoryMenu.broadcastFullState();
        player.connection.resetPosition();
        player.level().getChunkSource().move(player);
    }

    private static boolean canStart(ServerPlayer viewer, FakeServerPlayer target) {
        if (viewer == target || viewer.hasDisconnected() || target.hasDisconnected()
            || viewer.level() != target.level() || !viewer.isAlive() || !target.isAlive()
            || viewer.isPassenger() || target.isPassenger() || viewer.isVehicle() || target.isVehicle()
            || !FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())) {
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_unavailable"));
            return false;
        }
        GameType viewerMode = viewer.gameMode.getGameModeForPlayer();
        GameType targetMode = target.gameMode.getGameModeForPlayer();
        boolean allowed = switch (viewerMode) {
            case CREATIVE -> targetMode != GameType.SPECTATOR;
            case SURVIVAL -> targetMode == GameType.SURVIVAL || targetMode == GameType.ADVENTURE;
            case ADVENTURE -> targetMode == GameType.ADVENTURE;
            default -> false;
        };
        if (!allowed) {
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_gamemode"));
        }
        return allowed;
    }

    private static void prepareForExchange(ServerPlayer player) {
        player.closeContainer();
        player.stopUsingItem();
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        player.stopRiding();
    }

    private static void broadcast(Session session, boolean active) {
        PossessionStatePayload payload = active
            ? PossessionStatePayload.started(session.viewer.getId(), session.target.getId())
            : PossessionStatePayload.stopped(session.viewer.getId());
        for (ServerPlayer player : session.viewer.level().getServer().getPlayerList().getPlayers()) {
            if (!(player instanceof FakeServerPlayer)) {
                sendAppearanceState(player, payload);
            }
        }
    }

    private static void sendAppearanceState(ServerPlayer player, PossessionStatePayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException exception) {
            // 外观同步失败不能回滚已经成功提交的服务端身体交换。
            FakePlayerMod.LOGGER.warn("向玩家 {} 同步附身外观失败", player.getGameProfile().name(), exception);
        }
    }

    private static void removeSession(Session session) {
        BY_VIEWER.remove(session.viewer.getUUID(), session);
        BY_TARGET.remove(session.target.getUUID(), session);
        session.state = State.IDLE;
    }

    private enum State {
        IDLE,
        STARTING,
        ACTIVE,
        STOPPING,
        RECOVERING
    }

    private static final class Session {
        private final ServerPlayer viewer;
        private final FakeServerPlayer target;
        private final PossessionBodyState viewerOriginal;
        private final PossessionBodyState targetOriginal;
        private final FakePlayerActions.State targetActions;
        private State state;

        private Session(
            ServerPlayer viewer,
            FakeServerPlayer target,
            PossessionBodyState viewerOriginal,
            PossessionBodyState targetOriginal,
            FakePlayerActions.State targetActions,
            State state
        ) {
            this.viewer = viewer;
            this.target = target;
            this.viewerOriginal = viewerOriginal;
            this.targetOriginal = targetOriginal;
            this.targetActions = targetActions;
            this.state = state;
        }
    }

}
