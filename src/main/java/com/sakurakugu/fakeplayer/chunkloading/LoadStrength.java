package com.sakurakugu.fakeplayer.chunkloading;

/** 平台无关的区块加载能力。 */
public enum LoadStrength {
    LOADED,
    TICKING,
    FULL;

    public static LoadStrength strongest(LoadStrength left, LoadStrength right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
