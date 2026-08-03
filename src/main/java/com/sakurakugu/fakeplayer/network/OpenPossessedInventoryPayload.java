package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 附身期间请求打开当前假人的背包。 */
public record OpenPossessedInventoryPayload() implements CustomPacketPayload {
    public static final Type<OpenPossessedInventoryPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "open_possessed_inventory")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPossessedInventoryPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OpenPossessedInventoryPayload::write, OpenPossessedInventoryPayload::new);

    private OpenPossessedInventoryPayload(RegistryFriendlyByteBuf ignored) {
        this();
    }

    private void write(RegistryFriendlyByteBuf ignored) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
