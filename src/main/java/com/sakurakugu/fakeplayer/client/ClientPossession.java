package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.network.PossessionStatePayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;

/** 客户端只维护附身外观代理；摄像机、输入和移动始终使用本地玩家。 */
public final class ClientPossession {
    private static final Map<Integer, Integer> APPEARANCE_PROXIES = new HashMap<>();
    private static boolean resolvingSkin;

    private ClientPossession() {
    }

    public static void accept(PossessionStatePayload payload) {
        Integer previous = APPEARANCE_PROXIES.remove(payload.operatorEntityId());
        if (previous != null) {
            APPEARANCE_PROXIES.remove(previous);
        }
        if (payload.targetEntityId() != PossessionStatePayload.NONE) {
            APPEARANCE_PROXIES.put(payload.operatorEntityId(), payload.targetEntityId());
            APPEARANCE_PROXIES.put(payload.targetEntityId(), payload.operatorEntityId());
        }
    }

    public static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && APPEARANCE_PROXIES.containsKey(minecraft.player.getId());
    }

    public static PlayerSkin proxySkin(AbstractClientPlayer player) {
        if (resolvingSkin) {
            return null;
        }
        Integer proxyId = APPEARANCE_PROXIES.get(player.getId());
        if (proxyId == null || player.level() == null) {
            return null;
        }
        Entity proxy = player.level().getEntity(proxyId);
        if (!(proxy instanceof AbstractClientPlayer proxyPlayer)) {
            return null;
        }
        resolvingSkin = true;
        try {
            return proxyPlayer.getSkin();
        } finally {
            resolvingSkin = false;
        }
    }

    public static Boolean proxyModelPart(AbstractClientPlayer player, PlayerModelPart part) {
        if (resolvingSkin) {
            return null;
        }
        Integer proxyId = APPEARANCE_PROXIES.get(player.getId());
        if (proxyId == null || player.level() == null) {
            return null;
        }
        Entity proxy = player.level().getEntity(proxyId);
        if (!(proxy instanceof AbstractClientPlayer proxyPlayer)) {
            return null;
        }
        resolvingSkin = true;
        try {
            return proxyPlayer.isModelPartShown(part);
        } finally {
            resolvingSkin = false;
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            APPEARANCE_PROXIES.clear();
        }
    }
}
