package com.sakurakugu.fakeplayer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.sakurakugu.fakeplayer.entity.FakePlayerActions;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FakePlayerSavedDataTest {
    @Test
    void residentsAreReplacedByUuidAndReturnedAsSnapshot() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        UUID uuid = UUID.randomUUID();
        FakePlayerSavedData.Resident first = resident(uuid, "Bot");
        FakePlayerSavedData.Resident replacement = resident(uuid, "Renamed");

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
        FakePlayerSavedData.Resident first = resident(UUID.randomUUID(), "One");
        FakePlayerSavedData.Resident second = resident(UUID.randomUUID(), "Two");
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
    void renamingPlayerUpdatesResidentAndEveryPresetWithSameUuid() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        FakePlayerSavedData.Preset first = preset("Miner");
        FakePlayerSavedData.PlayerSnapshot original = first.player();
        FakePlayerSavedData.Preset second = new FakePlayerSavedData.Preset(
            "MinerBackup",
            "backup",
            new FakePlayerSavedData.PlayerSnapshot(
                original.uuid(), original.name(), original.playerData(), original.actions(), original.automation())
        );
        FakePlayerSavedData.Preset unrelated = preset("Builder");
        data.putResident(resident(original.uuid(), original.name()));
        data.putPreset(first);
        data.putPreset(second);
        data.putPreset(unrelated);

        UUID renamedUuid = UUID.randomUUID();
        data.migratePlayer(original.uuid(), renamedUuid, "RenamedBot");

        assertEquals("RenamedBot", data.residents().getFirst().name());
        assertEquals(renamedUuid, data.residents().getFirst().uuid());
        assertEquals("RenamedBot", data.preset("Miner").orElseThrow().player().name());
        assertEquals(renamedUuid, data.preset("Miner").orElseThrow().player().uuid());
        assertFalse(data.preset("Miner").orElseThrow().player().playerData().contains("UUID"));
        assertEquals("RenamedBot", data.preset("MinerBackup").orElseThrow().player().name());
        assertEquals(unrelated, data.preset("Builder").orElseThrow());
        assertTrue(data.isDirty());
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
    void groupMemberCanBeRemovedCaseInsensitively() {
        FakePlayerSavedData data = new FakePlayerSavedData();
        data.putPreset(preset("Miner"));
        data.createGroup("Workers");
        data.addToGroup("Workers", "Miner");

        assertTrue(data.removeFromGroup("workers", "mINER"));
        assertTrue(data.group("Workers").orElseThrow().presetIds().isEmpty());
        assertFalse(data.removeFromGroup("Workers", "Miner"));
        assertFalse(data.removeFromGroup("Missing", "Miner"));
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
        data.putResident(resident(preset.player().uuid(), preset.player().name()));
        data.putPreset(preset);
        data.createGroup("Workers");
        data.addToGroup("Workers", "Miner");
        data.migratePlayer(preset.player().uuid(), UUID.randomUUID(), "RenamedBot");

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
            java.util.Optional.of(new FakePlayerActions.DropState(4, false, false, 12)),
            true,
            false,
            true
        );
        return new FakePlayerSavedData.Preset(
            id,
            "test preset",
            new FakePlayerSavedData.PlayerSnapshot(
                UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "TestBot",
                playerData(id),
                actions,
                com.sakurakugu.fakeplayer.automation.FakePlayerAutomation.AutomationState.DEFAULT
            )
        );
    }

    private static FakePlayerSavedData.Resident resident(UUID uuid, String name) {
        return new FakePlayerSavedData.Resident(
            uuid,
            name,
            com.sakurakugu.fakeplayer.automation.FakePlayerAutomation.AutomationState.DEFAULT
        );
    }

    private static CompoundTag playerData(String marker) {
        CompoundTag data = new CompoundTag();
        data.putString("Dimension", "minecraft:overworld");
        data.putString("marker", marker);
        data.putString("UUID", "old identity");
        return data;
    }

}
