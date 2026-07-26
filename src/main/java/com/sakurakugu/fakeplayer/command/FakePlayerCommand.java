package com.sakurakugu.fakeplayer.command;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 注册并处理 {@code /fakeplayer} 命令的各个分支。 */
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
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            FakePlayerManager.all(context.getSource().getServer()).stream()
                                .map(player -> player.getGameProfile().name()),
                            builder
                        ))
                        .executes(FakePlayerCommand::remove)))
                .then(Commands.literal("list").executes(FakePlayerCommand::list))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> spawn(context, StringArgumentType.getString(context, "name"))))
        );
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String name) {
        // 玩家名规则与原版一致，提前校验可以避免无效档案进入玩家列表。
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.invalid_name"));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        // 生成位置和朝向来自执行命令的玩家，因此控制台不能直接生成假玩家。
        ServerPlayer owner = source.getPlayer();
        if (owner == null) {
            source.sendFailure(Component.translatable("commands.fakeplayer.player_only"));
            return 0;
        }

        try {
            FakeServerPlayer fake = FakePlayerManager.spawn(
                source.getServer(),
                owner.level(),
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

    private static int remove(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.not_found", name));
            return 0;
        }
        FakePlayerManager.remove(fake);
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.removed", name), true);
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

    private static String nextName(CommandContext<CommandSourceStack> context) {
        // 从最小可用序号开始查找，确保无参数命令不会与在线玩家重名。
        int index = 1;
        while (context.getSource().getServer().getPlayerList().getPlayerByName("FakePlayer" + index) != null) {
            index++;
        }
        return "FakePlayer" + index;
    }
}
