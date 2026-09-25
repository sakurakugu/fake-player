package com.sakurakugu.fakeplayer.client.chunkloading;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 区块地图的客户端显示设置。 */
public final class ChunkMapClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue MARKER_NAME_SCALE;
    private static final ModConfigSpec.BooleanValue SHOW_WEAK_LOADING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MARKER_NAME_SCALE = builder
            .comment("地图上玩家名称的缩放比例，范围 0.5-2.0。")
            .defineInRange("chunkMap.markerNameScale", 1.0D, 0.5D, 2.0D);
        SHOW_WEAK_LOADING = builder
            .comment("地图上是否画出强加载区块外围的弱加载范围（票据传播出的等级 32，方块刻但无实体刻）。")
            .define("chunkMap.showWeakLoading", true);
        SPEC = builder.build();
    }

    private ChunkMapClientConfig() {
    }

    public static double markerNameScale() {
        return MARKER_NAME_SCALE.get();
    }

    public static void setMarkerNameScale(double value) {
        MARKER_NAME_SCALE.set(value);
    }

    public static boolean weakLoadingVisible() {
        return SHOW_WEAK_LOADING.get();
    }

    public static void setWeakLoadingVisible(boolean value) {
        SHOW_WEAK_LOADING.set(value);
    }

    public static void save() {
        SPEC.save();
    }
}
