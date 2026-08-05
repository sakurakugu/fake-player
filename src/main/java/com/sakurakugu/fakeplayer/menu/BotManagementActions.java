package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.network.BotActionPayload;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Group;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.PlayerSnapshot;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Preset;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 在服务端执行预设管理界面的操作并返回最新快照。 */
public final class BotManagementActions {
    private BotManagementActions() {
    }

    public static void handle(ServerPlayer viewer, BotActionPayload payload) {
        boolean groupsPage = switch (payload.action()) {
            case SAVE_PRESET, LOAD_PRESET, REMOVE_PRESET -> false;
            default -> true;
        };
        switch (payload.action()) {
            case SAVE_PRESET -> savePreset(viewer, payload.first(), payload.second(), payload.third());
            case LOAD_PRESET -> loadPreset(viewer, payload.first());
            case REMOVE_PRESET -> removePreset(viewer, payload.first());
            case CREATE_GROUP -> createGroup(viewer, payload.first());
            case ADD_TO_GROUP -> addToGroup(viewer, payload.first(), payload.second());
            case LOAD_GROUP -> loadGroup(viewer, payload.first(), false);
            case UNLOAD_GROUP -> loadGroup(viewer, payload.first(), true);
            case REMOVE_GROUP -> removeGroup(viewer, payload.first());
        }
        FakePlayerMenuOpener.openBotManagement(viewer, groupsPage);
    }

    private static void savePreset(ServerPlayer viewer, String id, String playerName, String description) {
        if (!validId(id)) {
            failure(viewer, "gui.fakeplayer.bot.invalid_id");
            return;
        }
        FakeServerPlayer fake = FakePlayerManager.find(viewer.level().getServer(), playerName);
        if (fake == null) {
            failure(viewer, "commands.fakeplayer.not_found", playerName);
            return;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            failure(viewer, "gui.fakeplayer.possess_locked");
            return;
        }
        data(viewer).putPreset(new Preset(id, description, PlayerSnapshot.from(fake, true)));
        success(viewer, "commands.fakeplayer.bot.preset_saved", id, playerName);
    }

    private static void loadPreset(ServerPlayer viewer, String id) {
        Preset preset = data(viewer).preset(id).orElse(null);
        if (preset == null) {
            failure(viewer, "commands.fakeplayer.bot.preset_not_found", id);
            return;
        }
        var result = FakePlayerPersistence.loadPreset(viewer.level().getServer(), preset);
        if (!result.successful()) {
            failure(viewer, "commands.fakeplayer.bot.load_failed", id, result.reason());
            return;
        }
        success(viewer, "commands.fakeplayer.bot.preset_loaded", id, preset.player().name());
    }

    private static void removePreset(ServerPlayer viewer, String id) {
        if (!data(viewer).removePreset(id)) {
            failure(viewer, "commands.fakeplayer.bot.preset_not_found", id);
            return;
        }
        success(viewer, "commands.fakeplayer.bot.preset_removed", id);
    }

    private static void createGroup(ServerPlayer viewer, String id) {
        if (!validId(id)) {
            failure(viewer, "gui.fakeplayer.bot.invalid_id");
            return;
        }
        if (!data(viewer).createGroup(id)) {
            failure(viewer, "commands.fakeplayer.bot.group_exists", id);
            return;
        }
        success(viewer, "commands.fakeplayer.bot.group_created", id);
    }

    private static void addToGroup(ServerPlayer viewer, String groupId, String presetId) {
        FakePlayerSavedData data = data(viewer);
        if (data.group(groupId).isEmpty()) {
            failure(viewer, "commands.fakeplayer.bot.group_not_found", groupId);
            return;
        }
        if (data.preset(presetId).isEmpty()) {
            failure(viewer, "commands.fakeplayer.bot.preset_not_found", presetId);
            return;
        }
        if (!data.addToGroup(groupId, presetId)) {
            failure(viewer, "commands.fakeplayer.bot.group_member_exists", presetId, groupId);
            return;
        }
        success(viewer, "commands.fakeplayer.bot.group_member_added", presetId, groupId);
    }

    private static void loadGroup(ServerPlayer viewer, String id, boolean unload) {
        FakePlayerSavedData data = data(viewer);
        Group group = data.group(id).orElse(null);
        if (group == null) {
            failure(viewer, "commands.fakeplayer.bot.group_not_found", id);
            return;
        }
        int succeeded = 0;
        int failed = 0;
        for (String presetId : group.presetIds()) {
            Preset preset = data.preset(presetId).orElse(null);
            if (preset == null) {
                failed++;
                continue;
            }
            if (unload) {
                FakeServerPlayer fake = FakePlayerManager.find(viewer.level().getServer(), preset.player().name());
                if (fake != null && fake.getUUID().equals(preset.player().uuid())
                    && !FakePlayerPossession.isPossessed(fake)) {
                    FakePlayerManager.remove(fake);
                    succeeded++;
                } else {
                    failed++;
                }
            } else if (FakePlayerPersistence.loadPreset(viewer.level().getServer(), preset).successful()) {
                succeeded++;
            } else {
                failed++;
            }
        }
        success(viewer, unload ? "commands.fakeplayer.bot.group_unloaded" : "commands.fakeplayer.bot.group_loaded",
            id, succeeded, failed);
    }

    private static void removeGroup(ServerPlayer viewer, String id) {
        if (!data(viewer).removeGroup(id)) {
            failure(viewer, "commands.fakeplayer.bot.group_not_found", id);
            return;
        }
        success(viewer, "commands.fakeplayer.bot.group_removed", id);
    }

    private static boolean validId(String value) {
        return !value.isBlank() && value.length() <= 64 && value.chars().noneMatch(Character::isWhitespace);
    }

    private static FakePlayerSavedData data(ServerPlayer viewer) {
        return FakePlayerPersistence.data(viewer.level().getServer());
    }

    private static void success(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments));
    }

    private static void failure(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments).withColor(0xFF5555));
    }
}
