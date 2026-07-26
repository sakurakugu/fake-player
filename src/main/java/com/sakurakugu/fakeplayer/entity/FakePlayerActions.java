package com.sakurakugu.fakeplayer.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 封装假玩家可由控制菜单触发的动作及其持续执行状态。 */
public final class FakePlayerActions {
    // 与生存模式下玩家交互距离保持一致。
    private static final double REACH = 4.5;
    private final FakeServerPlayer player;
    private boolean repeatingAttack;
    private boolean repeatingUse;
    private int actionCooldown;

    FakePlayerActions(FakeServerPlayer player) {
        this.player = player;
    }

    public void tick() {
        // 每 10 tick 最多执行一次持续动作，避免每刻攻击或使用物品。
        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }
        if (repeatingAttack) {
            attackOnce();
            actionCooldown = 9;
        } else if (repeatingUse) {
            useOnce();
            actionCooldown = 9;
        }
    }

    public void attackOnce() {
        player.swing(InteractionHand.MAIN_HAND);
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(REACH));
        // 沿视线查找第一个可碰撞实体，并用原版玩家攻击流程处理伤害。
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player,
            from,
            to,
            player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
            Entity::isPickable,
            REACH * REACH
        );
        if (hit != null) {
            player.attack(hit.getEntity());
        }
    }

    public void useOnce() {
        HitResult hit = player.pick(REACH, 1.0F, false);
        // 命中方块时优先执行方块交互，否则尝试直接使用主手物品。
        if (hit instanceof BlockHitResult blockHit) {
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

    public void jump() {
        if (player.onGround()) {
            player.jumpFromGround();
        }
    }

    public void turn(float degrees) {
        float yaw = player.getYRot() + degrees;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    public void toggleSneak() {
        player.setShiftKeyDown(!player.isShiftKeyDown());
    }

    public void toggleAttack() {
        // 攻击和使用是互斥的持续动作，切换一个时总会关闭另一个。
        repeatingAttack = !repeatingAttack;
        repeatingUse = false;
        actionCooldown = 0;
    }

    public void toggleUse() {
        repeatingUse = !repeatingUse;
        repeatingAttack = false;
        actionCooldown = 0;
    }

    public void stop() {
        // “停止”同时清理持续动作和可能残留的玩家姿态。
        repeatingAttack = false;
        repeatingUse = false;
        player.stopUsingItem();
        player.setShiftKeyDown(false);
        player.setSprinting(false);
    }

    public boolean isRepeatingAttack() {
        return repeatingAttack;
    }

    public boolean isRepeatingUse() {
        return repeatingUse;
    }
}
