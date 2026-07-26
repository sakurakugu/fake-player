package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端按键请求打开全局假人列表的无数据载荷。 */
public record OpenGlobalMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenGlobalMenuPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "open_global_menu")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGlobalMenuPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OpenGlobalMenuPayload::write, OpenGlobalMenuPayload::new);

    private OpenGlobalMenuPayload(RegistryFriendlyByteBuf ignored) {
        this();
    }

    private void write(RegistryFriendlyByteBuf ignored) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
