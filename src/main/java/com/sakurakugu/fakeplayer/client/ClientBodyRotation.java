package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.network.BodyRotationPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;

/** 在客户端实体刻之后恢复服务端指定的独立身体朝向。 */
public final class ClientBodyRotation {
    private static final Map<Integer, Float> BODY_YAWS = new HashMap<>();
    private static ClientLevel level;

    private ClientBodyRotation() {
    }

    public static void accept(BodyRotationPayload payload) {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel != level) {
            level = currentLevel;
            BODY_YAWS.clear();
        }
        BODY_YAWS.put(payload.entityId(), payload.yaw());
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != level) {
            level = minecraft.level;
            BODY_YAWS.clear();
        }
        if (level == null) {
            return;
        }
        BODY_YAWS.entrySet().removeIf(entry -> {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity entity)) {
                return true;
            }
            entity.yBodyRot = entry.getValue();
            entity.yBodyRotO = entry.getValue();
            return false;
        });
    }
}
