package com.sakurakugu.fakeplayer.entity;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 封装命令和控制菜单可触发的假玩家动作。 */
public final class FakePlayerActions {
    private static final double REACH = 4.5;
    private static final double MOVE_SPEED = 0.22;

    public enum RepeatMode {
        ONCE,
        CONTINUOUS,
        INTERVAL
    }

    public enum MoveDirection {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT
    }

    private enum ScheduledAction {
        ATTACK,
        USE,
        JUMP,
        DROP
    }

    private final FakeServerPlayer player;
    private final Map<ScheduledAction, Schedule> schedules = new EnumMap<>(ScheduledAction.class);
    private MoveDirection moveDirection;
    private DropRequest dropRequest;

    FakePlayerActions(FakeServerPlayer player) {
        this.player = player;
    }

    public void tick() {
        if (moveDirection != null) {
            moveTick();
        }
        schedules.replaceAll((action, schedule) -> schedule.tick() ? run(action) : schedule);
    }

    private Schedule run(ScheduledAction action) {
        switch (action) {
            case ATTACK -> attackOnce();
            case USE -> useOnce();
            case JUMP -> jumpOnce();
            case DROP -> dropOnce();
        }
        return schedules.get(action);
    }

    public void attack(RepeatMode mode, int interval) {
        configure(ScheduledAction.ATTACK, mode, interval, this::attackOnce);
    }

    public void use(RepeatMode mode, int interval) {
        configure(ScheduledAction.USE, mode, interval, this::useOnce);
    }

    public void jump(RepeatMode mode, int interval) {
        configure(ScheduledAction.JUMP, mode, interval, this::jumpOnce);
    }

    public void drop(int slot, boolean wholeStack, RepeatMode mode, int interval) {
        dropRequest = new DropRequest(slot, wholeStack);
        configure(ScheduledAction.DROP, mode, interval, this::dropOnce);
    }

    public void dropAll(boolean wholeStack, RepeatMode mode, int interval) {
        drop(-1, wholeStack, mode, interval);
    }

    private void configure(ScheduledAction action, RepeatMode mode, int interval, Runnable once) {
        schedules.remove(action);
        if (mode == RepeatMode.ONCE) {
            once.run();
            return;
        }
        schedules.put(action, new Schedule(mode == RepeatMode.CONTINUOUS ? 1 : Math.max(1, interval)));
    }

    public void attackOnce() {
        player.swing(InteractionHand.MAIN_HAND);
        HitResult hit = pickTarget();
        if (hit instanceof EntityHitResult entityHit) {
            player.attack(entityHit.getEntity());
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            player.gameMode.handleBlockBreakAction(
                blockHit.getBlockPos(),
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                blockHit.getDirection(),
                player.level().getMaxY(),
                0
            );
        }
    }

    public void useOnce() {
        HitResult hit = pickTarget();
        if (hit instanceof EntityHitResult entityHit) {
            player.interactOn(entityHit.getEntity(), InteractionHand.MAIN_HAND, entityHit.getLocation());
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            player.gameMode.useItemOn(
                player,
                player.level(),
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND,
                blockHit
            );
        } else {
            player.gameMode.useItem(
                player,
                player.level(),
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND
            );
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    private HitResult pickTarget() {
        HitResult blockHit = player.pick(REACH, 1.0F, false);
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(REACH));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player,
            from,
            to,
            player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
            Entity::isPickable,
            REACH * REACH
        );
        if (entityHit == null) {
            return blockHit;
        }
        return from.distanceToSqr(entityHit.getLocation()) < from.distanceToSqr(blockHit.getLocation())
            ? entityHit
            : blockHit;
    }

    public void jumpOnce() {
        if (player.onGround()) {
            player.jumpFromGround();
        }
    }

    public void move(MoveDirection direction) {
        moveDirection = direction;
    }

    private void moveTick() {
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double x;
        double z;
        switch (moveDirection) {
            case FORWARD -> { x = forwardX; z = forwardZ; }
            case BACKWARD -> { x = -forwardX; z = -forwardZ; }
            case LEFT -> { x = -forwardZ; z = forwardX; }
            case RIGHT -> { x = forwardZ; z = -forwardX; }
            default -> throw new IllegalStateException("未知移动方向");
        }
        Vec3 velocity = player.getDeltaMovement();
        double speed = player.isSprinting() ? MOVE_SPEED * 1.3 : MOVE_SPEED;
        player.setDeltaMovement(x * speed, velocity.y, z * speed);
    }

    public void lookAt(Vec3 target) {
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        setRotation((float) -(Math.toDegrees(Math.atan2(delta.y, horizontal))),
            (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
    }

    public void look(Direction direction) {
        Vec3 normal = direction.getUnitVec3();
        lookAt(player.getEyePosition().add(normal));
    }

    public void turn(float yawDelta) {
        setRotation(player.getXRot(), player.getYRot() + yawDelta);
    }

    public void setRotation(float pitch, float yaw) {
        player.setXRot(Math.max(-90.0F, Math.min(90.0F, pitch)));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    public void setSneaking(boolean sneaking) {
        player.setShiftKeyDown(sneaking);
    }

    public void toggleSneak() {
        setSneaking(!player.isShiftKeyDown());
    }

    public void setSprinting(boolean sprinting) {
        player.setSprinting(sprinting);
    }

    public void swapHands() {
        ItemStack main = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getOffhandItem());
        player.setItemInHand(InteractionHand.OFF_HAND, main);
    }

    private void dropOnce() {
        if (dropRequest == null) {
            return;
        }
        if (dropRequest.slot() < 0) {
            for (int slot = 0; slot < 36; slot++) {
                dropFromSlot(slot, dropRequest.wholeStack());
            }
            return;
        }
        dropFromSlot(dropRequest.slot(), dropRequest.wholeStack());
    }

    private void dropFromSlot(int slot, boolean wholeStack) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        int count = wholeStack ? stack.getCount() : 1;
        player.drop(stack.split(count), false, true);
    }

    public void stop() {
        schedules.clear();
        moveDirection = null;
        dropRequest = null;
        player.stopUsingItem();
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(0.0, velocity.y, 0.0);
    }

    public boolean isRepeatingAttack() {
        return schedules.containsKey(ScheduledAction.ATTACK);
    }

    public boolean isRepeatingUse() {
        return schedules.containsKey(ScheduledAction.USE);
    }

    // 保留菜单所用的切换接口。
    public void toggleAttack() {
        if (isRepeatingAttack()) {
            schedules.remove(ScheduledAction.ATTACK);
        } else {
            attack(RepeatMode.CONTINUOUS, 1);
        }
    }

    public void toggleUse() {
        if (isRepeatingUse()) {
            schedules.remove(ScheduledAction.USE);
        } else {
            use(RepeatMode.CONTINUOUS, 1);
        }
    }

    public void jump() {
        jumpOnce();
    }

    private static final class Schedule {
        private final int interval;
        private int remaining;

        private Schedule(int interval) {
            this.interval = interval;
            this.remaining = 0;
        }

        private boolean tick() {
            if (remaining > 0) {
                remaining--;
                return false;
            }
            remaining = interval - 1;
            return true;
        }
    }

    private record DropRequest(int slot, boolean wholeStack) {
    }
}
