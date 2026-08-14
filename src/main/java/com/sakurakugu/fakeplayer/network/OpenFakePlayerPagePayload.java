package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从地图底栏请求打开假人功能页面。 */
public record OpenFakePlayerPagePayload(Page page) implements CustomPacketPayload {
    public static final Type<OpenFakePlayerPagePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "open_fake_player_page")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFakePlayerPagePayload> STREAM_CODEC =
        CustomPacketPayload.codec(OpenFakePlayerPagePayload::write, OpenFakePlayerPagePayload::new);

    private OpenFakePlayerPagePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readEnum(Page.class));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(page);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Page {
        SPAWN,
        LIST,
        PRESETS
    }
}
