package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 提交区块加载点管理界面的操作。 */
public record ChunkLoaderActionPayload(int containerId, Action action, String name, int radius, boolean ticking)
    implements CustomPacketPayload {
    public static final Type<ChunkLoaderActionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_loader_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkLoaderActionPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkLoaderActionPayload::write, ChunkLoaderActionPayload::new);

    private ChunkLoaderActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readEnum(Action.class), buffer.readUtf(32),
            buffer.readVarInt(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeEnum(action);
        buffer.writeUtf(name, 32);
        buffer.writeVarInt(radius);
        buffer.writeBoolean(ticking);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ADD, CONFIGURE, ENABLE, DISABLE, REMOVE, BACKUP, RESTORE
    }
}
