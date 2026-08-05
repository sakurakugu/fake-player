package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.ChunkLoaderMenu;
import com.sakurakugu.fakeplayer.menu.ChunkLoaderMenu.AnchorSummary;
import com.sakurakugu.fakeplayer.network.ChunkLoaderActionPayload;
import com.sakurakugu.fakeplayer.network.ChunkLoaderActionPayload.Action;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 创建和管理区块加载点。 */
public final class ChunkLoaderScreen extends AbstractContainerScreen<ChunkLoaderMenu> {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 286;
    private static final int PAGE_SIZE = 6;
    private int page;
    private int selectedIndex = -1;
    private boolean configureTicking;
    private boolean addTicking;
    private Action confirmation;

    public ChunkLoaderScreen(ChunkLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        rebuildControls();
    }

    private void rebuildControls() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.backup"),
            button -> send(Action.BACKUP, "", 0, false)).bounds(leftPos + 280, topPos + 9, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(confirmation == Action.RESTORE
                ? "gui.fakeplayer.chunkloader.confirm_restore" : "gui.fakeplayer.chunkloader.restore"),
            button -> confirmOrSend(Action.RESTORE, "")).bounds(leftPos + 348, topPos + 9, 66, 20).build());

        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, menu.anchors().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            AnchorSummary anchor = menu.anchors().get(index);
            Component label = Component.literal((anchor.enabled() ? "[+] " : "[-] ") + anchor.name());
            addRenderableWidget(Button.builder(label, button -> select(selected))
                .bounds(leftPos + 16, topPos + 48 + (index - first) * 27, 145, 22).build());
        }
        addPageButtons();
        addSelectedControls();
        addCreateControls();
    }

    private void addSelectedControls() {
        AnchorSummary selected = selected();
        if (selected == null) {
            return;
        }
        EditBox radius = addRenderableWidget(new EditBox(font, leftPos + 180, topPos + 138, 52, 20,
            Component.translatable("gui.fakeplayer.chunkloader.radius")));
        radius.setMaxLength(2);
        radius.setValue(Integer.toString(selected.radius()));
        radius.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        Button mode = addRenderableWidget(Button.builder(modeLabel(configureTicking), button -> {
            configureTicking = !configureTicking;
            button.setMessage(modeLabel(configureTicking));
        }).bounds(leftPos + 236, topPos + 138, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.apply"), button ->
            send(Action.CONFIGURE, selected.name(), parseRadius(radius), configureTicking))
            .bounds(leftPos + 335, topPos + 138, 69, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(selected.enabled()
                ? "gui.fakeplayer.chunkloader.disable" : "gui.fakeplayer.chunkloader.enable"), button ->
            send(selected.enabled() ? Action.DISABLE : Action.ENABLE, selected.name(), 0, false))
            .bounds(leftPos + 180, topPos + 166, 105, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(confirmation == Action.REMOVE
                ? "gui.fakeplayer.chunkloader.confirm_remove" : "gui.fakeplayer.chunkloader.remove"), button ->
            confirmOrSend(Action.REMOVE, selected.name())).bounds(leftPos + 289, topPos + 166, 115, 20).build());
    }

    private void addCreateControls() {
        EditBox name = addRenderableWidget(new EditBox(font, leftPos + 16, topPos + 251, 125, 20,
            Component.translatable("gui.fakeplayer.chunkloader.name")));
        name.setMaxLength(32);
        name.setHint(Component.translatable("gui.fakeplayer.chunkloader.name"));
        EditBox radius = addRenderableWidget(new EditBox(font, leftPos + 145, topPos + 251, 48, 20,
            Component.translatable("gui.fakeplayer.chunkloader.radius")));
        radius.setMaxLength(2);
        radius.setValue("0");
        radius.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        Button mode = addRenderableWidget(Button.builder(modeLabel(addTicking), button -> {
            addTicking = !addTicking;
            button.setMessage(modeLabel(addTicking));
        }).bounds(leftPos + 197, topPos + 251, 118, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.chunkloader.add"), button ->
            send(Action.ADD, name.getValue(), parseRadius(radius), addTicking))
            .bounds(leftPos + 319, topPos + 251, 95, 20).build());
    }

    private void addPageButtons() {
        Button previous = Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(leftPos + 16, topPos + 214, 32, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(leftPos + 129, topPos + 214, 32, 20).build();
        next.active = page + 1 < pageCount();
        addRenderableWidget(next);
    }

    private void select(int index) {
        selectedIndex = index;
        configureTicking = menu.anchors().get(index).ticking();
        confirmation = null;
        rebuildControls();
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(page + offset, pageCount() - 1));
        selectedIndex = -1;
        confirmation = null;
        rebuildControls();
    }

    private void confirmOrSend(Action action, String name) {
        if (confirmation != action) {
            confirmation = action;
            rebuildControls();
            return;
        }
        send(action, name, 0, false);
    }

    private void send(Action action, String name, int radius, boolean ticking) {
        ClientPacketDistributor.sendToServer(
            new ChunkLoaderActionPayload(menu.containerId, action, name, radius, ticking));
    }

    private int parseRadius(EditBox box) {
        try {
            return Math.min(Integer.parseInt(box.getValue()), menu.maximumRadius());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int pageCount() {
        return Math.max(1, (menu.anchors().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private AnchorSummary selected() {
        return selectedIndex >= 0 && selectedIndex < menu.anchors().size()
            ? menu.anchors().get(selectedIndex) : null;
    }

    private static Component modeLabel(boolean ticking) {
        return Component.translatable(ticking
            ? "gui.fakeplayer.chunkloader.mode_ticking"
            : "gui.fakeplayer.chunkloader.mode_loading");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xF0222528);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 36, 0xFF2F4A3D);
        graphics.fill(leftPos, topPos + 36, leftPos + PANEL_WIDTH, topPos + 38, 0xFFD5A94E);
        graphics.outline(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);
        graphics.fill(leftPos + 174, topPos + 48, leftPos + 414, topPos + 192, 0x802C3033);
        graphics.fill(leftPos, topPos + 240, leftPos + PANEL_WIDTH, topPos + 242, 0xFF496957);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 16, 14, 0xFFFFFFFF, false);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.page",
            page + 1, pageCount()), 88, 219, 0xFFB8D8C5);
        AnchorSummary selected = selected();
        if (selected == null) {
            graphics.centeredText(font, Component.translatable(menu.anchors().isEmpty()
                ? "gui.fakeplayer.chunkloader.empty" : "gui.fakeplayer.chunkloader.select"),
                294, 107, 0xFFAAAAAA);
        } else {
            graphics.text(font, Component.literal(selected.name()), 184, 58, 0xFFFFFFFF, false);
            graphics.text(font, Component.literal(selected.dimension()), 184, 76, 0xFFB8D8C5, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.position",
                selected.x(), selected.y(), selected.z()), 184, 94, 0xFFCCCCCC, false);
            int diameter = selected.radius() * 2 + 1;
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.chunks",
                diameter * diameter), 184, 112, 0xFFCCCCCC, false);
        }
        graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.create_here"),
            16, 243, 0xFFB8D8C5, false);
    }
}
