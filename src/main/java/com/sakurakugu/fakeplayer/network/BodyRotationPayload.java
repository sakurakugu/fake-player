package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 将服务端不会原生同步的玩家身体朝向发送给观察客户端。 */
public record BodyRotationPayload(int entityId, float yaw) implements CustomPacketPayload {
    public static final Type<BodyRotationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "body_rotation")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyRotationPayload> STREAM_CODEC =
        CustomPacketPayload.codec(BodyRotationPayload::write, BodyRotationPayload::new);

    private BodyRotationPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeFloat(yaw);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
