package com.sakurakugu.fakeplayer.client.chunkloading;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.chunkloading.ChunkKey;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode;
import com.sakurakugu.fakeplayer.client.ui.SolidButton;
import com.sakurakugu.fakeplayer.client.ui.PixelGlyph;
import com.sakurakugu.fakeplayer.client.ui.SolidSliderButton;
import com.sakurakugu.fakeplayer.network.ChunkLoaderActionPayload;
import com.sakurakugu.fakeplayer.network.ChunkLoaderActionPayload.Action;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import com.sakurakugu.fakeplayer.network.RequestChunkMapPayload;
import com.sakurakugu.fakeplayer.network.OpenFakePlayerPagePayload;
import com.sakurakugu.fakeplayer.network.ToggleGlobalSettingPayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 以客户端已加载地形为背景的区块加载编辑地图。 */
public final class ChunkMapScreen extends Screen implements ChunkLoadMapFrontend {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 286;
    private static final int SETTINGS_PANEL_WIDTH = 430;
    private static final int SETTINGS_PANEL_HEIGHT = 180;
    private static final int PAGE_SIZE = 6;
    private static final int BOTTOM_BUTTON_WIDTH = 52;
    private static final int BOTTOM_BUTTON_GAP = 4;
    private static final String[] GLOBAL_SETTING_KEYS = {
        "restore_players", "container_transfer_buttons"
    };
    private static final double MIN_SCALE = 0.35D;
    private static final double MAX_SCALE = 2.5D;
    private static final int UNLOADED_A = 0xFF20262A;
    private static final int UNLOADED_B = 0xFF252C30;
    private static final Identifier PLAYER_MARKER = Identifier.withDefaultNamespace(
        "textures/map/decorations/player.png"
    );
    private static final int[] PLAYER_MARKER_COLORS = {
        0xFF4FC3F7, 0xFFFF6B6B, 0xFF66D17A, 0xFFFFC857,
        0xFFC77DFF, 0xFF36C9B4, 0xFFFF8A4C, 0xFFF06292
    };

    private final ChunkLoadMapController controller;
    private final ChunkTerrainTileCache terrainTiles;
    private final Map<Long, ManualLoadMode> authoritativeModes = new HashMap<>();
    private double centerBlockX;
    private double centerBlockZ;
    private double pixelsPerBlock = 0.75D;
    private boolean dragging;
    private boolean settingsOpen;
    private boolean managementOpen;
    private int snapshotRefreshTicks;
    private Button saveButton;
    private final Button[] globalSettingButtons = new Button[GLOBAL_SETTING_KEYS.length];
    private int page;
    private int selectedIndex = -1;
    private boolean configureTicking;
    private boolean addTicking;
    private Action confirmation;

    public ChunkMapScreen(ChunkMapSnapshotPayload snapshot, boolean managementOpen, boolean settingsOpen) {
        super(Component.translatable("gui.fakeplayer.chunkloader.map_title"));
        controller = new ChunkLoadMapController(snapshot);
        terrainTiles = ClientChunkLoadingState.terrainTiles();
        this.managementOpen = managementOpen;
        this.settingsOpen = settingsOpen;
        centerBlockX = snapshot.playerChunkX() * 16.0D + 8.0D;
        centerBlockZ = snapshot.playerChunkZ() * 16.0D + 8.0D;
        rebuildAuthoritativeModes();
    }

    public void update(ChunkMapSnapshotPayload value) { acceptSnapshot(value); }

    @Override
    public void tick() {
        super.tick();
        if (saveButton != null) saveButton.active = controller.dirty();
        if (minecraft.player != null && minecraft.getConnection() != null && snapshotRefreshTicks-- <= 0) {
            ClientPacketDistributor.sendToServer(new RequestChunkMapPayload(false, false, false));
            snapshotRefreshTicks = 10;
        }
    }

