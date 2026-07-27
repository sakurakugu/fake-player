package com.sakurakugu.fakeplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Group;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Preset;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Resident;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** 管理可手动加载的假人预设和预设分组。 */
public final class BotCommand {
    private BotCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot")
            .requires(FakePlayerConfig::canUseCommands)
            .then(Commands.literal("list").executes(BotCommand::listPresets))
            .then(Commands.literal("add")
                .then(Commands.argument("preset", StringArgumentType.word())
                    .then(fakeArgument()
                        .executes(context -> addPreset(context, ""))
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                            .executes(context -> addPreset(
                                context, StringArgumentType.getString(context, "description")))))))
            .then(Commands.literal("load")
                .then(presetArgument().executes(BotCommand::loadPreset)))
            .then(Commands.literal("remove")
                .then(presetArgument().executes(BotCommand::removePreset)))
            .then(groupCommand());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groupCommand() {
        return Commands.literal("group")
            .then(Commands.literal("create")
                .then(Commands.argument("group", StringArgumentType.word()).executes(BotCommand::createGroup)))
            .then(Commands.literal("list").executes(BotCommand::listGroups))
            .then(Commands.literal("remove")
                .then(groupArgument().executes(BotCommand::removeGroup)))
            .then(Commands.literal("add")
                .then(groupArgument().then(presetArgument().executes(BotCommand::addToGroup))))
            .then(Commands.literal("load")
                .then(groupArgument().executes(context -> loadGroup(context, false))))
            .then(Commands.literal("unload")
                .then(groupArgument().executes(context -> loadGroup(context, true))))
            .then(Commands.literal("info")
                .then(groupArgument().executes(BotCommand::groupInfo)));
    }

    private static int addPreset(CommandContext<CommandSourceStack> context, String description) {
        String id = StringArgumentType.getString(context, "preset");
        String playerName = StringArgumentType.getString(context, "player");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), playerName);
        if (fake == null) {
            return failure(context, "commands.fakeplayer.not_found", playerName);
        }
        FakePlayerSavedData data = data(context);
        data.putPreset(new Preset(id, description, Resident.from(fake, true)));
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.preset_saved", id, playerName), true);
        return 1;
    }

    private static int listPresets(CommandContext<CommandSourceStack> context) {
        String values = data(context).presets().stream()
            .sorted(Comparator.comparing(Preset::id, String.CASE_INSENSITIVE_ORDER))
            .map(preset -> preset.description().isBlank()
                ? preset.id()
                : preset.id() + " (" + preset.description() + ")")
            .reduce((left, right) -> left + ", " + right)
            .orElse(Component.translatable("commands.fakeplayer.none").getString());
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.preset_list", values), false);
        return 1;
    }

    private static int loadPreset(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "preset");
        Preset preset = data(context).preset(id).orElse(null);
        if (preset == null) {
            return failure(context, "commands.fakeplayer.bot.preset_not_found", id);
        }
        var result = FakePlayerPersistence.loadPreset(context.getSource().getServer(), preset);
        if (!result.successful()) {
            return failure(context, "commands.fakeplayer.bot.load_failed", id, result.reason());
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.preset_loaded", id, preset.player().name()), true);
        return 1;
    }

    private static int removePreset(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "preset");
        if (!data(context).removePreset(id)) {
            return failure(context, "commands.fakeplayer.bot.preset_not_found", id);
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.preset_removed", id), true);
        return 1;
    }

    private static int createGroup(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "group");
        if (!data(context).createGroup(id)) {
            return failure(context, "commands.fakeplayer.bot.group_exists", id);
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_created", id), true);
        return 1;
    }

    private static int listGroups(CommandContext<CommandSourceStack> context) {
        String values = data(context).groups().stream()
            .sorted(Comparator.comparing(Group::id, String.CASE_INSENSITIVE_ORDER))
            .map(group -> group.id() + " [" + group.presetIds().size() + "]")
            .reduce((left, right) -> left + ", " + right)
            .orElse(Component.translatable("commands.fakeplayer.none").getString());
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_list", values), false);
        return 1;
    }

    private static int removeGroup(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "group");
        if (!data(context).removeGroup(id)) {
            return failure(context, "commands.fakeplayer.bot.group_not_found", id);
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_removed", id), true);
        return 1;
    }

    private static int addToGroup(CommandContext<CommandSourceStack> context) {
        String group = StringArgumentType.getString(context, "group");
        String preset = StringArgumentType.getString(context, "preset");
        FakePlayerSavedData data = data(context);
        if (data.group(group).isEmpty()) {
            return failure(context, "commands.fakeplayer.bot.group_not_found", group);
        }
        if (data.preset(preset).isEmpty()) {
            return failure(context, "commands.fakeplayer.bot.preset_not_found", preset);
        }
        if (!data.addToGroup(group, preset)) {
            return failure(context, "commands.fakeplayer.bot.group_member_exists", preset, group);
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_member_added", preset, group), true);
        return 1;
    }

    private static int loadGroup(CommandContext<CommandSourceStack> context, boolean unload) {
        String id = StringArgumentType.getString(context, "group");
        FakePlayerSavedData data = data(context);
        Group group = data.group(id).orElse(null);
        if (group == null) {
            return failure(context, "commands.fakeplayer.bot.group_not_found", id);
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
                FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), preset.player().name());
                if (fake != null && fake.getUUID().equals(preset.player().uuid())) {
                    FakePlayerManager.remove(fake);
                    succeeded++;
                } else {
                    failed++;
                }
            } else if (FakePlayerPersistence.loadPreset(context.getSource().getServer(), preset).successful()) {
                succeeded++;
            } else {
                failed++;
            }
        }
        int successCount = succeeded;
        int failureCount = failed;
        String key = unload ? "commands.fakeplayer.bot.group_unloaded" : "commands.fakeplayer.bot.group_loaded";
        context.getSource().sendSuccess(() -> Component.translatable(key, id, successCount, failureCount), true);
        return succeeded;
    }

    private static int groupInfo(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "group");
        Group group = data(context).group(id).orElse(null);
        if (group == null) {
            return failure(context, "commands.fakeplayer.bot.group_not_found", id);
        }
        String members = group.presetIds().isEmpty()
            ? Component.translatable("commands.fakeplayer.none").getString()
            : String.join(", ", group.presetIds());
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_info", group.id(), members), false);
        return 1;
    }

    private static FakePlayerSavedData data(CommandContext<CommandSourceStack> context) {
        return FakePlayerPersistence.data(context.getSource().getServer());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> fakeArgument() {
        return Commands.argument("player", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                FakePlayerManager.all(context.getSource().getServer()).stream()
                    .map(player -> player.getGameProfile().name()), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> presetArgument() {
        return Commands.argument("preset", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                data(context).presets().stream().map(Preset::id), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> groupArgument() {
        return Commands.argument("group", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                data(context).groups().stream().map(Group::id), builder));
    }

    private static int failure(CommandContext<CommandSourceStack> context, String key, Object... arguments) {
        context.getSource().sendFailure(Component.translatable(key, arguments));
        return 0;
    }
}
