package com.sakurakugu.fakeplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import com.sakurakugu.fakeplayer.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadRegion;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.menu.FakePlayerMenuOpener;
import java.util.Comparator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** 提供区块票加载点的创建、配置和生命周期命令。 */
public final class ChunkLoaderCommand {
    private ChunkLoaderCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chunkloader")
            .requires(FakePlayerConfig::canUseCommands)
            .executes(ChunkLoaderCommand::openGui)
            .then(Commands.literal("list").executes(ChunkLoaderCommand::list))
            .then(Commands.literal("backup").executes(ChunkLoaderCommand::backup))
            .then(Commands.literal("restore").then(Commands.literal("confirm")
                .executes(ChunkLoaderCommand::restore)))
            .then(Commands.literal("info").then(anchorArgument().executes(ChunkLoaderCommand::info)))
            .then(Commands.literal("add")
                .then(Commands.argument("anchor", StringArgumentType.word())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(0,
                            ChunkLoaderManager.ABSOLUTE_MAX_RADIUS))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(ManualLoadMode.values()).map(mode -> mode.name().toLowerCase()), builder))
                            .executes(ChunkLoaderCommand::add)))))
            .then(Commands.literal("enable").then(anchorArgument()
                .executes(context -> setEnabled(context, true))))
            .then(Commands.literal("disable").then(anchorArgument()
                .executes(context -> setEnabled(context, false))))
            .then(Commands.literal("fake").then(fakeArgument()
                .then(Commands.literal("info").executes(ChunkLoaderCommand::fakeInfo))
                .then(Commands.literal("disable").executes(ChunkLoaderCommand::disableFake))
                .then(Commands.literal("set").then(Commands.argument("distance",
                    IntegerArgumentType.integer(0, 32))
                    .executes(ChunkLoaderCommand::setFake)))))
            .then(Commands.literal("remove").then(anchorArgument().executes(ChunkLoaderCommand::remove)))
            .then(Commands.literal("configure").then(anchorArgument()
                .then(Commands.argument("radius", IntegerArgumentType.integer(0,
                        ChunkLoaderManager.ABSOLUTE_MAX_RADIUS))
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            java.util.Arrays.stream(ManualLoadMode.values()).map(mode -> mode.name().toLowerCase()), builder))
                        .executes(ChunkLoaderCommand::configure))))));
    }

    private static int openGui(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        FakePlayerMenuOpener.openChunkLoaders(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int backup(CommandContext<CommandSourceStack> context) {
        if (!ChunkLoaderManager.backup(context.getSource().getServer())) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.backup_failed"));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.backup_created"), false);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context) {
        var result = ChunkLoaderManager.restoreLatestBackup(context.getSource().getServer());
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.backup_restored"), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        if (radius > FakePlayerConfig.maxChunkLoadingRadius()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.radius_limit",
                FakePlayerConfig.maxChunkLoadingRadius()));
        }
        String name = StringArgumentType.getString(context, "anchor");
        ManualLoadMode mode = parseMode(context);
        if (mode == null) return failure(context, Component.literal("模式必须是 loaded、ticking 或 full"));
        BlockPos position = BlockPos.containing(context.getSource().getPosition());
        var result = ChunkLoaderManager.add(context.getSource().getServer(), name,
            context.getSource().getLevel(), position, radius, mode);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        ManualLoadRegion anchor = result.region().orElseThrow();
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.chunkloader.added",
            anchor.name(), position.toShortString(), anchor.dimension(), radius, mode(anchor)), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.setEnabled(context.getSource().getServer(), name, enabled);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "commands.fakeplayer.chunkloader.enabled" : "commands.fakeplayer.chunkloader.disabled", name), true);
        return 1;
    }

    private static int configure(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        if (radius > FakePlayerConfig.maxChunkLoadingRadius()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.radius_limit",
                FakePlayerConfig.maxChunkLoadingRadius()));
        }
        String name = StringArgumentType.getString(context, "anchor");
        ManualLoadMode mode = parseMode(context);
        if (mode == null) return failure(context, Component.literal("模式必须是 loaded、ticking 或 full"));
        var result = ChunkLoaderManager.configure(context.getSource().getServer(), name, radius, mode);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        ManualLoadRegion anchor = result.region().orElseThrow();
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.chunkloader.configured",
            anchor.name(), anchor.chunks().size(), mode(anchor)), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.remove(context.getSource().getServer(), name);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.removed", name), true);
        return 1;
    }

    private static int setFake(CommandContext<CommandSourceStack> context) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) return 0;
        int distance = IntegerArgumentType.getInteger(context, "distance");
        var result = FakePlayerSimulationService.setPolicy(context.getSource().getServer(), fake.getUUID(), true, distance);
        if (!result.successful()) return failure(context, Component.literal("无法设置假人模拟距离：" + result.reason()));
        context.getSource().sendSuccess(() -> Component.literal("已启用假人 " + fake.getName().getString()
            + " 的模拟加载，距离 " + distance + " 区块"), true);
        return 1;
    }

    private static int disableFake(CommandContext<CommandSourceStack> context) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) return 0;
        int distance = ChunkLoaderManager.data(context.getSource().getServer()).policy(fake.getUUID())
            .map(policy -> policy.simulationDistance()).orElse(0);
        var result = FakePlayerSimulationService.setPolicy(context.getSource().getServer(), fake.getUUID(), false, distance);
        if (!result.successful()) return failure(context, Component.literal("无法关闭假人模拟加载：" + result.reason()));
        context.getSource().sendSuccess(() -> Component.literal("已关闭假人 " + fake.getName().getString() + " 的模拟加载"), true);
        return 1;
    }

    private static int fakeInfo(CommandContext<CommandSourceStack> context) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) return 0;
        var policy = ChunkLoaderManager.data(context.getSource().getServer()).policy(fake.getUUID()).orElse(null);
        boolean enabled = policy != null && policy.enabled();
        int distance = policy == null ? 0 : policy.simulationDistance();
        context.getSource().sendSuccess(() -> Component.literal("假人 " + fake.getName().getString()
            + " 的模拟加载：" + (enabled ? "已启用" : "未启用") + "，距离 " + distance + " 区块"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        String values = ChunkLoaderManager.data(context.getSource().getServer()).regions().stream()
            .sorted(Comparator.comparing(ManualLoadRegion::name, String.CASE_INSENSITIVE_ORDER))
            .map(anchor -> anchor.name() + " [" + (anchor.enabled() ? "on" : "off") + ", r="
                + anchor.chunks().size() + ", " + mode(anchor).getString() + "]")
            .reduce((left, right) -> left + ", " + right)
            .orElse(Component.translatable("commands.fakeplayer.none").getString());
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.list", values), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "anchor");
        ManualLoadRegion anchor = ChunkLoaderManager.data(context.getSource().getServer()).region(name).orElse(null);
        if (anchor == null) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", "找不到加载点"));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.chunkloader.info",
            anchor.name(), anchor.enabled(), anchor.dimension(), "-", 0,
            anchor.chunks().size(), mode(anchor)), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> anchorArgument() {
        return Commands.argument("anchor", StringArgumentType.word()).suggests((context, builder) ->
            SharedSuggestionProvider.suggest(ChunkLoaderManager.data(context.getSource().getServer()).regions().stream()
                .map(ManualLoadRegion::name), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> fakeArgument() {
        return Commands.argument("fake", StringArgumentType.word()).suggests((context, builder) ->
            SharedSuggestionProvider.suggest(FakePlayerManager.all(context.getSource().getServer()).stream()
                .map(fake -> fake.getGameProfile().name()), builder));
    }

    private static FakeServerPlayer getFake(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "fake");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.literal("找不到在线假人：" + name));
        }
        return fake;
    }

    private static Component mode(ManualLoadRegion region) {
        return Component.literal(region.mode().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static ManualLoadMode parseMode(CommandContext<CommandSourceStack> context) {
        try {
            return ManualLoadMode.valueOf(StringArgumentType.getString(context, "mode").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int failure(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendFailure(message);
        return 0;
    }
}
