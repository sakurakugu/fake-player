package com.sakurakugu.fakeplayer.entity;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.network.PossessionStatePayload;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;

/** 维护真实玩家与假人之间的身体交换会话和服务端访问锁。 */
public final class FakePlayerPossession {
    private static final Map<UUID, Session> BY_VIEWER = new HashMap<>();
    private static final Map<UUID, Session> BY_TARGET = new HashMap<>();
    private FakePlayerPossession() {
    }

    public static boolean start(ServerPlayer viewer, FakeServerPlayer target) {
        if (isControlling(viewer, target)) {
            return true;
        }
        if (BY_VIEWER.containsKey(viewer.getUUID())) {
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

    /** 生命周期结束时仅丢弃会话，不再尝试恢复附身前的身体。 */
    public static void discard(ServerPlayer viewer) {
        Session session = BY_VIEWER.get(viewer.getUUID());
        if (session != null) {
            removeSession(session);
        }
    }

    public static void discardTarget(FakeServerPlayer target) {
        Session session = BY_TARGET.get(target.getUUID());
        if (session != null) {
            removeSession(session);
        }
    }

    /** 假玩家被移除前把身体状态交换回去；会话不处于活动状态或恢复失败时退回丢弃。 */
    public static void restoreTarget(FakeServerPlayer target) {
        Session session = BY_TARGET.get(target.getUUID());
        if (session == null) {
            return;
        }
        try {
            if (!stop(session)) {
                removeSession(session);
            }
        } catch (RuntimeException exception) {
            // 恢复失败不能阻断假玩家移除流程。
            FakePlayerMod.LOGGER.error("移除假玩家 {} 前恢复附身失败",
                target.getGameProfile().name(), exception);
            removeSession(session);
        }
    }

    public static void discardAll() {
        for (Session session : BY_VIEWER.values().toArray(Session[]::new)) {
            removeSession(session);
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
            if (activeBody.isSpectator()) {
                // 旁观模式只用于操作者脱离活动身体，不能覆盖假人进入附身前的模式和能力。
                activeBody.applyWithGameModeFrom(session.target, session.targetOriginal);
            } else {
                activeBody.apply(session.target);
            }
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
        return BY_TARGET.containsKey(target.getUUID());
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
