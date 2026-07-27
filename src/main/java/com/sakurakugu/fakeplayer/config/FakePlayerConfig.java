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
    private static final ModConfigSpec.BooleanValue RESTORE_FAKE_PLAYERS;
    private static final ModConfigSpec.BooleanValue RESTORE_ACTIONS;
    private static final ModConfigSpec.BooleanValue AUTO_REPLENISHMENT;
    private static final ModConfigSpec.BooleanValue AUTO_REPLENISHMENT_FROM_SHULKER_BOXES;
    private static final ModConfigSpec.BooleanValue AUTO_REPLACE_TOOLS;
    private static final ModConfigSpec.BooleanValue AUTO_FISHING;
    private static final ModConfigSpec.IntValue MAX_CHUNK_LOADING_RADIUS;

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

        builder.push("persistence");
        RESTORE_FAKE_PLAYERS = builder
            .comment("服务器启动后是否恢复上次仍在线的假玩家。")
            .define("restoreFakePlayers", true);
        RESTORE_ACTIONS = builder
            .comment("恢复驻留假玩家时是否同时恢复持续动作。")
            .define("restoreActions", true);
        builder.pop();

        builder.push("automation");
        AUTO_REPLENISHMENT = builder
            .comment("手中可堆叠物品低于阈值时，是否从 36 格主背包自动补货。")
            .define("autoReplenishment", false);
        AUTO_REPLENISHMENT_FROM_SHULKER_BOXES = builder
            .comment("自动补货时是否也可从主背包内的潜影盒取出物品。")
            .define("autoReplenishmentFromShulkerBoxes", false);
        AUTO_REPLACE_TOOLS = builder
            .comment("主手或副手工具剩余耐久不超过 10 时，是否自动换上背包中的同种工具。")
            .define("autoReplaceTools", false);
        AUTO_FISHING = builder
            .comment("原版浮漂检测到咬钩后，是否自动收杆并再次抛竿。")
            .define("autoFishing", false);
        builder.pop();

        builder.push("chunkloading");
        MAX_CHUNK_LOADING_RADIUS = builder
            .comment("单个区块加载点允许的最大半径，0 表示仅中心区块，范围 0-32。")
            .defineInRange("maxRadius", 8, 0, 32);
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

    public static boolean restoreFakePlayers() {
        return RESTORE_FAKE_PLAYERS.get();
    }

    public static boolean restoreActions() {
        return RESTORE_ACTIONS.get();
    }

    public static boolean autoReplenishment() {
        return AUTO_REPLENISHMENT.get();
    }

    public static boolean autoReplenishmentFromShulkerBoxes() {
        return AUTO_REPLENISHMENT_FROM_SHULKER_BOXES.get();
    }

    public static boolean autoReplaceTools() {
        return AUTO_REPLACE_TOOLS.get();
    }

    public static boolean autoFishing() {
        return AUTO_FISHING.get();
    }

    public static int maxChunkLoadingRadius() {
        return MAX_CHUNK_LOADING_RADIUS.get();
    }

    /** 返回全局界面可即时调整的布尔配置快照。 */
    public static int globalSettingsMask() {
        int mask = 0;
        for (GlobalSetting setting : GlobalSetting.values()) {
            if (setting.enabled()) {
                mask |= 1 << setting.ordinal();
            }
        }
        return mask;
    }

    /** 切换一项全局配置并立即写回世界服务端配置文件。 */
    public static boolean toggleGlobalSetting(int index) {
        GlobalSetting[] settings = GlobalSetting.values();
        if (index < 0 || index >= settings.length) {
            return false;
        }
        settings[index].toggle();
        SPEC.save();
        return true;
    }

    public enum GlobalSetting {
        RESTORE_FAKE_PLAYERS,
        RESTORE_ACTIONS,
        AUTO_REPLENISHMENT,
        AUTO_REPLENISHMENT_FROM_SHULKER_BOXES,
        AUTO_REPLACE_TOOLS,
        AUTO_FISHING;

        private ModConfigSpec.BooleanValue value() {
            return switch (this) {
                case RESTORE_FAKE_PLAYERS -> FakePlayerConfig.RESTORE_FAKE_PLAYERS;
                case RESTORE_ACTIONS -> FakePlayerConfig.RESTORE_ACTIONS;
                case AUTO_REPLENISHMENT -> FakePlayerConfig.AUTO_REPLENISHMENT;
                case AUTO_REPLENISHMENT_FROM_SHULKER_BOXES -> FakePlayerConfig.AUTO_REPLENISHMENT_FROM_SHULKER_BOXES;
                case AUTO_REPLACE_TOOLS -> FakePlayerConfig.AUTO_REPLACE_TOOLS;
                case AUTO_FISHING -> FakePlayerConfig.AUTO_FISHING;
            };
        }

        public boolean enabled() {
            return value().get();
        }

        private void toggle() {
            ModConfigSpec.BooleanValue configValue = value();
            configValue.set(!configValue.get());
        }
    }

    public enum ProfileStrategy {
        ONLINE_PREFERRED,
        CACHE_ONLY,
        OFFLINE_ONLY
    }
}
