package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.client.ClientPossession;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在实体渲染器生成名牌渲染状态时应用附身双方的名称映射。 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameMixin {
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessionNameTag(
        Entity entity,
        CallbackInfoReturnable<Component> callback
    ) {
        if (entity instanceof AbstractClientPlayer player) {
            Component name = ClientPossession.proxyDisplayName(player);
            if (name != null) {
                callback.setReturnValue(name);
            }
        }
    }
}
