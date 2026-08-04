package com.sakurakugu.fakeplayer.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.entity.FakePlayerActions;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** 保存驻留清单、用户预设和分组；三者只共享存储文件，不共享生命周期语义。 */
public final class FakePlayerSavedData extends SavedData {
    private static final Codec<FakePlayerActions.RepeatMode> REPEAT_MODE_CODEC =
        enumCodec(FakePlayerActions.RepeatMode.class);
    private static final Codec<FakePlayerActions.ScheduledAction> ACTION_CODEC =
        enumCodec(FakePlayerActions.ScheduledAction.class);

    private static final Codec<FakePlayerActions.ScheduledState> SCHEDULE_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ACTION_CODEC.fieldOf("action").forGetter(FakePlayerActions.ScheduledState::action),
            REPEAT_MODE_CODEC.fieldOf("mode").forGetter(FakePlayerActions.ScheduledState::mode),
            Codec.INT.fieldOf("interval").forGetter(FakePlayerActions.ScheduledState::interval),
            Codec.INT.optionalFieldOf("remaining", 0).forGetter(FakePlayerActions.ScheduledState::remaining)
        ).apply(instance, FakePlayerActions.ScheduledState::new)
    );
    private static final Codec<FakePlayerActions.DropState> DROP_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("slot").forGetter(FakePlayerActions.DropState::slot),
            Codec.BOOL.fieldOf("whole_stack").forGetter(FakePlayerActions.DropState::wholeStack),
            Codec.BOOL.fieldOf("percentage").forGetter(FakePlayerActions.DropState::percentage),
            Codec.INT.fieldOf("amount").forGetter(FakePlayerActions.DropState::amount)
        ).apply(instance, FakePlayerActions.DropState::new)
    );
    private static final Codec<FakePlayerActions.State> ACTION_STATE_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            SCHEDULE_CODEC.listOf().optionalFieldOf("schedules", List.of())
                .forGetter(FakePlayerActions.State::schedules),
            Codec.FLOAT.optionalFieldOf("forward", 0.0F).forGetter(FakePlayerActions.State::forwardInput),
            Codec.FLOAT.optionalFieldOf("strafe", 0.0F).forGetter(FakePlayerActions.State::strafeInput),
            DROP_CODEC.optionalFieldOf("drop").forGetter(FakePlayerActions.State::drop),
            Codec.BOOL.optionalFieldOf("sneaking", false).forGetter(FakePlayerActions.State::sneaking),
            Codec.BOOL.optionalFieldOf("sprinting", false).forGetter(FakePlayerActions.State::sprinting)
        ).apply(instance, FakePlayerActions.State::new)
    );

    public static final Codec<Resident> RESIDENT_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(Resident::uuid),
            Codec.STRING.fieldOf("name").forGetter(Resident::name)
        ).apply(instance, Resident::new)
    );
    public static final Codec<PlayerSnapshot> PLAYER_SNAPSHOT_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerSnapshot::uuid),
            Codec.STRING.fieldOf("name").forGetter(PlayerSnapshot::name),
            CompoundTag.CODEC.fieldOf("player_data").forGetter(PlayerSnapshot::playerData),
            ACTION_STATE_CODEC.optionalFieldOf("actions", FakePlayerActions.State.EMPTY)
                .forGetter(PlayerSnapshot::actions)
        ).apply(instance, PlayerSnapshot::new)
    );
    public static final Codec<Preset> PRESET_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(Preset::id),
            Codec.STRING.optionalFieldOf("description", "").forGetter(Preset::description),
            PLAYER_SNAPSHOT_CODEC.fieldOf("player").forGetter(Preset::player)
        ).apply(instance, Preset::new)
    );
    public static final Codec<Group> GROUP_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(Group::id),
            Codec.STRING.listOf().optionalFieldOf("presets", List.of()).forGetter(Group::presetIds)
        ).apply(instance, Group::new)
    );
    public static final Codec<FakePlayerSavedData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            RESIDENT_CODEC.listOf().optionalFieldOf("residents", List.of())
                .forGetter(data -> List.copyOf(data.residents.values())),
            PRESET_CODEC.listOf().optionalFieldOf("presets", List.of())
                .forGetter(data -> List.copyOf(data.presets.values())),
            GROUP_CODEC.listOf().optionalFieldOf("groups", List.of())
                .forGetter(data -> List.copyOf(data.groups.values()))
        ).apply(instance, FakePlayerSavedData::new)
    );
    public static final SavedDataType<FakePlayerSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "fake_players"),
        FakePlayerSavedData::new,
        CODEC,
        null
    );

    private final Map<UUID, Resident> residents = new LinkedHashMap<>();
    private final Map<String, Preset> presets = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();

    public FakePlayerSavedData() {
    }

    private FakePlayerSavedData(
        List<Resident> residents,
        List<Preset> presets,
        List<Group> groups
    ) {
        residents.forEach(value -> this.residents.put(value.uuid(), value));
        presets.forEach(value -> this.presets.put(key(value.id()), value));
        groups.forEach(value -> this.groups.put(key(value.id()), value));
    }

    public List<Resident> residents() {
        return List.copyOf(residents.values());
    }

    public void putResident(Resident resident) {
        Resident previous = residents.put(resident.uuid(), resident);
        if (!resident.equals(previous)) {
            setDirty();
        }
    }

    public void removeResident(UUID uuid) {
        if (residents.remove(uuid) != null) {
            setDirty();
        }
    }

    public void clearResidents() {
        if (!residents.isEmpty()) {
            residents.clear();
            setDirty();
        }
    }

    public Collection<Preset> presets() {
        return List.copyOf(presets.values());
    }

    public Optional<Preset> preset(String id) {
        return Optional.ofNullable(presets.get(key(id)));
    }

    public void putPreset(Preset preset) {
        presets.put(key(preset.id()), preset);
        setDirty();
    }

    public boolean removePreset(String id) {
        String normalized = key(id);
        if (presets.remove(normalized) == null) {
            return false;
        }
        groups.replaceAll((groupId, group) -> new Group(group.id(), group.presetIds().stream()
            .filter(value -> !key(value).equals(normalized)).toList()));
        setDirty();
        return true;
    }

    public Collection<Group> groups() {
        return List.copyOf(groups.values());
    }

    public Optional<Group> group(String id) {
        return Optional.ofNullable(groups.get(key(id)));
    }

    public boolean createGroup(String id) {
        if (groups.containsKey(key(id))) {
            return false;
        }
        groups.put(key(id), new Group(id, List.of()));
        setDirty();
        return true;
    }

    public boolean removeGroup(String id) {
        if (groups.remove(key(id)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean addToGroup(String groupId, String presetId) {
        Group group = groups.get(key(groupId));
        Preset preset = presets.get(key(presetId));
        if (group == null || preset == null || group.presetIds().stream().anyMatch(value -> key(value).equals(key(presetId)))) {
            return false;
        }
        List<String> members = new ArrayList<>(group.presetIds());
        members.add(preset.id());
        groups.put(key(groupId), new Group(group.id(), members));
        setDirty();
        return true;
    }

    private static String key(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Enum.valueOf(type, value));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "未知的 " + type.getSimpleName() + "：" + value);
            }
        }, Enum::name);
    }

    /** 驻留记录只用于服务器重启后按名称和 UUID 恢复假人，不保存持续动作。 */
    public record Resident(
        UUID uuid,
        String name
    ) {
        public static Resident from(FakeServerPlayer player) {
            return new Resident(player.getUUID(), player.getGameProfile().name());
        }
    }

    public record PlayerSnapshot(
        UUID uuid,
        String name,
        CompoundTag playerData,
        FakePlayerActions.State actions
    ) {
        public PlayerSnapshot {
            playerData = playerData.copy();
        }

        public static PlayerSnapshot from(FakeServerPlayer player, boolean saveActions) {
            return new PlayerSnapshot(
                player.getUUID(),
                player.getGameProfile().name(),
                FakePlayerPersistence.snapshot(player),
                saveActions ? player.actions().snapshot() : FakePlayerActions.State.EMPTY
            );
        }
    }

    public record Preset(String id, String description, PlayerSnapshot player) {
    }

    public record Group(String id, List<String> presetIds) {
        public Group {
            presetIds = List.copyOf(presetIds);
        }
    }

}
