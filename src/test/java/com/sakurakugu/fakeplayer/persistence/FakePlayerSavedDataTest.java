package com.sakurakugu.fakeplayer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.sakurakugu.fakeplayer.entity.FakePlayerActions;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

class FakePlayerSavedDataTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void residentsAreReplacedByUuidAndReturnedAsSnapshot() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        UUID uuid = UUID.randomUUID();
        FakePlayerSavedData.Resident first = resident(uuid, "Bot", 1.0);
        FakePlayerSavedData.Resident replacement = resident(uuid, "Renamed", 2.0);

        data.putResident(first);
        List<FakePlayerSavedData.Resident> snapshot = data.residents();
        data.putResident(replacement);

        assertEquals(List.of(first), snapshot);
        assertEquals(List.of(replacement), data.residents());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertTrue(data.isDirty());
    }

    @Test
    void residentRemovalAndClearHandleMissingEntries() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        FakePlayerSavedData.Resident first = resident(UUID.randomUUID(), "One", 1.0);
        FakePlayerSavedData.Resident second = resident(UUID.randomUUID(), "Two", 2.0);
        data.putResident(first);
        data.putResident(second);

        data.removeResident(first.uuid());
        assertEquals(List.of(second), data.residents());

        data.clearResidents();
        assertTrue(data.residents().isEmpty());
        data.removeResident(UUID.randomUUID());
        data.clearResidents();
        assertTrue(data.residents().isEmpty());
    }

    @Test
    void presetsAndGroupsUseCaseInsensitiveIds() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        FakePlayerSavedData.Preset preset = preset("Miner");

        data.putPreset(preset);

        assertEquals(preset, data.preset("mInEr").orElseThrow());
        assertTrue(data.createGroup("Workers"));
        assertFalse(data.createGroup("workers"));
        assertTrue(data.addToGroup("WORKERS", "miner"));
        assertFalse(data.addToGroup("workers", "MINER"));
        assertEquals(List.of("Miner"), data.group("workers").orElseThrow().presetIds());
    }

    @Test
    void groupMembershipRequiresExistingGroupAndPreset() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        data.putPreset(preset("Miner"));

        assertFalse(data.addToGroup("missing", "Miner"));
        assertTrue(data.createGroup("Workers"));
        assertFalse(data.addToGroup("Workers", "missing"));
        assertTrue(data.group("Workers").orElseThrow().presetIds().isEmpty());
    }

    @Test
    void removingPresetCascadesToEveryGroup() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        data.putPreset(preset("Miner"));
        data.createGroup("Workers");
        data.createGroup("Backup");
        data.addToGroup("Workers", "Miner");
        data.addToGroup("Backup", "Miner");

        assertTrue(data.removePreset("mINER"));

        assertTrue(data.preset("Miner").isEmpty());
        assertTrue(data.group("workers").orElseThrow().presetIds().isEmpty());
        assertTrue(data.group("backup").orElseThrow().presetIds().isEmpty());
        assertFalse(data.removePreset("Miner"));
    }

    @Test
    void groupMembersAreDefensivelyCopied() {
        java.util.ArrayList<String> members = new java.util.ArrayList<>(List.of("Miner"));
        FakePlayerSavedData.Group group = new FakePlayerSavedData.Group("Workers", members);

        members.add("Builder");

        assertEquals(List.of("Miner"), group.presetIds());
        assertThrows(UnsupportedOperationException.class, () -> group.presetIds().add("Builder"));
    }

    @Test
    void codecRoundTripsResidentsPresetsGroupsAndActions() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        FakePlayerSavedData.Preset preset = preset("Miner");
        data.putResident(preset.player());
        data.putPreset(preset);
        data.createGroup("Workers");
        data.addToGroup("Workers", "Miner");

        var json = FakePlayerSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        FakePlayerSavedData decoded = FakePlayerSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(data.residents(), decoded.residents());
        assertEquals(data.presets(), decoded.presets());
        assertEquals(data.groups(), decoded.groups());
    }

    private static FakePlayerSavedData.Preset preset(String id) {
        FakePlayerActions.State actions = new FakePlayerActions.State(
            List.of(new FakePlayerActions.ScheduledState(
                FakePlayerActions.ScheduledAction.ATTACK,
                FakePlayerActions.RepeatMode.INTERVAL,
                10,
                3
            )),
            1.0F,
            -1.0F,
            java.util.Optional.of(new FakePlayerActions.DropState(4, true)),
            true,
            false
        );
        return new FakePlayerSavedData.Preset(
            id,
            "test preset",
            new FakePlayerSavedData.Resident(
                UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "TestBot",
                OVERWORLD,
                1.25,
                64.0,
                -3.5,
                90.0F,
                -10.0F,
                GameType.CREATIVE,
                true,
                actions
            )
        );
    }

    private static FakePlayerSavedData.Resident resident(UUID uuid, String name, double x) {
        return new FakePlayerSavedData.Resident(
            uuid,
            name,
            OVERWORLD,
            x,
            64.0,
            0.0,
            0.0F,
            0.0F,
            GameType.SURVIVAL,
            false,
            FakePlayerActions.State.EMPTY
        );
    }
}
