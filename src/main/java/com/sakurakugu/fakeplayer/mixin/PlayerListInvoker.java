package com.sakurakugu.fakeplayer.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露原版玩家列表的受保护存档方法，供附身恢复后覆盖玩家数据。 */
@Mixin(PlayerList.class)
public interface PlayerListInvoker {
    @Invoker("save")
    void fakeplayer$save(ServerPlayer player);
}