    @Override
    protected void init() {
        saveButton = null;
        if (managementOpen) {
            addManagementControls();
            return;
        }
        if (settingsOpen) {
            int panelWidth = settingsPanelWidth();
            int left = (width - panelWidth) / 2;
            int top = settingsPanelTop();
            int halfWidth = panelWidth / 2;
            addRenderableWidget(new MarkerNameScaleSlider(
                left + halfWidth + 8, top + 44, halfWidth - 24, 20));
            for (int index = 0; index < GLOBAL_SETTING_KEYS.length; index++) {
                int settingIndex = index;
                globalSettingButtons[index] = addRenderableWidget(Button.builder(globalSettingLabel(index),
                    button -> toggleGlobalSetting(settingIndex))
                    .bounds(left + 16, top + 104 + index * 26, panelWidth - 32, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> showSettings(false))
                .bounds(width / 2 - 50, top + SETTINGS_PANEL_HEIGHT - 26, 100, 20).build());
            return;
        }
        int modeCount = ChunkMapEditMode.values().length;
        int buttonWidth = Mth.clamp((width - 118) / modeCount, 32, 44);
        int x = 6;
        for (ChunkMapEditMode mode : ChunkMapEditMode.values()) {
            addRenderableWidget(Button.builder(Component.literal(label(mode)), button -> setEditMode(mode))
                .bounds(x, 6, buttonWidth, 20).build());
            x += buttonWidth;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.map_undo"),
            button -> controller.undo()).bounds(x + 6, 6, 48, 20).build());

        saveButton = addRenderableWidget(new SolidButton(width - 42, 7, 18, 18, PixelGlyph.SAVE,
            Component.translatable("gui.fakeplayer.chunkloader.map_save"), button -> controller.apply()));
        saveButton.active = controller.dirty();
        addRenderableWidget(new SolidButton(width - 22, 7, 18, 18, PixelGlyph.CLOSE,
            Component.translatable("gui.close"), button -> closeMap()));

        addBottomBar();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (managementOpen) {
            drawManagement(graphics);
            return;
        }
        if (settingsOpen) {
            drawMapContents(graphics);
            drawSettings(graphics);
            return;
        }
        drawMapContents(graphics);
        PlayerMarker hoveredPlayer = playerMarkerAt(mouseX, mouseY);
        drawHoveredChunk(graphics, mouseX, mouseY, hoveredPlayer);

        Component heading = Component.literal(title.getString() + "  [" + label(controller.mode()) + "]");
        drawFloatingText(graphics, heading, width / 2, 32, 0xFFFFFFFF);
        int centerChunkX = Mth.floor(centerBlockX) >> 4;
        int centerChunkZ = Mth.floor(centerBlockZ) >> 4;
        Component status = Component.translatable("gui.fakeplayer.chunkloader.map_position",
            centerChunkX, centerChunkZ, controller.snapshot().dimension()).copy()
            .append("  ").append(Math.round(pixelsPerBlock * 100.0D) + "%");
        drawFloatingText(graphics, status, width / 2, height - 38, 0xFFC8D6CF);
    }

