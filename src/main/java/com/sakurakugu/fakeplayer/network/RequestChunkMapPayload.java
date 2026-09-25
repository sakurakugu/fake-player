package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 请求区块加载点快照，并可指定首次打开地图时显示的视图。
 *
 * <p>{@code knownRevision} + {@code knownDimension} 说明客户端手上已有的区域数据：
 * 服务端据此判断能否跳过区块列表的序列化（快照里最大的一块）。
 * 客户端没有快照时传 {@link #NO_REVISION} 与空维度。
 */
public record RequestChunkMapPayload(boolean openScreen, boolean openManagement, boolean openSettings,
                                     long knownRevision, String knownDimension)
    implements CustomPacketPayload {
    /** 客户端还没有任何区域数据。 */
    public static final long NO_REVISION = Long.MIN_VALUE;
    public static final Type<RequestChunkMapPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "request_chunk_map")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestChunkMapPayload> STREAM_CODEC =
        CustomPacketPayload.codec(RequestChunkMapPayload::write, RequestChunkMapPayload::new);

    public RequestChunkMapPayload(boolean openScreen, boolean openManagement, boolean openSettings) {
        this(openScreen, openManagement, openSettings, NO_REVISION, "");
    }

    private RequestChunkMapPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
            buffer.readVarLong(), buffer.readUtf(256));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(openScreen);
        buffer.writeBoolean(openManagement);
        buffer.writeBoolean(openSettings);
        buffer.writeVarLong(knownRevision);
        buffer.writeUtf(knownDimension, 256);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
