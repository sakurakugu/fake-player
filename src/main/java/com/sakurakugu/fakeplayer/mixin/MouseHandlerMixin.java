package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.client.ClientPossession;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让附身视角与鼠标移动逐帧同步，避免等待服务端实体包。 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void fakeplayer$copyPossessedRotation(double frameTime, CallbackInfo callback) {
        ClientPossession.copyViewRotation();
    }
}
