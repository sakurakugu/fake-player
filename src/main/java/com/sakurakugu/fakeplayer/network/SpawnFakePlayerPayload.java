package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从全局菜单提交假人生成请求。 */
public record SpawnFakePlayerPayload(int containerId, String name) implements CustomPacketPayload {
    public static final Type<SpawnFakePlayerPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "spawn_fake_player")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnFakePlayerPayload> STREAM_CODEC =
        CustomPacketPayload.codec(SpawnFakePlayerPayload::write, SpawnFakePlayerPayload::new);

    private SpawnFakePlayerPayload(RegistryFriendlyByteBuf buffer) {
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
