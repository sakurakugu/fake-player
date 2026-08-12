package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 地图前端提交的一批编辑意图，服务端仍负责全部业务校验。 */
public record ApplyChunkLoadEditsPayload(long expectedRevision, String dimension, List<Edit> edits)
    implements CustomPacketPayload {
    public static final int MAX_EDITS = 256;
    public static final int MAX_EDIT_CHUNKS = 4096;
    public static final Type<ApplyChunkLoadEditsPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "apply_chunk_load_edits"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyChunkLoadEditsPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ApplyChunkLoadEditsPayload::write, ApplyChunkLoadEditsPayload::new);

    public ApplyChunkLoadEditsPayload {
        edits = List.copyOf(edits);
    }

    private ApplyChunkLoadEditsPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarLong(), buffer.readUtf(256), readEdits(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(expectedRevision);
        buffer.writeUtf(dimension, 256);
        buffer.writeVarInt(edits.size());
        edits.forEach(edit -> edit.write(buffer));
    }

    private static List<Edit> readEdits(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 1 || size > MAX_EDITS) throw new IllegalArgumentException("批量编辑数量非法");
        List<Edit> values = new ArrayList<>(size);
        int chunks = 0;
        for (int index = 0; index < size; index++) {
            Edit edit = new Edit(buffer);
            chunks = Math.addExact(chunks, edit.chunks().size());
            if (chunks > MAX_EDIT_CHUNKS) throw new IllegalArgumentException("单次编辑区块数超限");
            values.add(edit);
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action {
        CREATE_REGION, ADD_CHUNKS, REMOVE_CHUNKS, SET_MODE, SET_ENABLED, DELETE_REGION, SET_FAKE_POLICY
    }

    /** 不同动作只读取自己需要的字段，其余字段使用稳定占位值。 */
    public record Edit(Action action, UUID targetId, String name, ManualLoadMode mode, boolean enabled,
                       int simulationDistance, List<Long> chunks) {
        public Edit {
            chunks = List.copyOf(chunks);
        }

        private Edit(RegistryFriendlyByteBuf buffer) {
            this(buffer.readEnum(Action.class), buffer.readUUID(), buffer.readUtf(32),
                buffer.readEnum(ManualLoadMode.class), buffer.readBoolean(), buffer.readVarInt(), readChunks(buffer));
            if (simulationDistance < 0 || simulationDistance > 32) throw new IllegalArgumentException("模拟距离非法");
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(action); buffer.writeUUID(targetId); buffer.writeUtf(name, 32);
            buffer.writeEnum(mode); buffer.writeBoolean(enabled); buffer.writeVarInt(simulationDistance);
            buffer.writeVarInt(chunks.size()); chunks.forEach(buffer::writeLong);
        }

        private static List<Long> readChunks(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_EDIT_CHUNKS) throw new IllegalArgumentException("编辑区块数量非法");
            List<Long> values = new ArrayList<>(size);
            java.util.HashSet<Long> unique = new java.util.HashSet<>();
            for (int index = 0; index < size; index++) {
                long value = buffer.readLong();
                if (!unique.add(value)) throw new IllegalArgumentException("编辑区块重复");
                values.add(value);
            }
            return values;
        }
    }
}
