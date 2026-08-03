package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.client.ClientPossession;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 将帽子、袖子、裤腿和披风等皮肤层的显示位也纳入外观代理。 */
@Mixin(Avatar.class)
public abstract class PlayerAppearanceMixin {
    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessionModelParts(
        PlayerModelPart part,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if ((Object) this instanceof AbstractClientPlayer player) {
            Boolean shown = ClientPossession.proxyModelPart(player, part);
            if (shown != null) {
                callback.setReturnValue(shown);
            }
        }
    }
}
