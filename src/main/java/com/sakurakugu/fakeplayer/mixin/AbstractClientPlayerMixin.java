package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.client.ClientPossession;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让活动身体和原地躯壳分别使用对方进入附身前的玩家外观。 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessionSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerSkin skin = ClientPossession.proxySkin((AbstractClientPlayer) (Object) this);
        if (skin != null) {
            callback.setReturnValue(skin);
        }
    }
}
