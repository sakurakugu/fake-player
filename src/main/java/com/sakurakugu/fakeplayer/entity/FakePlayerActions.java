package com.sakurakugu.fakeplayer.entity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/** 封装命令和控制菜单可触发的假玩家动作。 */
public final class FakePlayerActions {
    private static final int USE_COOLDOWN_TICKS = 3;
    private static final int BLOCK_BREAK_COOLDOWN_TICKS = 5;
    private static final int ALL_SLOTS = -1;
    private static final int ARMOR_SLOTS = -2;

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

    // USE 必须先于 ATTACK；同一刻使用成功时，原版客户端不会再执行攻击。
    public enum ScheduledAction {
        USE,
        ATTACK,
        JUMP,
        DROP
    }

    private final FakeServerPlayer player;
    private final Map<ScheduledAction, Schedule> schedules = new EnumMap<>(ScheduledAction.class);
    private float forwardInput;
    private float strafeInput;
    private DropRequest dropRequest;
    private BlockPos breakingBlock;
    private Direction breakingFace = Direction.DOWN;
    private float blockDamage;
    private int blockBreakCooldown;
    private int useCooldown;
    private int movementInputTicks;
    private float heldTurn;
    private int lastJumpTick = -1000;

    FakePlayerActions(FakeServerPlayer player) {
        this.player = player;
    }

    public void tick() {
        if (useCooldown > 0) {
            useCooldown--;
        }
        applyMovementInput();
        if (movementInputTicks > 0 && --movementInputTicks == 0) {
            setMovementInput(0.0F, 0.0F);
        }
        if (heldTurn != 0.0F) {
            turn(heldTurn);
        }
        if (player.isSpectator()) {
            stopTransientActions();
            return;
        }

        Boolean useSucceeded = tickAction(ScheduledAction.USE);
        Boolean attackSucceeded = null;
        if (!Boolean.TRUE.equals(useSucceeded)) {
            attackSucceeded = tickAction(ScheduledAction.ATTACK);
        }
        if (Boolean.TRUE.equals(attackSucceeded) && Boolean.FALSE.equals(useSucceeded)) {
            performUse();
        }
        tickAction(ScheduledAction.JUMP);
        tickAction(ScheduledAction.DROP);
    }

    private Boolean tickAction(ScheduledAction action) {
        Schedule schedule = schedules.get(action);
        if (schedule == null) {
            return null;
        }
        if (!schedule.tick()) {
            inactiveTick(action);
            return null;
        }
        if (schedule.isEveryTickPulse()) {
            // interval 1 是每刻重新按键，continuous 则是一直按住，两者对弓和挖掘的语义不同。
            inactiveTick(action);
        }
        return run(action, schedule.mode());
    }

    private boolean run(ScheduledAction action, RepeatMode mode) {
        return switch (action) {
            case USE -> performUse();
            case ATTACK -> attackOnce(mode == RepeatMode.CONTINUOUS);
            case JUMP -> {
                jumpOnce(mode != RepeatMode.ONCE);
                yield false;
            }
            case DROP -> {
                dropOnce();
                yield false;
            }
        };
    }

    public void attack(RepeatMode mode, int interval) {
        configure(ScheduledAction.ATTACK, mode, interval);
    }

    public void use(RepeatMode mode, int interval) {
        configure(ScheduledAction.USE, mode, interval);
    }

    public void jump(RepeatMode mode, int interval) {
        configure(ScheduledAction.JUMP, mode, interval);
    }

    public void drop(int slot, boolean wholeStack, RepeatMode mode, int interval) {
        dropRequest = new DropRequest(slot, wholeStack, false, 1);
        configure(ScheduledAction.DROP, mode, interval);
    }

    /** 从指定槽位丢出固定数量；数量超过当前堆叠时会丢出剩余全部物品。 */
    public void dropAmount(int slot, int amount, RepeatMode mode, int interval) {
        dropRequest = new DropRequest(slot, false, false, Math.max(1, amount));
        configure(ScheduledAction.DROP, mode, interval);
    }

