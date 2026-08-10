package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.menu.GlobalFakePlayerMenu;
import com.sakurakugu.fakeplayer.network.SpawnFakePlayerPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 显示全局设置，并允许切换到假人列表。 */
public final class GlobalFakePlayerScreen extends AbstractContainerScreen<GlobalFakePlayerMenu> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 240;
    private static final int PAGE_SIZE = 5;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_GAP = 5;
    private static final String[] SETTING_KEYS = {
        "restore_players", "container_transfer_buttons"
    };

    private int page;
    private Page currentPage;
    private EditBox nameInput;
    private Button spawnButton;

    public GlobalFakePlayerScreen(GlobalFakePlayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
        currentPage = menu.openListInitially() ? Page.LIST : Page.SETTINGS;
        ClientGlobalSettings.setContainerTransferButtons(
            menu.settingEnabled(FakePlayerConfig.GlobalSetting.CONTAINER_TRANSFER_BUTTONS.ordinal()));
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        nameInput = null;
        spawnButton = null;
        if (currentPage == Page.SETTINGS) {
            int settingWidth = 130;
            for (int index = 0; index < FakePlayerConfig.GlobalSetting.values().length; index++) {
                int column = index % 2;
                int row = index / 2;
                int actionId = GlobalFakePlayerMenu.settingAction(index);
                addRenderableWidget(
                    Button.builder(settingLabel(index), button -> sendAction(actionId))
                        .bounds(leftPos + 16 + column * 138, topPos + 52 + row * 32, settingWidth, BUTTON_HEIGHT)
                        .build()
                );
            }
            addRenderableWidget(
                Button.builder(Component.translatable("gui.fakeplayer.global.open_spawn"), button -> showSpawn())
                    .bounds(leftPos + 50, topPos + 120, PANEL_WIDTH - 100, BUTTON_HEIGHT)
                    .build()
            );
            addRenderableWidget(
                Button.builder(Component.translatable("gui.fakeplayer.global.open_list"), button -> showList())
                    .bounds(leftPos + 50, topPos + 150, PANEL_WIDTH - 100, BUTTON_HEIGHT)
                    .build()
            );
            addRenderableWidget(
                Button.builder(Component.translatable("gui.fakeplayer.global.open_bots"),
                        button -> sendAction(GlobalFakePlayerMenu.ACTION_OPEN_BOTS))
                    .bounds(leftPos + 50, topPos + 180, PANEL_WIDTH - 100, BUTTON_HEIGHT)
                    .build()
            );
            return;
        }

        if (currentPage == Page.SPAWN) {
            nameInput = addRenderableWidget(new EditBox(
                font, leftPos + 50, topPos + 86, PANEL_WIDTH - 100, 22,
                Component.translatable("gui.fakeplayer.global.spawn_name")
            ));
            nameInput.setMaxLength(16);
            nameInput.setHint(Component.translatable("gui.fakeplayer.global.spawn_name"));
            nameInput.setResponder(value -> updateSpawnButton());
            spawnButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.fakeplayer.global.spawn"), button -> submitSpawn())
                    .bounds(leftPos + 50, topPos + 120, PANEL_WIDTH - 100, BUTTON_HEIGHT)
                    .build()
            );
            updateSpawnButton();
            addRenderableWidget(
                Button.builder(Component.translatable("gui.fakeplayer.global.settings"), button -> showSettings())
                    .bounds(leftPos + 50, topPos + 180, PANEL_WIDTH - 100, BUTTON_HEIGHT)
                    .build()
            );
            setInitialFocus(nameInput);
            return;
        }

        int firstIndex = page * PAGE_SIZE;
        int endIndex = Math.min(firstIndex + PAGE_SIZE, menu.playerNames().size());
        for (int index = firstIndex; index < endIndex; index++) {
            int row = index - firstIndex;
            int actionId = index;
            addRenderableWidget(
                Button.builder(Component.literal(menu.playerNames().get(index)), button -> sendAction(actionId))
                    .bounds(leftPos + 16, topPos + 48 + row * (BUTTON_HEIGHT + ROW_GAP), PANEL_WIDTH - 32, BUTTON_HEIGHT)
                    .build()
            );
        }

        int footerY = topPos + PANEL_HEIGHT - 30;
        Button previous = Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(leftPos + 16, footerY, 32, 20)
            .build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        addRenderableWidget(
            Button.builder(Component.translatable("gui.fakeplayer.global.settings"), button -> showSettings())
                .bounds(leftPos + 58, footerY, 76, 20)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.translatable("gui.fakeplayer.global.refresh"), button -> sendAction(GlobalFakePlayerMenu.ACTION_REFRESH))
                .bounds(leftPos + 142, footerY, 76, 20)
                .build()
        );

        Button next = Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(leftPos + PANEL_WIDTH - 48, footerY, 32, 20)
            .build();
        next.active = page + 1 < pageCount();
        addRenderableWidget(next);
    }

    private void showList() {
        currentPage = Page.LIST;
        page = 0;
        rebuildButtons();
    }

    private void showSettings() {
        currentPage = Page.SETTINGS;
        rebuildButtons();
    }

    private void showSpawn() {
        currentPage = Page.SPAWN;
        rebuildButtons();
    }

    private void updateSpawnButton() {
        if (spawnButton != null && nameInput != null) {
            spawnButton.active = nameInput.getValue().matches("[A-Za-z0-9_-]{1,16}");
        }
    }

    private void submitSpawn() {
        if (spawnButton != null && spawnButton.active && nameInput != null) {
            ClientPacketDistributor.sendToServer(new SpawnFakePlayerPayload(menu.containerId, nameInput.getValue()));
            spawnButton.active = false;
        }
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(page + offset, pageCount() - 1));
        rebuildButtons();
    }

    private int pageCount() {
        return Math.max(1, (menu.playerNames().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void sendAction(int actionId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actionId);
        }
    }

    private Component settingLabel(int index) {
        Component name = Component.translatable("gui.fakeplayer.global.setting." + SETTING_KEYS[index]);
        Component state = Component.translatable(menu.settingEnabled(index)
            ? "gui.fakeplayer.global.enabled"
            : "gui.fakeplayer.global.disabled");
        return Component.translatable("gui.fakeplayer.global.setting_value", name, state);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xF0222528);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 40, 0xFF373737);
        graphics.fill(leftPos, topPos + 40, leftPos + PANEL_WIDTH, topPos + 42, 0xFFD5A94E);
        graphics.outline(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component pageTitle = switch (currentPage) {
            case LIST -> Component.translatable("gui.fakeplayer.global.list_title");
            case SPAWN -> Component.translatable("gui.fakeplayer.global.spawn_title");
            case SETTINGS -> title;
        };
        graphics.centeredText(font, pageTitle, PANEL_WIDTH / 2, 12, 0xFFFFFFFF);
        if (currentPage != Page.LIST) {
            return;
        }
        if (menu.playerNames().isEmpty()) {
            graphics.centeredText(font, Component.translatable("gui.fakeplayer.global.empty"), PANEL_WIDTH / 2, 100, 0xFFAAAAAA);
        }
        graphics.centeredText(
            font,
            Component.translatable("gui.fakeplayer.global.page", page + 1, pageCount(), menu.playerNames().size()),
            PANEL_WIDTH / 2,
            27,
            0xFFC6C6C6
        );
    }

    private enum Page {
        SETTINGS,
        LIST,
        SPAWN
    }
}
