package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原版浮漂首次进入咬钩状态时通知假玩家自动化器。 */
@Mixin(FishingHook.class)
abstract class FishingHookMixin {
    @Inject(
        method = "catchingFish",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V",
            ordinal = 1,
            shift = At.Shift.AFTER
        )
    )
    private void fakeplayer$onFishBite(BlockPos pos, CallbackInfo callbackInfo) {
        Entity owner = ((FishingHook) (Object) this).getOwner();
        if (owner instanceof FakeServerPlayer fakePlayer) {
            fakePlayer.automation().onFishBite((FishingHook) (Object) this);
        }
    }
}
