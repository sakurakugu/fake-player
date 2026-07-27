package com.sakurakugu.fakeplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.network.OpenGlobalMenuPayload;
import com.sakurakugu.fakeplayer.network.RequestChunkMapPayload;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import com.sakurakugu.fakeplayer.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.fakeplayer.client.chunkloading.ChunkLoadingHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** 客户端入口，负责注册并处理假人全局设置快捷键。 */
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
    private static final KeyMapping OPEN_CHUNK_MAP = new KeyMapping(
        "key.fakeplayer.open_chunk_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY
    );
    private static final KeyMapping TOGGLE_CHUNK_HUD = new KeyMapping(
        "key.fakeplayer.toggle_chunk_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY
    );
    private static int refreshTicks;

    public FakePlayerClientMod(IEventBus modBus) {
        modBus.addListener(FakePlayerClientMod::registerKeys);
        modBus.addListener(FakePlayerClientMod::registerGuiLayers);
        modBus.addListener(FakePlayerClientMod::registerClientPayloads);
        NeoForge.EVENT_BUS.addListener(FakePlayerClientMod::clientTick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_LIST);
        event.register(OPEN_CHUNK_MAP);
        event.register(TOGGLE_CHUNK_HUD);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_loading_hud"),
            ChunkLoadingHud::render);
    }

    private static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ChunkMapSnapshotPayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.accept(payload));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_LIST.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientPacketDistributor.sendToServer(new OpenGlobalMenuPayload());
            }
        }
        while (OPEN_CHUNK_MAP.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientPacketDistributor.sendToServer(new RequestChunkMapPayload(true));
            }
        }
        while (TOGGLE_CHUNK_HUD.consumeClick()) {
            ClientChunkLoadingState.toggleHud();
            refreshTicks = 0;
        }
        if (minecraft.player == null) {
            ClientChunkLoadingState.clear();
            refreshTicks = 0;
        } else if (ClientChunkLoadingState.hudEnabled() && refreshTicks-- <= 0) {
            ClientPacketDistributor.sendToServer(new RequestChunkMapPayload(false));
            refreshTicks = 40;
        }
    }
}
