package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 请求区块加载点快照；openScreen 为真时客户端收到后打开地图。 */
public record RequestChunkMapPayload(boolean openScreen) implements CustomPacketPayload {
    public static final Type<RequestChunkMapPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "request_chunk_map")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestChunkMapPayload> STREAM_CODEC =
        CustomPacketPayload.codec(RequestChunkMapPayload::write, RequestChunkMapPayload::new);

    private RequestChunkMapPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(openScreen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
