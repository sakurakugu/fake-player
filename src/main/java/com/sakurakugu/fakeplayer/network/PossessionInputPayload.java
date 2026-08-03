package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端逐刻同步给被附身假人的输入。 */
public record PossessionInputPayload(
    float forward,
    float strafe,
    float yaw,
    float pitch,
    boolean jump,
    boolean sneak,
    boolean sprint,
    boolean attack,
    boolean use
) implements CustomPacketPayload {
    public static final Type<PossessionInputPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "possession_input")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PossessionInputPayload> STREAM_CODEC =
        CustomPacketPayload.codec(PossessionInputPayload::write, PossessionInputPayload::new);

    private PossessionInputPayload(RegistryFriendlyByteBuf buffer) {
        this(
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
            buffer.readBoolean(), buffer.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeFloat(forward);
        buffer.writeFloat(strafe);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeBoolean(jump);
        buffer.writeBoolean(sneak);
        buffer.writeBoolean(sprint);
        buffer.writeBoolean(attack);
        buffer.writeBoolean(use);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
