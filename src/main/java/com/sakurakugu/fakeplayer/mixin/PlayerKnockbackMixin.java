package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 假玩家没有客户端接收速度包，受玩家攻击时必须保留服务端计算出的击退速度。 */
@Mixin(Player.class)
public abstract class PlayerKnockbackMixin {
    @Redirect(
        method = "causeExtraKnockback",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/entity/Entity;hurtMarked:Z",
            ordinal = 0
        )
    )
    private boolean fakeplayer$keepServerKnockback(Entity target) {
        return target.hurtMarked && !(target instanceof FakeServerPlayer);
    }
}
