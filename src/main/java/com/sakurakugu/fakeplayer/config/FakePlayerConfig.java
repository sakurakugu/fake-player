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
    private static final ModConfigSpec.IntValue MAX_CHUNK_LOADING_RADIUS;
    private static final ModConfigSpec.IntValue MAX_FORCED_CHUNKS;
    private static final ModConfigSpec.IntValue MAX_TICKING_CHUNKS;
    private static final ModConfigSpec.IntValue MAX_FAKE_PLAYER_SIMULATION_DISTANCE;
    private static final ModConfigSpec.IntValue MAX_PLAYER_LOADING_CHUNKS;
    private static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_TRANSFER_BUTTONS;

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
        builder.pop();

        builder.push("chunkloading");
        MAX_CHUNK_LOADING_RADIUS = builder
            .comment("单个区块加载点允许的最大半径，0 表示仅中心区块，范围 0-32。")
            .defineInRange("maxRadius", 8, 0, 32);
        MAX_FORCED_CHUNKS = builder
            .comment("所有启用加载点允许强加载的区块总数，防止批量同步加载拖垮服务器。")
            .defineInRange("maxForcedChunks", 2048, 1, 65536);
        MAX_TICKING_CHUNKS = builder
            .comment("所有 ticking 加载点允许完整刻处理的区块总数。")
            .defineInRange("maxTickingChunks", 512, 1, 16384);
        MAX_FAKE_PLAYER_SIMULATION_DISTANCE = builder
            .comment("单个假人允许的最大模拟加载距离，单位为区块，范围 0-32。")
            .defineInRange("maxFakePlayerSimulationDistance", 16, 0, 32);
        MAX_PLAYER_LOADING_CHUNKS = builder
            .comment("所有启用假人策略合计允许的模拟区块预算，与手动加载点预算独立。")
            .defineInRange("maxPlayerLoadingChunks", 65536, 1, 65536);
        builder.pop();
        builder.push("ui");
        ENABLE_CONTAINER_TRANSFER_BUTTONS = builder
            .comment("普通容器是否显示物品转移按钮；假人物品栏始终显示。")
            .define("enableContainerTransferButtons", true);
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

    public static int maxChunkLoadingRadius() {
        return MAX_CHUNK_LOADING_RADIUS.get();
    }

    public static int maxForcedChunks() {
        return MAX_FORCED_CHUNKS.get();
    }

    public static int maxTickingChunks() {
        return MAX_TICKING_CHUNKS.get();
    }

    public static int maxFakePlayerSimulationDistance() {
        return MAX_FAKE_PLAYER_SIMULATION_DISTANCE.get();
    }

    public static int maxPlayerLoadingChunks() {
        return MAX_PLAYER_LOADING_CHUNKS.get();
    }

    public static boolean containerTransferButtons() {
        return ENABLE_CONTAINER_TRANSFER_BUTTONS.get();
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
        CONTAINER_TRANSFER_BUTTONS;

        private ModConfigSpec.BooleanValue value() {
            return switch (this) {
                case RESTORE_FAKE_PLAYERS -> FakePlayerConfig.RESTORE_FAKE_PLAYERS;
                case CONTAINER_TRANSFER_BUTTONS -> FakePlayerConfig.ENABLE_CONTAINER_TRANSFER_BUTTONS;
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
