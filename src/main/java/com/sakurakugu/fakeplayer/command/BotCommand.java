package com.sakurakugu.fakeplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Group;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.PlayerSnapshot;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData.Preset;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** 管理可手动加载的假人预设和预设分组。 */
public final class BotCommand {
    private static final int PAGE_SIZE = 8;

    private BotCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot")
            .requires(FakePlayerConfig::canUseCommands)
            .executes(BotCommand::openGui)
            .then(Commands.literal("list")
                .executes(context -> listPresets(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> listPresets(context, IntegerArgumentType.getInteger(context, "page")))))
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

    private static int openGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer == null) {
            return failure(context, "commands.fakeplayer.player_only");
        }
        FakePlayerMenuOpener.openBotManagement(viewer);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groupCommand() {
        return Commands.literal("group")
            .then(Commands.literal("create")
                .then(Commands.argument("group", StringArgumentType.word()).executes(BotCommand::createGroup)))
            .then(Commands.literal("list")
                .executes(context -> listGroups(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> listGroups(context, IntegerArgumentType.getInteger(context, "page")))))
            .then(Commands.literal("remove")
                .then(groupArgument()
                    .executes(BotCommand::removeGroup)
                    .then(presetArgument().executes(BotCommand::removeFromGroup))))
            .then(Commands.literal("add")
                .then(groupArgument().then(presetArgument().executes(BotCommand::addToGroup))))
            .then(Commands.literal("load")
                .then(groupArgument().executes(context -> loadGroup(context, false))))
            .then(Commands.literal("unload")
                .then(groupArgument().executes(context -> loadGroup(context, true))))
            .then(Commands.literal("info")
                .then(groupArgument()
                    .executes(context -> groupInfo(context, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> groupInfo(context, IntegerArgumentType.getInteger(context, "page"))))));
    }

    private static int addPreset(CommandContext<CommandSourceStack> context, String description) {
        String id = StringArgumentType.getString(context, "preset");
        String playerName = StringArgumentType.getString(context, "player");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), playerName);
        if (fake == null) {
            return failure(context, "commands.fakeplayer.not_found", playerName);
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            return failure(context, "gui.fakeplayer.possess_locked");
        }
        FakePlayerSavedData data = data(context);
        data.putPreset(new Preset(id, description, PlayerSnapshot.from(fake, true)));
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.preset_saved", id, playerName), true);
        return 1;
    }

    private static int listPresets(CommandContext<CommandSourceStack> context, int page) {
        List<Preset> presets = data(context).presets().stream()
            .sorted(Comparator.comparing(Preset::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
        int pageCount = pageCount(presets.size());
        if (!validPage(context, page, pageCount)) {
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.preset_page", page, pageCount, presets.size())
                .withStyle(ChatFormatting.GOLD), false);
        if (presets.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.none"), false);
        }
        int start = (page - 1) * PAGE_SIZE;
        for (Preset preset : presets.subList(start, Math.min(start + PAGE_SIZE, presets.size()))) {
            context.getSource().sendSuccess(() -> presetEntry(preset), false);
        }
        sendNavigation(context, page, pageCount, "/bot list ");
        return 1;
    }

    private static Component presetEntry(Preset preset) {
        MutableComponent entry = Component.literal("▶ " + preset.id()).withStyle(ChatFormatting.AQUA);
        if (!preset.description().isBlank()) {
            entry.append(Component.literal(" — " + preset.description()).withStyle(ChatFormatting.GRAY));
        }
        return entry
            .append(actionButton(" ↑", ChatFormatting.GREEN, new ClickEvent.RunCommand("/bot load " + preset.id())))
            .append(actionButton(" ×", ChatFormatting.RED, new ClickEvent.SuggestCommand("/bot remove " + preset.id())));
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

    private static int listGroups(CommandContext<CommandSourceStack> context, int page) {
        List<Group> groups = data(context).groups().stream()
            .sorted(Comparator.comparing(Group::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
        int pageCount = pageCount(groups.size());
        if (!validPage(context, page, pageCount)) {
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_page", page, pageCount, groups.size())
                .withStyle(ChatFormatting.GOLD), false);
        if (groups.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.none"), false);
        }
        int start = (page - 1) * PAGE_SIZE;
        for (Group group : groups.subList(start, Math.min(start + PAGE_SIZE, groups.size()))) {
            context.getSource().sendSuccess(() -> groupEntry(group), false);
        }
        sendNavigation(context, page, pageCount, "/bot group list ");
        return 1;
    }

    private static Component groupEntry(Group group) {
        return Component.literal("▶ " + group.id() + " [" + group.presetIds().size() + "]")
            .withStyle(ChatFormatting.AQUA)
            .append(actionButton(" ↑", ChatFormatting.GREEN,
                new ClickEvent.RunCommand("/bot group load " + group.id())))
            .append(actionButton(" ↓", ChatFormatting.YELLOW,
                new ClickEvent.RunCommand("/bot group unload " + group.id())))
            .append(actionButton(" i", ChatFormatting.WHITE,
                new ClickEvent.RunCommand("/bot group info " + group.id())))
            .append(actionButton(" ×", ChatFormatting.RED,
                new ClickEvent.SuggestCommand("/bot group remove " + group.id())));
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

    private static int removeFromGroup(CommandContext<CommandSourceStack> context) {
        String group = StringArgumentType.getString(context, "group");
        String preset = StringArgumentType.getString(context, "preset");
        FakePlayerSavedData data = data(context);
        if (data.group(group).isEmpty()) {
            return failure(context, "commands.fakeplayer.bot.group_not_found", group);
        }
        if (!data.removeFromGroup(group, preset)) {
            return failure(context, "commands.fakeplayer.bot.group_member_not_found", preset, group);
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_member_removed", preset, group), true);
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

    private static int groupInfo(CommandContext<CommandSourceStack> context, int page) {
        String id = StringArgumentType.getString(context, "group");
        Group group = data(context).group(id).orElse(null);
        if (group == null) {
            return failure(context, "commands.fakeplayer.bot.group_not_found", id);
        }
        int pageCount = pageCount(group.presetIds().size());
        if (!validPage(context, page, pageCount)) {
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.bot.group_info_page",
                group.id(), page, pageCount, group.presetIds().size()).withStyle(ChatFormatting.GOLD), false);
        if (group.presetIds().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.none"), false);
        }
        int start = (page - 1) * PAGE_SIZE;
        for (String preset : group.presetIds().subList(start, Math.min(start + PAGE_SIZE, group.presetIds().size()))) {
            context.getSource().sendSuccess(() -> Component.literal("▶ " + preset).withStyle(ChatFormatting.AQUA)
                .append(actionButton(" ↑", ChatFormatting.GREEN,
                    new ClickEvent.RunCommand("/bot load " + preset)))
                .append(actionButton(" −", ChatFormatting.RED,
                    new ClickEvent.RunCommand("/bot group remove " + group.id() + " " + preset))), false);
        }
        sendNavigation(context, page, pageCount, "/bot group info " + group.id() + " ");
        return 1;
    }

    private static MutableComponent actionButton(String label, ChatFormatting color, ClickEvent clickEvent) {
        return Component.literal(label).withStyle(color).withStyle(style -> style.withClickEvent(clickEvent));
    }

    private static void sendNavigation(
        CommandContext<CommandSourceStack> context,
        int page,
        int pageCount,
        String commandPrefix
    ) {
        if (pageCount <= 1) {
            return;
        }
        MutableComponent navigation = Component.empty();
        if (page > 1) {
            navigation.append(actionButton("« ", ChatFormatting.GRAY,
                new ClickEvent.RunCommand(commandPrefix + (page - 1))));
        }
        navigation.append(Component.translatable("commands.fakeplayer.bot.page_navigation", page, pageCount));
        if (page < pageCount) {
            navigation.append(actionButton(" »", ChatFormatting.GRAY,
                new ClickEvent.RunCommand(commandPrefix + (page + 1))));
        }
        context.getSource().sendSuccess(() -> navigation, false);
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static boolean validPage(CommandContext<CommandSourceStack> context, int page, int pageCount) {
        if (page <= pageCount) {
            return true;
        }
        failure(context, "commands.fakeplayer.bot.page_not_found", page, pageCount);
        return false;
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
