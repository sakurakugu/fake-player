package com.sakurakugu.fakeplayer.chunkloading;

import java.util.UUID;

/** 区分票据来源，避免重叠区域互相撤销。 */
public record LoadOwner(Type type, UUID id) {
    public enum Type {
        MANUAL_REGION,
        FAKE_PLAYER
    }

    public static LoadOwner manualRegion(UUID id) {
        return new LoadOwner(Type.MANUAL_REGION, id);
    }

    public static LoadOwner fakePlayer(UUID id) {
        return new LoadOwner(Type.FAKE_PLAYER, id);
    }
}
