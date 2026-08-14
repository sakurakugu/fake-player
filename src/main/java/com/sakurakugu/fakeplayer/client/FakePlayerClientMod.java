package com.sakurakugu.fakeplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.network.PossessionStatePayload;
import com.sakurakugu.fakeplayer.network.RequestChunkMapPayload;
import com.sakurakugu.fakeplayer.network.StopPossessionPayload;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import com.sakurakugu.fakeplayer.network.BodyRotationPayload;
import com.sakurakugu.fakeplayer.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.fakeplayer.client.chunkloading.ChunkLoadingHud;
import com.sakurakugu.fakeplayer.client.chunkloading.ChunkMapClientConfig;
import com.sakurakugu.fakeplayer.client.ui.InventorySlotButton;
import com.sakurakugu.fakeplayer.client.ui.TransferButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** 客户端入口，负责注册并处理客户端快捷键。 */
@Mod(value = FakePlayerMod.MOD_ID, dist = Dist.CLIENT)
public final class FakePlayerClientMod {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "main")
    );
    private static final KeyMapping OPEN_CHUNK_MAP = new KeyMapping(
        "key.fakeplayer.open_chunk_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY
    );
    private static final KeyMapping TOGGLE_CHUNK_HUD = new KeyMapping(
        "key.fakeplayer.toggle_chunk_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY
    );
    private static final KeyMapping STOP_POSSESSION = new KeyMapping(
        "key.fakeplayer.stop_possession", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY
    );
    private static int refreshTicks;
    private static CreativeModeInventoryScreen creativeInventoryScreen;
    private static Button creativePossessionButton;

    public FakePlayerClientMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ChunkMapClientConfig.SPEC, "fakeplayer-client.toml");
        modBus.addListener(FakePlayerClientMod::registerKeys);
        modBus.addListener(FakePlayerClientMod::registerGuiLayers);
        modBus.addListener(FakePlayerClientMod::registerClientPayloads);
        NeoForge.EVENT_BUS.addListener(FakePlayerClientMod::clientTick);
        NeoForge.EVENT_BUS.addListener(FakePlayerClientMod::addInventoryButtons);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_CHUNK_MAP);
        event.register(TOGGLE_CHUNK_HUD);
        event.register(STOP_POSSESSION);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_loading_hud"),
            ChunkLoadingHud::render);
    }

    private static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ChunkMapSnapshotPayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.accept(payload));
        event.register(PossessionStatePayload.TYPE,
            (payload, context) -> ClientPossession.accept(payload));
        event.register(BodyRotationPayload.TYPE,
            (payload, context) -> ClientBodyRotation.accept(payload));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPossession.tick(minecraft);
        ClientBodyRotation.tick(minecraft);
        updateCreativePossessionButton(minecraft);
        while (STOP_POSSESSION.consumeClick()) {
            if (ClientPossession.active()) {
                ClientPacketDistributor.sendToServer(new StopPossessionPayload());
            }
        }
        while (OPEN_CHUNK_MAP.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientPacketDistributor.sendToServer(new RequestChunkMapPayload(true, false, false));
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
            ClientPacketDistributor.sendToServer(new RequestChunkMapPayload(false, false, false));
            refreshTicks = 40;
        }
    }

    private static void updateCreativePossessionButton(Minecraft minecraft) {
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen screen)
            || screen != creativeInventoryScreen || creativePossessionButton == null) {
            return;
        }
        creativePossessionButton.visible = ClientPossession.active() && screen.isInventoryOpen();
    }

    /** 附身期间在原版个人背包中提供可见的退出入口。 */
    private static void addInventoryButtons(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen
            && containerScreen.getMenu() instanceof ChestMenu
            && !(containerScreen instanceof FakePlayerInventoryScreen)
            && !(containerScreen instanceof GlobalFakePlayerScreen)
            && ClientGlobalSettings.containerTransferButtons()) {
            TransferButton.forContainer(containerScreen).forEach(event::addListener);
        }
        if (!(event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen)
            || !ClientPossession.active()) {
            return;
        }
        var screen = event.getScreen();
        int buttonX = screen.width / 2
            + (screen instanceof CreativeModeInventoryScreen ? 28 : -12);
        int buttonY = screen.height / 2 
            + (screen instanceof CreativeModeInventoryScreen ? -50 : -40);;
        Button button = new InventorySlotButton(
            buttonX,
            buttonY,
            FakePlayerInventoryScreen.POSSESSION_EXIT_ICON,
            Component.translatable("gui.fakeplayer.stop_possessing"),
            clicked -> ClientPacketDistributor.sendToServer(new StopPossessionPayload())
        );
        if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            creativeInventoryScreen = creativeScreen;
            creativePossessionButton = button;
            button.visible = creativeScreen.isInventoryOpen();
        }
        event.addListener(button);
    }
}
