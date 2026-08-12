package com.sakurakugu.fakeplayer.chunkloading;

import java.util.UUID;

/** 假玩家加载配置；位置和维度始终从在线实体读取。 */
public record FakePlayerLoadPolicy(UUID fakePlayerId, boolean enabled, int simulationDistance) {
    public FakePlayerLoadPolicy withEnabled(boolean value) {
        return new FakePlayerLoadPolicy(fakePlayerId, value, simulationDistance);
    }

    public FakePlayerLoadPolicy withSimulationDistance(int value) {
        return new FakePlayerLoadPolicy(fakePlayerId, enabled, value);
    }
}
