package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端从区块地图请求打开区块加载点管理界面的无数据载荷。 */
public record OpenChunkLoaderMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenChunkLoaderMenuPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "open_chunk_loader_menu")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChunkLoaderMenuPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OpenChunkLoaderMenuPayload::write, OpenChunkLoaderMenuPayload::new);

    private OpenChunkLoaderMenuPayload(RegistryFriendlyByteBuf ignored) {
        this();
    }

    private void write(RegistryFriendlyByteBuf ignored) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
