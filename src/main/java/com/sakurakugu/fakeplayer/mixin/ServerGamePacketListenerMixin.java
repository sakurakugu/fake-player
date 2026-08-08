package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让假连接的传送立即在服务端生效，不等待不存在的客户端确认包。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    public abstract void resetPosition();

    @Inject(
        method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fakeplayer$completeFakeTeleport(
        PositionMoveRotation destination,
        Set<Relative> relatives,
        CallbackInfo callbackInfo
    ) {
        if (!(player instanceof FakeServerPlayer)) {
            return;
        }
        player.teleportSetPosition(destination, relatives);
        resetPosition();
        if (player.level().getPlayerByUUID(player.getUUID()) != null) {
            // 首次登录传送发生在玩家加入世界列表之前，此时不能访问尚未建立的距离管理记录。
            player.level().getChunkSource().move(player);
        }
        callbackInfo.cancel();
    }
}
