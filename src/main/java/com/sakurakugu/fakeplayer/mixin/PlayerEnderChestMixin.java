package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 附身者打开末影箱时代理到假人身份数据，不交换双方的末影箱对象。 */
@Mixin(Player.class)
public abstract class PlayerEnderChestMixin {
    @Inject(method = "getEnderChestInventory", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$usePossessedEnderChest(
        CallbackInfoReturnable<PlayerEnderChestContainer> callback
    ) {
        if ((Object) this instanceof ServerPlayer player) {
            PlayerEnderChestContainer enderChest = FakePlayerPossession.possessedEnderChest(player);
            if (enderChest != null) {
                callback.setReturnValue(enderChest);
            }
        }
    }
}
