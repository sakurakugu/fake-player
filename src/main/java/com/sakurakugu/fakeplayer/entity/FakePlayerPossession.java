package com.sakurakugu.fakeplayer.entity;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import com.sakurakugu.fakeplayer.network.PossessionInputPayload;
import com.sakurakugu.fakeplayer.network.PossessionStatePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 维护真实玩家与被附身假人之间的一对一控制会话。 */
public final class FakePlayerPossession {
    private static final int INPUT_TIMEOUT_TICKS = 40;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private FakePlayerPossession() {
    }

    public static boolean start(ServerPlayer viewer, FakeServerPlayer target) {
        if (viewer.level() != target.level() || target.hasDisconnected()
            || !FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())) {
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_unavailable"));
            return false;
        }
        if (SESSIONS.values().stream().anyMatch(session -> session.target == target && session.viewer != viewer)) {
            viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_occupied"));
            return false;
        }
        if (isControlling(viewer, target)) {
            return true;
        }

        stop(viewer);
        target.actions().stopPossessionControl();
        SESSIONS.put(viewer.getUUID(), new Session(viewer, target, target.tickCount));
        viewer.closeContainer();
        PacketDistributor.sendToPlayer(viewer, new PossessionStatePayload(target.getId()));
        return true;
    }

    public static void acceptInput(ServerPlayer viewer, PossessionInputPayload input) {
        Session session = validSession(viewer);
        if (session == null) {
            return;
        }
        if (!Float.isFinite(input.yaw()) || !Float.isFinite(input.pitch())) {
            stop(viewer);
            return;
        }
        session.lastInputTick = session.target.tickCount;
        session.target.actions().applyPossessionControl(
            Math.clamp(input.forward(), -1.0F, 1.0F),
            Math.clamp(input.strafe(), -1.0F, 1.0F),
            input.jump(), input.sneak(), input.sprint(), input.attack(), input.use()
        );
        session.target.actions().setRotation(Math.clamp(input.pitch(), -90.0F, 90.0F), input.yaw());
    }

    public static void openInventory(ServerPlayer viewer) {
        Session session = validSession(viewer);
        if (session != null) {
            FakePlayerMenuOpener.openPossessedInventory(viewer, session.target);
        }
    }

    public static boolean isControlling(ServerPlayer viewer, FakeServerPlayer target) {
        Session session = SESSIONS.get(viewer.getUUID());
        return session != null && session.target == target;
    }

    public static boolean isPossessed(FakeServerPlayer target) {
        return SESSIONS.values().stream().anyMatch(session -> session.target == target);
    }

    public static boolean stop(ServerPlayer viewer) {
        Session session = SESSIONS.remove(viewer.getUUID());
        if (session == null) {
            return false;
        }
        session.target.actions().stopPossessionControl();
        PacketDistributor.sendToPlayer(viewer, new PossessionStatePayload(PossessionStatePayload.NONE));
        return true;
    }

    public static void stopTarget(FakeServerPlayer target) {
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.target != target) {
                continue;
            }
            iterator.remove();
            target.actions().stopPossessionControl();
            PacketDistributor.sendToPlayer(session.viewer, new PossessionStatePayload(PossessionStatePayload.NONE));
        }
    }

    public static void tickTarget(FakeServerPlayer target) {
        Session session = SESSIONS.values().stream()
            .filter(candidate -> candidate.target == target)
            .findFirst()
            .orElse(null);
        if (session != null && (target.tickCount - session.lastInputTick > INPUT_TIMEOUT_TICKS
            || validSession(session.viewer) == null)) {
            stop(session.viewer);
        }
    }

    private static Session validSession(ServerPlayer viewer) {
        Session session = SESSIONS.get(viewer.getUUID());
        if (session == null) {
            return null;
        }
        if (viewer.hasDisconnected() || session.target.hasDisconnected()
            || viewer.level() != session.target.level()
            || !FakePlayerConfig.canUseCommands(viewer.createCommandSourceStack())) {
            stop(viewer);
            return null;
        }
        return session;
    }

    private static final class Session {
        private final ServerPlayer viewer;
        private final FakeServerPlayer target;
        private int lastInputTick;

        private Session(ServerPlayer viewer, FakeServerPlayer target, int lastInputTick) {
            this.viewer = viewer;
            this.target = target;
            this.lastInputTick = lastInputTick;
        }
    }
}