    /** 按执行时物品堆的当前数量计算丢弃比例。 */
    public void dropPercentage(int slot, int percentage, RepeatMode mode, int interval) {
        dropRequest = new DropRequest(slot, false, true, Math.clamp(percentage, 1, 100));
        configure(ScheduledAction.DROP, mode, interval);
    }

    public void dropAll(boolean wholeStack, RepeatMode mode, int interval) {
        drop(ALL_SLOTS, wholeStack, mode, interval);
    }

    public void dropArmor(boolean wholeStack, RepeatMode mode, int interval) {
        drop(ARMOR_SLOTS, wholeStack, mode, interval);
    }

    private void configure(ScheduledAction action, RepeatMode mode, int interval) {
        stopAction(action);
        if (mode == RepeatMode.ONCE) {
            if (!player.isSpectator()) {
                run(action, mode);
            }
            inactiveTick(action);
            return;
        }
        int ticks = mode == RepeatMode.CONTINUOUS ? 1 : Math.max(1, interval);
        schedules.put(action, new Schedule(mode, ticks));
    }

    public void attackOnce() {
        attackOnce(false);
        abortBreakingBlock();
    }

    private boolean attackOnce(boolean continuous) {
        HitResult hit = pickAttackTarget();
        if (hit instanceof EntityHitResult entityHit) {
            abortBreakingBlock();
            if (!player.level().getWorldBorder().isWithinBounds(entityHit.getEntity().blockPosition())) {
                return false;
            }
            // 按住攻击键只持续挖方块；攻击实体需要新的按键脉冲。
            if (!continuous) {
                ItemStack weapon = player.getMainHandItem();
                if (weapon.has(DataComponents.PIERCING_WEAPON) || player.cannotAttackWithItem(weapon, 5)) {
                    return false;
                }
                player.attack(entityHit.getEntity());
                player.swing(InteractionHand.MAIN_HAND);
            }
            player.resetAttackStrengthTicker();
            player.resetLastActionTime();
            return true;
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return attackBlock(blockHit);
        }
        abortBreakingBlock();
        return false;
    }

    private HitResult pickAttackTarget() {
        HitResult blockHit = player.pick(player.blockInteractionRange(), 1.0F, false);
        HitResult entityRangeHit = player.getAttackRangeWith(player.getMainHandItem()).getClosesetHit(
            player,
            1.0F,
            entity -> !entity.isSpectator() && entity.isPickable() && entity.isAttackable()
        );
        if (!(entityRangeHit instanceof EntityHitResult entityHit)) {
            return blockHit;
        }
        Vec3 eye = player.getEyePosition();
        return eye.distanceToSqr(entityHit.getLocation()) < eye.distanceToSqr(blockHit.getLocation())
            ? entityHit
            : blockHit;
    }

    private boolean attackBlock(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        boolean blockBroken = false;
        if (blockBreakCooldown > 0) {
            blockBreakCooldown--;
            return false;
        }
        if (player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer())) {
            abortBreakingBlock();
            return false;
        }

