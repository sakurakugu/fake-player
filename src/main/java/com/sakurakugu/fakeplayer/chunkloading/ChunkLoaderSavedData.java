package com.sakurakugu.fakeplayer.chunkloading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** 保存独立的区块加载点配置，不与假人驻留清单混用。 */
public final class ChunkLoaderSavedData extends SavedData {
    public static final Codec<Anchor> ANCHOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("uuid").forGetter(Anchor::uuid),
        Codec.STRING.fieldOf("name").forGetter(Anchor::name),
        Identifier.CODEC.fieldOf("dimension").forGetter(Anchor::dimension),
        BlockPos.CODEC.fieldOf("position").forGetter(Anchor::position),
        Codec.INT.fieldOf("radius").forGetter(Anchor::radius),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Anchor::enabled),
        Codec.BOOL.optionalFieldOf("ticking", false).forGetter(Anchor::ticking)
    ).apply(instance, Anchor::new));

    public static final Codec<ChunkLoaderSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ANCHOR_CODEC.listOf().optionalFieldOf("anchors", List.of())
            .forGetter(data -> List.copyOf(data.anchors.values()))
    ).apply(instance, ChunkLoaderSavedData::new));

    public static final SavedDataType<ChunkLoaderSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_loaders"),
        ChunkLoaderSavedData::new,
        CODEC,
        null
    );

    private final Map<String, Anchor> anchors = new LinkedHashMap<>();

    public ChunkLoaderSavedData() {
    }

    private ChunkLoaderSavedData(List<Anchor> anchors) {
        anchors.forEach(anchor -> this.anchors.put(key(anchor.name()), anchor));
    }

    public Collection<Anchor> anchors() {
        return List.copyOf(anchors.values());
    }

    public Optional<Anchor> anchor(String name) {
        return Optional.ofNullable(anchors.get(key(name)));
    }

    public boolean add(Anchor anchor) {
        if (anchors.putIfAbsent(key(anchor.name()), anchor) != null) {
            return false;
        }
        setDirty();
        return true;
    }

    public void put(Anchor anchor) {
        anchors.put(key(anchor.name()), anchor);
        setDirty();
    }

    public Optional<Anchor> remove(String name) {
        Anchor removed = anchors.remove(key(name));
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    /** 使用备份内容整体替换当前配置。 */
    public void replaceAll(Collection<Anchor> replacement) {
        anchors.clear();
        replacement.forEach(anchor -> anchors.put(key(anchor.name()), anchor));
        setDirty();
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public record Anchor(
        UUID uuid,
        String name,
        Identifier dimension,
        BlockPos position,
        int radius,
        boolean enabled,
        boolean ticking
    ) {
        public Anchor withEnabled(boolean value) {
            return new Anchor(uuid, name, dimension, position, radius, value, ticking);
        }

        public Anchor withSettings(int newRadius, boolean newTicking) {
            return new Anchor(uuid, name, dimension, position, newRadius, enabled, newTicking);
        }
    }
}
