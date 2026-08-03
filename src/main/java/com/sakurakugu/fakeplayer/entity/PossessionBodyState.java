package com.sakurakugu.fakeplayer.entity;

import com.sakurakugu.fakeplayer.mixin.FoodDataAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** 附身时允许在两个玩家实体之间交换的身体状态快照。 */
final class PossessionBodyState {
    private final Vec3 position;
    private final float yaw;
    private final float pitch;
    private final Vec3 velocity;
    private final List<ItemStack> inventory;
    private final int selectedSlot;
    private final float health;
    private final float absorption;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int foodTickTimer;
    private final int experienceLevel;
    private final float experienceProgress;
    private final int totalExperience;
    private final List<MobEffectInstance> effects;
    private final int air;
    private final int fireTicks;
    private final int frozenTicks;
    private final GameType gameType;
    private final Abilities.Packed abilities;
    private final Pose pose;
    private final boolean sneaking;
    private final boolean sprinting;
    private final boolean swimming;
    private final double fallDistance;

    private PossessionBodyState(ServerPlayer player) {
        position = player.position();
        yaw = player.getYRot();
        pitch = player.getXRot();
        velocity = player.getDeltaMovement();

        Inventory sourceInventory = player.getInventory();
        inventory = new ArrayList<>(sourceInventory.getContainerSize());
        for (int slot = 0; slot < sourceInventory.getContainerSize(); slot++) {
            inventory.add(sourceInventory.getItem(slot).copy());
        }
        selectedSlot = sourceInventory.getSelectedSlot();

        health = player.getHealth();
        absorption = player.getAbsorptionAmount();
        FoodData food = player.getFoodData();
        FoodDataAccessor foodAccessor = (FoodDataAccessor) food;
        foodLevel = food.getFoodLevel();
        saturation = food.getSaturationLevel();
        exhaustion = foodAccessor.fakeplayer$getExhaustionLevel();
        foodTickTimer = foodAccessor.fakeplayer$getTickTimer();
        experienceLevel = player.experienceLevel;
        experienceProgress = player.experienceProgress;
        totalExperience = player.totalExperience;
        effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        air = player.getAirSupply();
        fireTicks = player.getRemainingFireTicks();
        frozenTicks = player.getTicksFrozen();
        gameType = player.gameMode.getGameModeForPlayer();
        abilities = player.getAbilities().pack();
        pose = player.getPose();
        sneaking = player.isShiftKeyDown();
        sprinting = player.isSprinting();
        swimming = player.isSwimming();
        fallDistance = player.fallDistance;
    }

    public static PossessionBodyState capture(ServerPlayer player) {
        return new PossessionBodyState(player);
    }

    /** 应用快照时只写身体字段，不触碰身份、连接、统计、进度或末影箱。 */
    public void apply(ServerPlayer player) {
        player.stopUsingItem();
        player.connection.teleport(position.x, position.y, position.z, yaw, pitch);
        player.setDeltaMovement(velocity);

        Inventory destination = player.getInventory();
        if (destination.getContainerSize() != inventory.size()) {
            throw new IllegalStateException("玩家背包尺寸在附身交换期间发生变化");
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            destination.setItem(slot, inventory.get(slot).copy());
        }
        destination.setSelectedSlot(selectedSlot);
        destination.setChanged();

        player.setGameMode(gameType);
        player.getAbilities().apply(abilities);
        player.onUpdateAbilities();

        player.removeAllEffects();
        effects.forEach(effect -> player.addEffect(new MobEffectInstance(effect)));
        player.setHealth(health);
        player.setAbsorptionAmount(absorption);

        FoodData food = player.getFoodData();
        food.setFoodLevel(foodLevel);
        food.setSaturation(saturation);
        FoodDataAccessor foodAccessor = (FoodDataAccessor) food;
        foodAccessor.fakeplayer$setExhaustionLevel(exhaustion);
        foodAccessor.fakeplayer$setTickTimer(foodTickTimer);
        player.experienceLevel = experienceLevel;
        player.experienceProgress = experienceProgress;
        player.totalExperience = totalExperience;

        player.setAirSupply(air);
        player.setRemainingFireTicks(fireTicks);
        player.setTicksFrozen(frozenTicks);
        player.setPose(pose);
        player.setShiftKeyDown(sneaking);
        player.setSprinting(sprinting);
        player.setSwimming(swimming);
        player.fallDistance = fallDistance;

        player.inventoryMenu.broadcastFullState();
        player.containerMenu.broadcastFullState();
        player.connection.resetPosition();
    }
}