        BlockState state = player.level().getBlockState(pos);
        if (state.isAir()) {
            abortBreakingBlock();
            return false;
        }
        if (!pos.equals(breakingBlock)) {
            abortBreakingBlock();
            player.gameMode.handleBlockBreakAction(
                pos,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                face,
                player.level().getMaxY(),
                -1
            );
            if (!player.level().getBlockState(pos).isAir()) {
                breakingBlock = pos.immutable();
                breakingFace = face;
                blockDamage = 0.0F;
            } else {
                blockBreakCooldown = BLOCK_BREAK_COOLDOWN_TICKS;
                blockBroken = true;
            }
        } else {
            blockDamage += state.getDestroyProgress(player, player.level(), pos);
            if (blockDamage >= 1.0F) {
                player.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    face,
                    player.level().getMaxY(),
                    -1
                );
                breakingBlock = null;
                blockDamage = 0.0F;
                blockBreakCooldown = BLOCK_BREAK_COOLDOWN_TICKS;
                blockBroken = true;
            }
        }

        player.resetLastActionTime();
        player.swing(InteractionHand.MAIN_HAND);
        return blockBroken;
    }

    public void useOnce() {
        performUse();
        player.releaseUsingItem();
    }

    private boolean performUse() {
        if (useCooldown > 0) {
            return true;
        }
        if (player.isUsingItem()) {
            return true;
        }

        HitResult hit = pickUseTarget();
        for (InteractionHand hand : InteractionHand.values()) {
            if (!player.getItemInHand(hand).isItemEnabled(player.level().enabledFeatures())) {
                continue;
            }
            InteractionResult result = interact(hit, hand);
            if (!result.consumesAction()) {
                result = player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
            }
            if (result.consumesAction()) {
                swingIfNeeded(hand, result);
                useCooldown = USE_COOLDOWN_TICKS;
                player.resetLastActionTime();
                return true;
            }
        }
        return false;
    }

    private InteractionResult interact(HitResult hit, InteractionHand hand) {
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            int topOffset = blockHit.getDirection() == Direction.UP ? 1 : 0;
            if (pos.getY() < player.level().getMaxY() - topOffset && player.level().mayInteract(player, pos)) {
                return player.gameMode.useItemOn(
                    player,
                    player.level(),
                    player.getItemInHand(hand),
                    hand,
                    blockHit
                );
            }
        } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (player.level().getWorldBorder().isWithinBounds(entity.blockPosition())
                && player.isWithinEntityInteractionRange(entity, 0.0)) {
                Vec3 relativeHit = entityHit.getLocation().subtract(entity.position());
                InteractionResult result = entity.interact(player, hand, relativeHit);
                return result.consumesAction() ? result : player.interactOn(entity, hand, relativeHit);
            }
        }
        return InteractionResult.PASS;
    }

    private void swingIfNeeded(InteractionHand hand, InteractionResult result) {
        if (result instanceof InteractionResult.Success success
            && success.swingSource() != InteractionResult.SwingSource.NONE) {
            player.swing(hand);
        }
    }

    private HitResult pickUseTarget() {
        double blockReach = player.blockInteractionRange();
        double entityReach = player.entityInteractionRange();
        HitResult blockHit = player.pick(blockReach, 1.0F, false);
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(entityReach));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player,
            from,
            to,
            player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
            entity -> !entity.isSpectator() && entity.isPickable(),
            entityReach * entityReach
        );
        if (entityHit == null) {
            return blockHit;
        }
        return from.distanceToSqr(entityHit.getLocation()) < from.distanceToSqr(blockHit.getLocation())
            ? entityHit
            : blockHit;
    }

    public void jumpOnce() {
        jumpOnce(false);
    }

    private void jumpOnce(boolean held) {
        if (held) {
            player.setJumping(true);
        } else if (player.getAbilities().mayfly
            && player.tickCount - lastJumpTick <= 10
            && (player.getAbilities().flying || !player.onGround())) {
            // 假人没有客户端按键包，因此在服务端按两次跳跃直接切换飞行状态。
            player.getAbilities().flying = !player.getAbilities().flying;
            player.onUpdateAbilities();
            lastJumpTick = -1000;
        } else if (player.onGround()) {
            player.jumpFromGround();
            lastJumpTick = player.tickCount;
        } else if (!player.onClimbable()) {
            player.tryToStartFallFlying();
            lastJumpTick = player.tickCount;
        }
    }

    public void move(MoveDirection direction) {
        switch (direction) {
            case FORWARD -> setMovementInput(1.0F, 0.0F);
            case BACKWARD -> setMovementInput(-1.0F, 0.0F);
            case LEFT -> setMovementInput(0.0F, 1.0F);
            case RIGHT -> setMovementInput(0.0F, -1.0F);
        }
    }

    /** 执行一个刻的移动输入，供控制界面的单击使用。 */
    public void moveOnce(MoveDirection direction) {
        move(direction);
        movementInputTicks = 1;
    }

    /** 在飞行状态下给假人一个竖直方向的移动速度。 */
    public void flyVertical(boolean upward) {
        if (!player.getAbilities().flying) {
            return;
        }
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(movement.x, upward ? 0.3D : -0.3D, movement.z);
        player.hurtMarked = true;
    }

    /** 开始持续移动，直到界面发送停止动作。 */
    public void startMove(MoveDirection direction) {
        move(direction);
        movementInputTicks = Integer.MAX_VALUE;
    }

    public void stopMove() {
        movementInputTicks = 0;
        setMovementInput(0.0F, 0.0F);
    }

    public void startAttack() {
        attack(RepeatMode.CONTINUOUS, 1);
    }

    public void startUse() {
        use(RepeatMode.CONTINUOUS, 1);
    }

    public void startJump() {
        jump(RepeatMode.CONTINUOUS, 1);
    }

    public void stopAttack() {
        stopAction(ScheduledAction.ATTACK);
    }

    public void stopUse() {
        stopAction(ScheduledAction.USE);
    }

    public void stopJump() {
        stopAction(ScheduledAction.JUMP);
    }

    public void startTurn(float yawDelta) {
        heldTurn = yawDelta;
    }

    public void stopTurn() {
        heldTurn = 0.0F;
    }

    private void setMovementInput(float forward, float strafe) {
        forwardInput = forward;
        strafeInput = strafe;
    }

    private void applyMovementInput() {
        float multiplier = player.isShiftKeyDown() ? 0.3F : 1.0F;
        player.zza = forwardInput * multiplier;
        player.xxa = strafeInput * multiplier;
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
        if (sneaking && player.isSprinting()) {
            setSprinting(false);
        }
    }

    public void toggleSneak() {
        setSneaking(!player.isShiftKeyDown());
    }

    public void setSprinting(boolean sprinting) {
        player.setSprinting(sprinting);
        if (sprinting && player.isShiftKeyDown()) {
            setSneaking(false);
        }
    }

    public void swapHands() {
        var event = CommonHooks.onLivingSwapHandItems(player);
        if (event.isCanceled()) {
            return;
        }
        player.setItemInHand(InteractionHand.OFF_HAND, event.getItemSwappedToOffHand());
        player.setItemInHand(InteractionHand.MAIN_HAND, event.getItemSwappedToMainHand());
        player.stopUsingItem();
        player.resetLastActionTime();
    }

    private void dropOnce() {
        if (dropRequest == null) {
            return;
        }
        if (dropRequest.slot() == ALL_SLOTS) {
            // 0-35 为主背包，36-39 为护甲栏，40 为副手。
            for (int slot = 0; slot <= 40; slot++) {
                dropFromSlot(slot, dropRequest.wholeStack());
            }
        } else if (dropRequest.slot() == ARMOR_SLOTS) {
            for (int slot = 36; slot <= 39; slot++) {
                dropFromSlot(slot, dropRequest.wholeStack());
            }
        } else {
            dropFromSlot(dropRequest.slot(), dropRequest.wholeStack());
        }
        player.resetLastActionTime();
    }

    private void dropFromSlot(int slot, boolean wholeStack) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        int count = wholeStack
            ? stack.getCount()
            : dropRequest.percentage()
                ? Math.max(1, (stack.getCount() * dropRequest.amount() + 99) / 100)
                : Math.min(dropRequest.amount(), stack.getCount());
        player.drop(stack.split(count), false, true);
    }

    public void stop() {
        for (ScheduledAction action : ScheduledAction.values()) {
            stopAction(action);
        }
        dropRequest = null;
        forwardInput = 0.0F;
        strafeInput = 0.0F;
        movementInputTicks = 0;
        heldTurn = 0.0F;
        player.zza = 0.0F;
        player.xxa = 0.0F;
        player.setShiftKeyDown(false);
        player.setSprinting(false);
    }

    /** 生成不含实体引用的动作快照，供世界存档和预设复用。 */
    public State snapshot() {
        List<ScheduledState> scheduledStates = schedules.entrySet().stream()
            .map(entry -> new ScheduledState(
                entry.getKey(), entry.getValue().mode, entry.getValue().interval, entry.getValue().remaining))
            .toList();
        Optional<DropState> drop = Optional.ofNullable(dropRequest)
            .map(request -> new DropState(
                request.slot(), request.wholeStack(), request.percentage(), request.amount()));
        return new State(
            scheduledStates,
            forwardInput,
            strafeInput,
            drop,
            player.isShiftKeyDown(),
            player.isSprinting()
        );
    }

    /** 在假人完成登录后恢复动作；无效或过期的运行时挖掘状态不会被恢复。 */
    public void restore(State state) {
        stop();
        forwardInput = state.forwardInput();
        strafeInput = state.strafeInput();
        dropRequest = state.drop()
            .map(value -> new DropRequest(
                value.slot(), value.wholeStack(), value.percentage(), Math.max(1, value.amount())))
            .orElse(null);
        for (ScheduledState scheduled : state.schedules()) {
            int interval = Math.max(1, scheduled.interval());
            Schedule schedule = new Schedule(scheduled.mode(), interval);
            schedule.remaining = Math.max(0, Math.min(scheduled.remaining(), interval - 1));
            schedules.put(scheduled.action(), schedule);
        }
        setSneaking(state.sneaking());
        setSprinting(state.sprinting());
    }

    private void stopTransientActions() {
        abortBreakingBlock();
        player.releaseUsingItem();
        player.setJumping(false);
        useCooldown = 0;
    }

    private void stopAction(ScheduledAction action) {
        if (schedules.remove(action) != null) {
            inactiveTick(action);
        }
    }

    private void inactiveTick(ScheduledAction action) {
        switch (action) {
            case ATTACK -> abortBreakingBlock();
            case USE -> {
                useCooldown = 0;
                player.releaseUsingItem();
            }
            case JUMP -> player.setJumping(false);
            case DROP -> {
            }
        }
    }

    private void abortBreakingBlock() {
        if (breakingBlock == null) {
            blockDamage = 0.0F;
            return;
        }
        player.gameMode.handleBlockBreakAction(
            breakingBlock,
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
            breakingFace,
            player.level().getMaxY(),
            -1
        );
        breakingBlock = null;
        blockDamage = 0.0F;
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
            stopAction(ScheduledAction.ATTACK);
        } else {
            attack(RepeatMode.CONTINUOUS, 1);
        }
    }

    public void toggleUse() {
        if (isRepeatingUse()) {
            stopAction(ScheduledAction.USE);
        } else {
            use(RepeatMode.CONTINUOUS, 1);
        }
    }

    public void jump() {
        jumpOnce();
    }

    private static final class Schedule {
        private final RepeatMode mode;
        private final int interval;
        private int remaining;

        private Schedule(RepeatMode mode, int interval) {
            this.mode = mode;
            this.interval = interval;
        }

        private RepeatMode mode() {
            return mode;
        }

        private boolean isEveryTickPulse() {
            return mode == RepeatMode.INTERVAL && interval == 1;
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

    private record DropRequest(int slot, boolean wholeStack, boolean percentage, int amount) {
    }

    public record ScheduledState(ScheduledAction action, RepeatMode mode, int interval, int remaining) {
    }

    public record DropState(int slot, boolean wholeStack, boolean percentage, int amount) {
    }

    public record State(
        List<ScheduledState> schedules,
        float forwardInput,
        float strafeInput,
        Optional<DropState> drop,
        boolean sneaking,
        boolean sprinting
    ) {
        public static final State EMPTY = new State(List.of(), 0.0F, 0.0F, Optional.empty(), false, false);

        public State {
            schedules = List.copyOf(schedules);
        }
    }
}
