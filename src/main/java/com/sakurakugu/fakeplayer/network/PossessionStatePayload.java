package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端通知客户端开始或结束附身。 */
public record PossessionStatePayload(int targetEntityId) implements CustomPacketPayload {
    public static final int NONE = -1;
    public static final Type<PossessionStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "possession_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PossessionStatePayload> STREAM_CODEC =
        CustomPacketPayload.codec(PossessionStatePayload::write, PossessionStatePayload::new);

    private PossessionStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(targetEntityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