    private void drawMapContents(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0xFF111518);
        graphics.enableScissor(0, 0, width, height);
        drawTerrain(graphics);
        drawChunkOverlays(graphics);
        drawFakePlayers(graphics);
        drawPlayer(graphics);
        graphics.disableScissor();
    }

    private void drawFloatingText(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        int textWidth = font.width(text);
        graphics.fill(centerX - textWidth / 2 - 3, y - 2,
            centerX + (textWidth + 1) / 2 + 3, y + 11, 0xB8111518);
        graphics.centeredText(font, text, centerX, y, color);
    }

    private void drawTerrain(GuiGraphicsExtractor graphics) {
        int minChunkX = Mth.floor(screenToWorldX(0)) >> 4;
        int maxChunkX = Mth.floor(screenToWorldX(width - 1)) >> 4;
        int minChunkZ = Mth.floor(screenToWorldZ(0)) >> 4;
        int maxChunkZ = Mth.floor(screenToWorldZ(height - 1)) >> 4;
        int sampleY = minecraft.player == null ? 64 : minecraft.player.getBlockY();
        terrainTiles.beginFrame();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int left = worldToScreenX(chunkX * 16.0D);
                int top = worldToScreenZ(chunkZ * 16.0D);
                int right = worldToScreenX((chunkX + 1) * 16.0D);
                int tileBottom = worldToScreenZ((chunkZ + 1) * 16.0D);
                int color = ((chunkX + chunkZ) & 1) == 0 ? UNLOADED_A : UNLOADED_B;
                graphics.fill(left, top, right, tileBottom, color);
                Identifier texture = terrainTiles.texture(chunkX, chunkZ, sampleY);
                if (texture != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0.0F, 0.0F,
                        Math.max(1, right - left), Math.max(1, tileBottom - top), 16, 16, 16, 16);
                }
            }
        }
    }

    private void drawChunkOverlays(GuiGraphicsExtractor graphics) {
        int minChunkX = Mth.floor(screenToWorldX(0)) >> 4;
        int maxChunkX = Mth.floor(screenToWorldX(width - 1)) >> 4;
        int minChunkZ = Mth.floor(screenToWorldZ(0)) >> 4;
        int maxChunkZ = Mth.floor(screenToWorldZ(height - 1)) >> 4;
        Map<Long, ManualLoadMode> painted = controller.painted();
        var erased = controller.erased();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long key = ChunkKey.pack(chunkX, chunkZ);
                ManualLoadMode mode = painted.get(key);
                if (mode == null && !erased.contains(key)) mode = authoritativeModes.get(key);
                int left = worldToScreenX(chunkX * 16.0D);
                int top = worldToScreenZ(chunkZ * 16.0D);
                int right = worldToScreenX((chunkX + 1) * 16.0D);
                int tileBottom = worldToScreenZ((chunkZ + 1) * 16.0D);
                if (mode != null) graphics.fill(left, top, right, tileBottom, modeColor(mode));
                if (pixelsPerBlock >= 0.65D) graphics.outline(left, top,
                    Math.max(1, right - left), Math.max(1, tileBottom - top), 0x283A4449);
            }
        }
    }

    private void drawFakePlayers(GuiGraphicsExtractor graphics) {
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            if (fake.enabled()) {
                outlineRange(graphics, fake.x() + 0.5D, fake.z() + 0.5D,
                    fake.simulationDistance(), 0xFF4EC9E8);
            }
        }
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            drawPlayerMarker(graphics, fake.id(), fake.x() + 0.5D, fake.z() + 0.5D, fake.yaw(), fake.name());
        }
    }

    private void outlineRange(GuiGraphicsExtractor graphics, double blockX, double blockZ, int radius, int color) {
        int centerChunkX = Mth.floor(blockX) >> 4;
        int centerChunkZ = Mth.floor(blockZ) >> 4;
        int left = worldToScreenX((centerChunkX - radius) * 16.0D);
        int top = worldToScreenZ((centerChunkZ - radius) * 16.0D);
        int right = worldToScreenX((centerChunkX + radius + 1) * 16.0D);
        int bottom = worldToScreenZ((centerChunkZ + radius + 1) * 16.0D);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), color);
    }

    private void drawPlayer(GuiGraphicsExtractor graphics) {
        if (minecraft.player == null) return;
        drawPlayerMarker(graphics, minecraft.player.getUUID(), minecraft.player.getX(), minecraft.player.getZ(),
            minecraft.player.getYRot(), minecraft.player.getGameProfile().name());
    }

    private void drawPlayerMarker(GuiGraphicsExtractor graphics, java.util.UUID id, double blockX, double blockZ,
                                  float yaw, String name) {
        int screenX = worldToScreenX(blockX);
        int screenY = worldToScreenZ(blockZ);
        int nameHeight = Mth.ceil(9.0D * ChunkMapClientConfig.markerNameScale());
        if (screenX < 4 || screenX >= width - 4 || screenY < 4 || screenY >= height - nameHeight - 6) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(screenX, screenY);
        // 原版地图玩家图标默认朝北，而实体 yaw 为 0 时朝南。
        graphics.pose().rotate((float) Math.toRadians(yaw + 180.0F));
        graphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_MARKER, -4, -4,
            0.0F, 0.0F, 8, 8, 8, 8, 8, 8, markerColor(id));
        graphics.pose().popMatrix();
        float scale = (float) ChunkMapClientConfig.markerNameScale();
        int nameWidth = Mth.ceil(font.width(name) * scale);
        int nameX = Mth.clamp(screenX - nameWidth / 2, 2, Math.max(2, width - nameWidth - 2));
        int nameY = screenY + 5;
        graphics.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + nameHeight, 0x99000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate(nameX, nameY);
        graphics.pose().scale(scale, scale);
        graphics.text(font, Component.literal(name), 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    private static int markerColor(java.util.UUID id) {
        int hash = Long.hashCode(id.getMostSignificantBits()) ^ Long.hashCode(id.getLeastSignificantBits());
        return PLAYER_MARKER_COLORS[Math.floorMod(hash, PLAYER_MARKER_COLORS.length)];
    }

    private PlayerMarker playerMarkerAt(int mouseX, int mouseY) {
        if (minecraft.player != null) {
            PlayerMarker player = new PlayerMarker(minecraft.player.getUUID(),
                minecraft.player.getGameProfile().name(), minecraft.player.getX(), minecraft.player.getZ(), false);
            if (containsMarker(player, mouseX, mouseY)) return player;
        }
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            PlayerMarker marker = new PlayerMarker(fake.id(), fake.name(), fake.x() + 0.5D, fake.z() + 0.5D, true);
            if (containsMarker(marker, mouseX, mouseY)) return marker;
        }
        return null;
    }

    private boolean containsMarker(PlayerMarker player, int mouseX, int mouseY) {
        int markerX = worldToScreenX(player.blockX());
        int markerY = worldToScreenZ(player.blockZ());
        float scale = (float) ChunkMapClientConfig.markerNameScale();
        int nameWidth = Mth.ceil(font.width(player.name()) * scale);
        int nameHeight = Mth.ceil(9.0F * scale);
        int nameX = Mth.clamp(markerX - nameWidth / 2, 2, Math.max(2, width - nameWidth - 2));
        boolean overIcon = mouseX >= markerX - 5 && mouseX <= markerX + 5
            && mouseY >= markerY - 5 && mouseY <= markerY + 5;
        boolean overName = mouseX >= nameX - 2 && mouseX <= nameX + nameWidth + 2
            && mouseY >= markerY + 4 && mouseY <= markerY + 5 + nameHeight;
        return overIcon || overName;
    }

    private PlayerSkin skin(PlayerMarker player) {
        if (minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(player.id());
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(new GameProfile(player.id(), player.name()));
    }

    private void drawSettings(GuiGraphicsExtractor graphics) {
        int panelWidth = settingsPanelWidth();
        int left = (width - panelWidth) / 2;
        int top = settingsPanelTop();
        int right = left + panelWidth;
        int halfWidth = panelWidth / 2;
        graphics.fill(0, 0, width, height, 0x55000000);
        graphics.fill(left, top, right, top + SETTINGS_PANEL_HEIGHT, 0xC0111518);
        graphics.outline(left, top, panelWidth, SETTINGS_PANEL_HEIGHT, 0xB08B8B8B);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.map_settings_title"),
            width / 2, top + 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.marker_name_preview"),
            left + halfWidth / 2, top + 34, 0xFFB8C1BD);
        drawScaledPreview(graphics, Component.literal(minecraft.player == null
            ? "Player" : minecraft.player.getGameProfile().name()), left + halfWidth / 2, top + 52);
        graphics.fill(left + 12, top + 78, right - 12, top + 79, 0x808B8B8B);
        graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.fake_player_settings"),
            left + 16, top + 88, 0xFFB8C1BD, false);
    }

    private int settingsPanelWidth() {
        return Math.min(SETTINGS_PANEL_WIDTH, width - 24);
    }

    private int settingsPanelTop() {
        return Math.max(6, (height - SETTINGS_PANEL_HEIGHT) / 2);
    }

    private void addBottomBar() {
        int count = 5;
        int totalWidth = count * BOTTOM_BUTTON_WIDTH + (count - 1) * BOTTOM_BUTTON_GAP;
        int x = (width - totalWidth) / 2;
        int y = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.map_settings"),
            button -> showSettings(true)).bounds(x, y, BOTTOM_BUTTON_WIDTH, 20).build());
        x += BOTTOM_BUTTON_WIDTH + BOTTOM_BUTTON_GAP;
        addPageButton(x, y, "gui.fakeplayer.chunkloader.bottom_spawn", OpenFakePlayerPagePayload.Page.SPAWN);
        x += BOTTOM_BUTTON_WIDTH + BOTTOM_BUTTON_GAP;
        addPageButton(x, y, "gui.fakeplayer.chunkloader.bottom_list", OpenFakePlayerPagePayload.Page.LIST);
        x += BOTTOM_BUTTON_WIDTH + BOTTOM_BUTTON_GAP;
        addPageButton(x, y, "gui.fakeplayer.chunkloader.bottom_presets", OpenFakePlayerPagePayload.Page.PRESETS);
        x += BOTTOM_BUTTON_WIDTH + BOTTOM_BUTTON_GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.bottom_management"),
            button -> showManagement(true)).bounds(x, y, BOTTOM_BUTTON_WIDTH, 20).build());
    }

    private void addPageButton(int x, int y, String translationKey, OpenFakePlayerPagePayload.Page page) {
        addRenderableWidget(Button.builder(Component.translatable(translationKey),
            button -> ClientPacketDistributor.sendToServer(new OpenFakePlayerPagePayload(page)))
            .bounds(x, y, BOTTOM_BUTTON_WIDTH, 20).build());
    }

    private void toggleGlobalSetting(int index) {
        ClientPacketDistributor.sendToServer(new ToggleGlobalSettingPayload(index));
        for (Button button : globalSettingButtons) if (button != null) button.active = false;
    }

    private Component globalSettingLabel(int index) {
        Component name = Component.translatable("gui.fakeplayer.global.setting." + GLOBAL_SETTING_KEYS[index]);
        Component state = Component.translatable((controller.snapshot().globalSettingsMask() & (1 << index)) != 0
            ? "gui.fakeplayer.global.enabled" : "gui.fakeplayer.global.disabled");
        return Component.translatable("gui.fakeplayer.global.setting_value", name, state);
    }

    private void drawScaledPreview(GuiGraphicsExtractor graphics, Component text, int centerX, int y) {
        float scale = (float) ChunkMapClientConfig.markerNameScale();
        int scaledWidth = Mth.ceil(font.width(text) * scale);
        int x = centerX - scaledWidth / 2;
        graphics.fill(x - 2, y - 1, x + scaledWidth + 2, y + Mth.ceil(9.0F * scale), 0x99000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    private void showSettings(boolean value) {
        if (settingsOpen && !value) ChunkMapClientConfig.save();
        settingsOpen = value;
        rebuildWidgets();
    }

    private void showManagement(boolean value) {
        managementOpen = value;
        page = 0;
        selectedIndex = -1;
        confirmation = null;
        rebuildWidgets();
    }

    private void addManagementControls() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> showManagement(false))
            .bounds(left + 16, top + 9, 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.backup"),
            button -> sendManagementAction(Action.BACKUP, "", 0, false))
            .bounds(left + 280, top + 9, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(confirmation == Action.RESTORE
                ? "gui.fakeplayer.chunkloader.confirm_restore" : "gui.fakeplayer.chunkloader.restore"),
            button -> confirmOrSend(Action.RESTORE, ""))
            .bounds(left + 348, top + 9, 66, 20).build());
        addRenderableWidget(new SolidButton(width - 22, 7, 18, 18, PixelGlyph.CLOSE,
            Component.translatable("gui.close"), button -> closeMap()));

        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, regions().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            var region = regions().get(index);
            Component label = Component.literal((region.enabled() ? "[+] " : "[-] ") + region.name());
            addRenderableWidget(Button.builder(label, button -> selectRegion(selected))
                .bounds(left + 16, top + 48 + (index - first) * 27, 145, 22).build());
        }
        addManagementPageButtons(left, top);
        addSelectedRegionControls(left, top);
        addCreateRegionControls(left, top);
    }

    private void addSelectedRegionControls(int left, int top) {
        var selected = selectedRegion();
        if (selected == null) return;
        EditBox radius = addRenderableWidget(new EditBox(font, left + 180, top + 138, 52, 20,
            Component.translatable("gui.fakeplayer.chunkloader.radius")));
        radius.setMaxLength(2);
        radius.setValue(Integer.toString(selected.radius()));
        radius.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(Button.builder(modeLabel(configureTicking), button -> {
            configureTicking = !configureTicking;
            button.setMessage(modeLabel(configureTicking));
        }).bounds(left + 236, top + 138, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.apply"), button ->
            sendManagementAction(Action.CONFIGURE, selected.name(), parseRadius(radius), configureTicking))
            .bounds(left + 335, top + 138, 69, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(selected.enabled()
                ? "gui.fakeplayer.chunkloader.disable" : "gui.fakeplayer.chunkloader.enable"), button ->
            sendManagementAction(selected.enabled() ? Action.DISABLE : Action.ENABLE,
                selected.name(), 0, false)).bounds(left + 180, top + 166, 105, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(confirmation == Action.REMOVE
                ? "gui.fakeplayer.chunkloader.confirm_remove" : "gui.fakeplayer.chunkloader.remove"), button ->
            confirmOrSend(Action.REMOVE, selected.name())).bounds(left + 289, top + 166, 115, 20).build());
    }

    private void addCreateRegionControls(int left, int top) {
        EditBox name = addRenderableWidget(new EditBox(font, left + 16, top + 251, 125, 20,
            Component.translatable("gui.fakeplayer.chunkloader.name")));
        name.setMaxLength(32);
        name.setHint(Component.translatable("gui.fakeplayer.chunkloader.name"));
        EditBox radius = addRenderableWidget(new EditBox(font, left + 145, top + 251, 48, 20,
            Component.translatable("gui.fakeplayer.chunkloader.radius")));
        radius.setMaxLength(2);
        radius.setValue("0");
        radius.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(Button.builder(modeLabel(addTicking), button -> {
            addTicking = !addTicking;
            button.setMessage(modeLabel(addTicking));
        }).bounds(left + 197, top + 251, 118, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.add"), button ->
            sendManagementAction(Action.ADD, name.getValue(), parseRadius(radius), addTicking))
            .bounds(left + 319, top + 251, 95, 20).build());
    }

    private void addManagementPageButtons(int left, int top) {
        Button previous = Button.builder(Component.literal("<"), button -> changeManagementPage(-1))
            .bounds(left + 16, top + 214, 32, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), button -> changeManagementPage(1))
            .bounds(left + 129, top + 214, 32, 20).build();
        next.active = page + 1 < managementPageCount();
        addRenderableWidget(next);
    }

    private void drawManagement(GuiGraphicsExtractor graphics) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(0, 0, width, height, 0xFF111518);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0222528);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 36, 0xFF373737);
        graphics.fill(left, top + 36, left + PANEL_WIDTH, top + 38, 0xFFD5A94E);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);
        graphics.fill(left + 174, top + 48, left + 414, top + 192, 0x802C3033);
        graphics.fill(left, top + 240, left + PANEL_WIDTH, top + 242, 0xFF565656);
        graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.title"),
            left + 78, top + 14, 0xFFFFFFFF, false);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.page",
            page + 1, managementPageCount()), left + 88, top + 219, 0xFFC6C6C6);
        var selected = selectedRegion();
        if (selected == null) {
            graphics.centeredText(font, Component.translatable(regions().isEmpty()
                ? "gui.fakeplayer.chunkloader.empty" : "gui.fakeplayer.chunkloader.select"),
                left + 294, top + 107, 0xFFAAAAAA);
        } else {
            graphics.text(font, Component.literal(selected.name()), left + 184, top + 58, 0xFFFFFFFF, false);
            graphics.text(font, Component.literal(selected.dimension()), left + 184, top + 76, 0xFFC6C6C6, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.position",
                selected.chunkX() << 4, 0, selected.chunkZ() << 4), left + 184, top + 94, 0xFFCCCCCC, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.chunks", selected.chunkCount()),
                left + 184, top + 112, 0xFFCCCCCC, false);
        }
        graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.create_here"),
            left + 16, top + 243, 0xFFC6C6C6, false);
    }

    private void selectRegion(int index) {
        selectedIndex = index;
        configureTicking = regions().get(index).ticking();
        confirmation = null;
        rebuildWidgets();
    }

    private void changeManagementPage(int offset) {
        page = Math.max(0, Math.min(page + offset, managementPageCount() - 1));
        selectedIndex = -1;
        confirmation = null;
        rebuildWidgets();
    }

    private void confirmOrSend(Action action, String name) {
        if (confirmation != action) {
            confirmation = action;
            rebuildWidgets();
            return;
        }
        sendManagementAction(action, name, 0, false);
    }

    private void sendManagementAction(Action action, String name, int radius, boolean ticking) {
        ClientPacketDistributor.sendToServer(new ChunkLoaderActionPayload(action, name, radius, ticking));
    }

    private int parseRadius(EditBox box) {
        try {
            return Math.min(Integer.parseInt(box.getValue()), controller.snapshot().maximumRadius());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int managementPageCount() {
        return Math.max(1, (regions().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private java.util.List<ChunkMapSnapshotPayload.RegionSummary> regions() {
        return controller.snapshot().managementRegions();
    }

    private ChunkMapSnapshotPayload.RegionSummary selectedRegion() {
        return selectedIndex >= 0 && selectedIndex < regions().size() ? regions().get(selectedIndex) : null;
    }

    private static Component modeLabel(boolean ticking) {
        return Component.translatable(ticking
            ? "gui.fakeplayer.chunkloader.mode_ticking"
            : "gui.fakeplayer.chunkloader.mode_loading");
    }

    @Override
    public void onClose() {
        if (settingsOpen) showSettings(false);
        else if (managementOpen) showManagement(false);
        else super.onClose();
    }

    private void closeMap() {
        super.onClose();
    }

    private void drawHoveredChunk(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  PlayerMarker hoveredPlayer) {
        int[] chunk = chunkAt(mouseX, mouseY);
        if (chunk == null) return;
        int blockX = Mth.floor(screenToWorldX(mouseX));
        int blockZ = Mth.floor(screenToWorldZ(mouseY));
        int left = worldToScreenX(chunk[0] * 16.0D);
        int top = worldToScreenZ(chunk[1] * 16.0D);
        int right = worldToScreenX((chunk[0] + 1) * 16.0D);
        int tileBottom = worldToScreenZ((chunk[1] + 1) * 16.0D);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, tileBottom - top), 0xFFFFFFFF);
        Component names = controller.snapshot().regions().stream()
            .filter(region -> region.dimension().equals(controller.snapshot().dimension()))
            .filter(region -> region.contains(chunk[0], chunk[1]))
            .map(region -> region.name() + ":" + region.mode().name().toLowerCase(Locale.ROOT))
            .reduce((a, b) -> a + ", " + b)
            .<Component>map(Component::literal)
            .orElseGet(() -> Component.translatable("gui.fakeplayer.chunkloader.map_hover.none"));
        Component chunkLine = Component.translatable("gui.fakeplayer.chunkloader.map_hover.chunk", chunk[0], chunk[1]);
        Component blockLine = Component.translatable("gui.fakeplayer.chunkloader.map_hover.block", blockX, blockZ);
        Component regionsLine = Component.translatable("gui.fakeplayer.chunkloader.map_hover.regions", names);
        Component playerLine = hoveredPlayer == null ? Component.empty() : Component.literal(hoveredPlayer.name());
        if (hoveredPlayer != null && hoveredPlayer.fake()) {
            playerLine = playerLine.copy().append(Component.translatable("gui.fakeplayer.tab_marker")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        int textWidth = Math.max(font.width(chunkLine), Math.max(font.width(blockLine), font.width(regionsLine)));
        int faceSize = font.lineHeight;
        int playerWidth = hoveredPlayer == null ? 0 : faceSize + 2 + font.width(playerLine);
        int contentWidth = Math.max(textWidth, playerWidth);
        int contentHeight = hoveredPlayer == null ? 29 : 29 + faceSize + 2;
        int textX = Math.max(4, Math.min(width - contentWidth - 4, mouseX + 10));
        int textY = Math.max(4, Math.min(height - contentHeight - 4, mouseY + 10));
        graphics.fill(textX - 2, textY - 2, textX + contentWidth + 2,
            textY + contentHeight + 2, 0xD9111518);
        graphics.text(font, chunkLine, textX, textY, 0xFFFFFFFF);
        graphics.text(font, blockLine, textX, textY + 10, 0xFFFFFFFF);
        graphics.text(font, regionsLine, textX, textY + 20, 0xFFFFFFFF);
        if (hoveredPlayer != null) {
            int playerY = textY + 31;
            PlayerFaceExtractor.extractRenderState(graphics, skin(hoveredPlayer), textX, playerY, faceSize);
            graphics.text(font, playerLine, textX + faceSize + 2, playerY, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (settingsOpen || managementOpen) return false;
        int[] chunk = chunkAt(event.x(), event.y());
        if (chunk == null) return false;
        dragging = true;
        if (event.button() == 0 && controller.mode() != ChunkMapEditMode.BROWSE) {
            controller.edit(chunk[0], chunk[1]);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (settingsOpen || managementOpen) return super.mouseDragged(event, deltaX, deltaY);
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY);
        if (controller.mode() == ChunkMapEditMode.BROWSE || event.button() == 1) {
            centerBlockX -= deltaX / pixelsPerBlock;
            centerBlockZ -= deltaY / pixelsPerBlock;
        } else {
            int[] chunk = chunkAt(event.x(), event.y());
            if (chunk != null) controller.edit(chunk[0], chunk[1]);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsOpen || managementOpen) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (!insideMap(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        double worldX = screenToWorldX(mouseX);
        double worldZ = screenToWorldZ(mouseY);
        double next = Mth.clamp(pixelsPerBlock * Math.pow(1.2D, verticalAmount), MIN_SCALE, MAX_SCALE);
        if (next != pixelsPerBlock) {
            pixelsPerBlock = next;
            centerBlockX = worldX - (mouseX - width / 2.0D) / pixelsPerBlock;
            centerBlockZ = worldZ - (mouseY - height / 2.0D) / pixelsPerBlock;
        }
        return true;
    }

    private int[] chunkAt(double mouseX, double mouseY) {
        if (!insideMap(mouseX, mouseY)) return null;
        return new int[]{Mth.floor(screenToWorldX(mouseX)) >> 4, Mth.floor(screenToWorldZ(mouseY)) >> 4};
    }

    private boolean insideMap(double x, double y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private int worldToScreenX(double worldX) {
        return Mth.floor(width / 2.0D + (worldX - centerBlockX) * pixelsPerBlock);
    }

    private int worldToScreenZ(double worldZ) {
        return Mth.floor(height / 2.0D + (worldZ - centerBlockZ) * pixelsPerBlock);
    }

    private double screenToWorldX(double screenX) {
        return centerBlockX + (screenX - width / 2.0D) / pixelsPerBlock;
    }

    private double screenToWorldZ(double screenY) {
        return centerBlockZ + (screenY - height / 2.0D) / pixelsPerBlock;
    }

    private void rebuildAuthoritativeModes() {
        authoritativeModes.clear();
        for (var region : controller.snapshot().regions()) {
            if (!region.dimension().equals(controller.snapshot().dimension())) continue;
            if (!region.enabled()) continue;
            for (long chunk : region.chunks()) {
                authoritativeModes.merge(chunk, region.mode(), (left, right) ->
                    left.ordinal() >= right.ordinal() ? left : right);
            }
        }
    }

    private static int modeColor(ManualLoadMode mode) { return switch (mode) {
        case LOADED -> 0x66287E8E; case TICKING -> 0x66D18B35; case FULL -> 0x66C94D55;
    }; }

    private static String label(ChunkMapEditMode mode) { return switch (mode) {
        case BROWSE -> "浏览"; case LOADED -> "弱"; case TICKING -> "强"; case FULL -> "完整"; case ERASE -> "擦除";
    }; }

    @Override
    public void acceptSnapshot(ChunkMapSnapshotPayload snapshot) {
        var previous = controller.snapshot();
        controller.accept(snapshot);
        rebuildAuthoritativeModes();
        if (settingsOpen && snapshot.globalSettingsMask() != previous.globalSettingsMask()) {
            rebuildWidgets();
        }
        if (managementOpen && (snapshot.revision() != previous.revision()
            || !snapshot.managementRegions().equals(previous.managementRegions()))) {
            selectedIndex = Math.min(selectedIndex, snapshot.managementRegions().size() - 1);
            confirmation = null;
            rebuildWidgets();
        }
    }

    @Override
    public void focus(String dimension, double blockX, double blockZ) {
        if (dimension.equals(controller.snapshot().dimension())) {
            centerBlockX = blockX;
            centerBlockZ = blockZ;
        }
    }

    @Override public void setEditMode(ChunkMapEditMode mode) { controller.setMode(mode); }
    @Override public void close() { onClose(); }

    private final class MarkerNameScaleSlider extends SolidSliderButton {
        private MarkerNameScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                (ChunkMapClientConfig.markerNameScale() - 0.5D) / 1.5D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.fakeplayer.chunkloader.marker_name_scale",
                Math.round((0.5D + value * 1.5D) * 100.0D)));
        }

        @Override
        protected void applyValue() {
            ChunkMapClientConfig.setMarkerNameScale(0.5D + value * 1.5D);
        }
    }

    private record PlayerMarker(java.util.UUID id, String name, double blockX, double blockZ, boolean fake) {
    }

}
