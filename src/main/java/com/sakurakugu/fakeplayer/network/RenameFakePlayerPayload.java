package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从假人物品栏提交名称修改请求。 */
public record RenameFakePlayerPayload(int containerId, String name) implements CustomPacketPayload {
    public static final Type<RenameFakePlayerPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "rename_fake_player")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameFakePlayerPayload> STREAM_CODEC =
        CustomPacketPayload.codec(RenameFakePlayerPayload::write, RenameFakePlayerPayload::new);

    private RenameFakePlayerPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(16));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(name, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
