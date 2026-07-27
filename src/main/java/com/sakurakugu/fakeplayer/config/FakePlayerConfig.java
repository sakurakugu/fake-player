package com.sakurakugu.fakeplayer.config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.ModConfigSpec;

/** 服务端假玩家规则。 */
public final class FakePlayerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue COMMAND_PERMISSION_LEVEL;
    private static final ModConfigSpec.BooleanValue ALLOW_OFFLINE_PROFILES;
    private static final ModConfigSpec.EnumValue<ProfileStrategy> PROFILE_STRATEGY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("commands");
        COMMAND_PERMISSION_LEVEL = builder
            .comment("使用 /fakeplayer 和 /player 的最低原版权限等级，范围 0-4。")
            .defineInRange("permissionLevel", 2, 0, 4);
        builder.pop();

        builder.push("profiles");
        ALLOW_OFFLINE_PROFILES = builder
            .comment("缓存和在线档案均不可用时，是否允许使用离线 UUID。")
            .define("allowOfflineProfiles", true);
        PROFILE_STRATEGY = builder
            .comment("ONLINE_PREFERRED、CACHE_ONLY 或 OFFLINE_ONLY。")
            .defineEnum("strategy", ProfileStrategy.ONLINE_PREFERRED);
        builder.pop();
        SPEC = builder.build();
    }

    private FakePlayerConfig() {
    }

    public static boolean canUseCommands(CommandSourceStack source) {
        return switch (COMMAND_PERMISSION_LEVEL.get()) {
            case 0 -> Commands.hasPermission(Commands.LEVEL_ALL).test(source);
            case 1 -> Commands.hasPermission(Commands.LEVEL_MODERATORS).test(source);
            case 2 -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
            case 3 -> Commands.hasPermission(Commands.LEVEL_ADMINS).test(source);
            default -> Commands.hasPermission(Commands.LEVEL_OWNERS).test(source);
        };
    }

    public static boolean allowOfflineProfiles() {
        return ALLOW_OFFLINE_PROFILES.get();
    }

    public static ProfileStrategy profileStrategy() {
        return PROFILE_STRATEGY.get();
    }

    public enum ProfileStrategy {
        ONLINE_PREFERRED,
        CACHE_ONLY,
        OFFLINE_ONLY
    }
}
