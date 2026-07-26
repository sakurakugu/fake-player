package com.sakurakugu.fakeplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.network.OpenGlobalMenuPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** 客户端入口，负责注册并处理全局假人列表快捷键。 */
@Mod(value = FakePlayerMod.MOD_ID, dist = Dist.CLIENT)
public final class FakePlayerClientMod {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "main")
    );
    private static final KeyMapping OPEN_LIST = new KeyMapping(
        "key.fakeplayer.open_list",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        CATEGORY
    );

    public FakePlayerClientMod(IEventBus modBus) {
        modBus.addListener(FakePlayerClientMod::registerKeys);
        NeoForge.EVENT_BUS.addListener(FakePlayerClientMod::clientTick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_LIST);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_LIST.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientPacketDistributor.sendToServer(new OpenGlobalMenuPayload());
            }
        }
    }
}
