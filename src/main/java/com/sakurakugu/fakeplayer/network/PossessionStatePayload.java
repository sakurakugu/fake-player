package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端同步操作者与躯壳之间的客户端外观代理关系。 */
public record PossessionStatePayload(int operatorEntityId, int targetEntityId) implements CustomPacketPayload {
    public static final int NONE = -1;
    public static final Type<PossessionStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "possession_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PossessionStatePayload> STREAM_CODEC =
        CustomPacketPayload.codec(PossessionStatePayload::write, PossessionStatePayload::new);

    public static PossessionStatePayload started(int operatorEntityId, int targetEntityId) {
        return new PossessionStatePayload(operatorEntityId, targetEntityId);
    }

    public static PossessionStatePayload stopped(int operatorEntityId) {
        return new PossessionStatePayload(operatorEntityId, NONE);
    }

    private PossessionStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(operatorEntityId);
        buffer.writeVarInt(targetEntityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
