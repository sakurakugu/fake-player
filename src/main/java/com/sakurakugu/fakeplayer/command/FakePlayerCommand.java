package com.sakurakugu.fakeplayer.command;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 注册并处理 {@code /fakeplayer} 与 {@code /player} 命令的各个分支。 */
public final class FakePlayerCommand {
    private FakePlayerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 同时保留“/fakeplayer 名称”和“/fakeplayer spawn 名称”两种生成写法。
        dispatcher.register(
            Commands.literal("fakeplayer")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> spawn(context, nextName(context)))
                .then(Commands.literal("spawn")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> spawn(context, StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("kill")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            FakePlayerManager.all(context.getSource().getServer()).stream()
                                .map(player -> player.getGameProfile().name()),
                            builder
                        ))
                        .executes(FakePlayerCommand::kill)))
                .then(Commands.literal("list").executes(FakePlayerCommand::list))
                .then(guiCommand("gui"))
                .then(guiCommand("setting"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> spawn(context, StringArgumentType.getString(context, "name"))))
        );

        // 提供 Carpet 风格的“名字在前、操作在后”命令顺序。
        dispatcher.register(
            Commands.literal("player")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        context.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(player -> player.getGameProfile().name()),
                        builder
                    ))
                    .then(Commands.literal("spawn")
                        .executes(context -> spawn(context, StringArgumentType.getString(context, "name"))))
                    .then(Commands.literal("kill")
                        .executes(FakePlayerCommand::kill))
                    .then(Commands.literal("shadow")
                        .executes(FakePlayerCommand::shadow))
                    .then(action("attack", "gui.fakeplayer.attack", fake -> fake.actions().toggleAttack()))
                    .then(action("use", "gui.fakeplayer.use", fake -> fake.actions().toggleUse()))
                    .then(action("jump", "gui.fakeplayer.jump", fake -> fake.actions().jump()))
                    .then(action("stop", "gui.fakeplayer.stop", fake -> fake.actions().stop()))
                    .then(action("turn_left", "gui.fakeplayer.turn_left", fake -> fake.actions().turn(-45.0F)))
                    .then(action("turn_right", "gui.fakeplayer.turn_right", fake -> fake.actions().turn(45.0F)))
                    .then(action("sneak", "gui.fakeplayer.sneak", fake -> fake.actions().toggleSneak())))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(
        String name,
        String translationKey,
        java.util.function.Consumer<FakeServerPlayer> action
    ) {
        return Commands.literal(name).executes(context -> runAction(context, translationKey, action));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> guiCommand(String name) {
        return Commands.literal(name)
            .executes(FakePlayerCommand::openGui)
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    FakePlayerManager.all(context.getSource().getServer()).stream()
                        .map(player -> player.getGameProfile().name()),
                    builder
                ))
                .executes(FakePlayerCommand::openPlayerGui));
    }

    private static int runAction(
        CommandContext<CommandSourceStack> context,
        String translationKey,
        java.util.function.Consumer<FakeServerPlayer> action
    ) {
        String name = StringArgumentType.getString(context, "name");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.not_found", name));
            return 0;
        }
        action.accept(fake);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.action", name, Component.translatable(translationKey)),
            false
        );
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String name) {
        // 玩家名规则与原版一致，提前校验可以避免无效档案进入玩家列表。
        if (!name.matches("[A-Za-z0-9_-]{1,16}")) { // 官方不支持 “-” 但是游戏本身支持
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.invalid_name"));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        // 位置、维度和朝向来自命令来源，命令方块因此可以在自身位置生成假人。

        try {
            FakeServerPlayer fake = FakePlayerManager.spawn(
                source.getServer(),
                source.getLevel(),
                name,
                source.getPosition(),
                source.getRotation()
            );
            source.sendSuccess(() -> Component.translatable("commands.fakeplayer.spawned", fake.getGameProfile().name()), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("commands.fakeplayer.duplicate", name));
            return 0;
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("生成假玩家 {} 时发生异常", name, exception);
            source.sendFailure(Component.translatable("commands.fakeplayer.spawn_failed", name));
            return 0;
        }
    }

    private static int kill(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.not_found", name));
            return 0;
        }
        FakePlayerManager.remove(fake);
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.killed", name), true);
        return 1;
    }

    private static int shadow(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.player_not_found", name));
            return 0;
        }
        if (player instanceof FakeServerPlayer) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.cannot_shadow_fake", name));
            return 0;
        }
        if (context.getSource().getServer().isSingleplayerOwner(player.nameAndId())) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.cannot_shadow_owner"));
            return 0;
        }

        try {
            FakePlayerManager.shadow(player);
            context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.shadowed", name), true);
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("为真玩家 {} 创建替身时发生异常", name, exception);
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.shadow_failed", name));
            return 0;
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        String names = FakePlayerManager.all(context.getSource().getServer()).stream()
            .map(player -> player.getGameProfile().name())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .reduce((left, right) -> left + ", " + right)
            .orElse(Component.translatable("commands.fakeplayer.none").getString());
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.list", names), false);
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.player_only"));
            return 0;
        }
        FakePlayerMenuOpener.openGlobal(viewer);
        return 1;
    }

    private static int openPlayerGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.player_only"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.not_found", name));
            return 0;
        }

        FakePlayerMenuOpener.openControl(viewer, fake);
        return 1;
    }

    private static String nextName(CommandContext<CommandSourceStack> context) {
        // 从最小可用序号开始查找，确保无参数命令不会与在线玩家重名。
        int index = 1;
        while (context.getSource().getServer().getPlayerList().getPlayerByName("robot-" + index) != null) {
            index++;
        }
        return "robot-" + index;
    }
}
