package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从假人控制界面一次提交完整视角，避免俯仰角和偏航角产生两次同步。 */
public record FakePlayerViewRotationPayload(int containerId, int pitch, int yaw)
    implements CustomPacketPayload {
    public static final Type<FakePlayerViewRotationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "fake_player_view_rotation")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerViewRotationPayload> STREAM_CODEC =
        CustomPacketPayload.codec(FakePlayerViewRotationPayload::write, FakePlayerViewRotationPayload::new);

    private FakePlayerViewRotationPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(pitch);
        buffer.writeVarInt(yaw);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
