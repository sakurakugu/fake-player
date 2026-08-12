package com.sakurakugu.fakeplayer.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从假人控制界面提交模拟加载策略。 */
public record FakePlayerSimulationPayload(int containerId, boolean enabled, int distance)
    implements CustomPacketPayload {
    public static final Type<FakePlayerSimulationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath("fakeplayer", "fake_player_simulation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerSimulationPayload> STREAM_CODEC =
        CustomPacketPayload.codec(FakePlayerSimulationPayload::write, FakePlayerSimulationPayload::new);

    private FakePlayerSimulationPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeBoolean(enabled);
        buffer.writeVarInt(distance);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
