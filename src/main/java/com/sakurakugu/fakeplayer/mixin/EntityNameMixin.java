package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.client.ClientPossession;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让附身时的名称与当前代理身体保持一致。 */
@Mixin(Entity.class)
public abstract class EntityNameMixin {
    @Inject(method = "getScoreboardName", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessionScoreboardName(CallbackInfoReturnable<String> callback) {
        if ((Object) this instanceof AbstractClientPlayer player) {
            String name = ClientPossession.proxyScoreboardName(player);
            if (name != null) {
                callback.setReturnValue(name);
            }
        }
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessionDisplayName(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof AbstractClientPlayer player) {
            Component name = ClientPossession.proxyDisplayName(player);
            if (name != null) {
                callback.setReturnValue(name);
            }
        }
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessionName(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof AbstractClientPlayer player) {
            Component name = ClientPossession.proxyName(player);
            if (name != null) {
                callback.setReturnValue(name);
            }
        }
    }
}
