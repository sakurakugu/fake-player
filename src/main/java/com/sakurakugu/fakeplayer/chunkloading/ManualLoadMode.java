package com.sakurakugu.fakeplayer.chunkloading;

/** 手动区块加载的业务等级，枚举顺序同时表示强弱关系。 */
public enum ManualLoadMode {
    LOADED,
    TICKING,
    FULL;

    public LoadStrength strength() {
        return LoadStrength.valueOf(name());
    }
}
