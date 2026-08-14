package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.BotManagementMenu;
import com.sakurakugu.fakeplayer.menu.BotManagementMenu.GroupSummary;
import com.sakurakugu.fakeplayer.menu.BotManagementMenu.PresetSummary;
import com.sakurakugu.fakeplayer.network.BotActionPayload;
import com.sakurakugu.fakeplayer.network.RequestChunkMapPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 管理假人预设及分组。 */
public final class BotManagementScreen extends AbstractContainerScreen<BotManagementMenu> {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 270;
    private static final int PAGE_SIZE = 5;
    private static final int ROW_HEIGHT = 22;

    private boolean showingGroups;
    private int page;
    private int selectedIndex = -1;
    private int onlinePlayerIndex;
    private int addPresetIndex;
    private int removePresetIndex;

    public BotManagementScreen(BotManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
        showingGroups = menu.openGroupsInitially();
    }

    @Override
    protected void init() {
        super.init();
        rebuildControls();
    }

    private void rebuildControls() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.presets"), button -> setTab(false))
            .bounds(leftPos + 16, topPos + 43, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.groups"), button -> setTab(true))
            .bounds(leftPos + 120, topPos + 43, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.back"), button ->
                ClientPacketDistributor.sendToServer(new RequestChunkMapPayload(true, false, true)))
            .bounds(leftPos + 298, topPos + 43, 66, 20).build());

        if (showingGroups) {
            addGroupWidgets();
        } else {
            addPresetWidgets();
        }
        addPageButtons();
    }

    private void addPresetWidgets() {
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, menu.presets().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            PresetSummary preset = menu.presets().get(index);
            addRenderableWidget(Button.builder(Component.literal(preset.id()), button -> select(selected))
                .bounds(leftPos + 16, topPos + 72 + (index - first) * 25, 132, ROW_HEIGHT).build());
        }
        PresetSummary selected = selectedPreset();
        if (selected != null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.load"), button ->
                    send(BotActionPayload.Action.LOAD_PRESET, selected.id(), "", ""))
                .bounds(leftPos + 164, topPos + 153, 88, 22).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.delete"), button ->
                    send(BotActionPayload.Action.REMOVE_PRESET, selected.id(), "", ""))
                .bounds(leftPos + 258, topPos + 153, 88, 22).build());
        }

        EditBox id = addRenderableWidget(new EditBox(font, leftPos + 16, topPos + 231, 78, 20,
            Component.translatable("gui.fakeplayer.bot.preset_id")));
        id.setMaxLength(64);
        id.setHint(Component.translatable("gui.fakeplayer.bot.preset_id"));
        EditBox description = addRenderableWidget(new EditBox(font, leftPos + 202, topPos + 231, 105, 20,
            Component.translatable("gui.fakeplayer.bot.description")));
        description.setMaxLength(256);
        description.setHint(Component.translatable("gui.fakeplayer.bot.description"));
        Button player = addRenderableWidget(Button.builder(onlinePlayerLabel(), button -> {
            onlinePlayerIndex = cycle(onlinePlayerIndex, menu.onlinePlayers().size());
            button.setMessage(onlinePlayerLabel());
        }).bounds(leftPos + 98, topPos + 231, 100, 20).build());
        Button save = Button.builder(Component.translatable("gui.fakeplayer.bot.save"), button -> {
            if (!menu.onlinePlayers().isEmpty()) {
                send(BotActionPayload.Action.SAVE_PRESET, id.getValue(),
                    menu.onlinePlayers().get(onlinePlayerIndex), description.getValue());
            }
        }).bounds(leftPos + 311, topPos + 231, 53, 20).build();
        save.active = !menu.onlinePlayers().isEmpty();
        addRenderableWidget(save);
        player.active = !menu.onlinePlayers().isEmpty();
    }

    private void addGroupWidgets() {
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, menu.groups().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            GroupSummary group = menu.groups().get(index);
            Component label = Component.translatable("gui.fakeplayer.bot.group_entry",
                group.id(), group.presetIds().size());
            addRenderableWidget(Button.builder(label, button -> select(selected))
                .bounds(leftPos + 16, topPos + 72 + (index - first) * 25, 132, ROW_HEIGHT).build());
        }
        GroupSummary selected = selectedGroup();
        if (selected != null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.load"), button ->
                    send(BotActionPayload.Action.LOAD_GROUP, selected.id(), "", ""))
                .bounds(leftPos + 158, topPos + 153, 61, 22).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.unload"), button ->
                    send(BotActionPayload.Action.UNLOAD_GROUP, selected.id(), "", ""))
                .bounds(leftPos + 223, topPos + 153, 61, 22).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.delete"), button ->
                    send(BotActionPayload.Action.REMOVE_GROUP, selected.id(), "", ""))
                .bounds(leftPos + 288, topPos + 153, 76, 22).build());

            Button member = addRenderableWidget(Button.builder(removePresetLabel(selected), button -> {
                removePresetIndex = cycle(removePresetIndex, selected.presetIds().size());
                button.setMessage(removePresetLabel(selected));
            }).bounds(leftPos + 158, topPos + 187, 140, 20).build());
            Button remove = Button.builder(Component.translatable("gui.fakeplayer.bot.remove_member"), button -> {
                if (!selected.presetIds().isEmpty()) {
                    send(BotActionPayload.Action.REMOVE_FROM_GROUP, selected.id(),
                        selected.presetIds().get(Math.min(removePresetIndex, selected.presetIds().size() - 1)), "");
                }
            }).bounds(leftPos + 302, topPos + 187, 62, 20).build();
            member.active = !selected.presetIds().isEmpty();
            remove.active = !selected.presetIds().isEmpty();
            addRenderableWidget(remove);
        }

        EditBox groupId = addRenderableWidget(new EditBox(font, leftPos + 16, topPos + 218, 132, 20,
            Component.translatable("gui.fakeplayer.bot.group_id")));
        groupId.setMaxLength(64);
        groupId.setHint(Component.translatable("gui.fakeplayer.bot.group_id"));
        addRenderableWidget(Button.builder(Component.translatable("gui.fakeplayer.bot.create"), button ->
                send(BotActionPayload.Action.CREATE_GROUP, groupId.getValue(), "", ""))
            .bounds(leftPos + 152, topPos + 218, 72, 20).build());

        Button preset = addRenderableWidget(Button.builder(addPresetLabel(), button -> {
            addPresetIndex = cycle(addPresetIndex, menu.presets().size());
            button.setMessage(addPresetLabel());
        }).bounds(leftPos + 228, topPos + 218, 85, 20).build());
        Button add = Button.builder(Component.translatable("gui.fakeplayer.bot.add"), button -> {
            GroupSummary group = selectedGroup();
            if (group != null && !menu.presets().isEmpty()) {
                send(BotActionPayload.Action.ADD_TO_GROUP, group.id(), menu.presets().get(addPresetIndex).id(), "");
            }
        }).bounds(leftPos + 317, topPos + 218, 47, 20).build();
        add.active = selected != null && !menu.presets().isEmpty();
        preset.active = !menu.presets().isEmpty();
        addRenderableWidget(add);
    }

    private void addPageButtons() {
        int y = topPos + 187;
        Button previous = Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(leftPos + 16, y, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(leftPos + 118, y, 30, 20).build();
        next.active = page + 1 < pageCount();
        addRenderableWidget(next);
    }

    private void setTab(boolean groups) {
        if (showingGroups == groups) {
            return;
        }
        showingGroups = groups;
        page = 0;
        selectedIndex = -1;
        removePresetIndex = 0;
        rebuildControls();
    }

    private void select(int index) {
        selectedIndex = index;
        removePresetIndex = 0;
        rebuildControls();
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(page + offset, pageCount() - 1));
        selectedIndex = -1;
        removePresetIndex = 0;
        rebuildControls();
    }

    private int pageCount() {
        int size = showingGroups ? menu.groups().size() : menu.presets().size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private PresetSummary selectedPreset() {
        return selectedIndex >= 0 && selectedIndex < menu.presets().size()
            ? menu.presets().get(selectedIndex) : null;
    }

    private GroupSummary selectedGroup() {
        return selectedIndex >= 0 && selectedIndex < menu.groups().size()
            ? menu.groups().get(selectedIndex) : null;
    }

    private Component onlinePlayerLabel() {
        return menu.onlinePlayers().isEmpty()
            ? Component.translatable("gui.fakeplayer.bot.no_online")
            : Component.literal(menu.onlinePlayers().get(Math.min(onlinePlayerIndex, menu.onlinePlayers().size() - 1)));
    }

    private Component addPresetLabel() {
        return menu.presets().isEmpty()
            ? Component.translatable("gui.fakeplayer.bot.no_presets")
            : Component.literal(menu.presets().get(Math.min(addPresetIndex, menu.presets().size() - 1)).id());
    }

    private Component removePresetLabel(GroupSummary group) {
        return group.presetIds().isEmpty()
            ? Component.translatable("gui.fakeplayer.bot.no_members")
            : Component.literal(group.presetIds().get(Math.min(removePresetIndex, group.presetIds().size() - 1)));
    }

    private static int cycle(int current, int size) {
        return size == 0 ? 0 : (current + 1) % size;
    }

    private void send(BotActionPayload.Action action, String first, String second, String third) {
        ClientPacketDistributor.sendToServer(new BotActionPayload(menu.containerId, action, first, second, third));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xF0222528);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 36, 0xFF373737);
        graphics.fill(leftPos, topPos + 36, leftPos + PANEL_WIDTH, topPos + 38, 0xFFD5A94E);
        graphics.outline(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);
        graphics.fill(leftPos + 156, topPos + 70, leftPos + 364, topPos + 181, 0x802C3033);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, PANEL_WIDTH / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.bot.page", page + 1, pageCount()),
            82, 192, 0xFFC6C6C6);
        if (showingGroups) {
            drawGroupDetails(graphics);
        } else {
            drawPresetDetails(graphics);
        }
    }

    private void drawPresetDetails(GuiGraphicsExtractor graphics) {
        PresetSummary preset = selectedPreset();
        if (preset == null) {
            graphics.centeredText(font, Component.translatable(menu.presets().isEmpty()
                ? "gui.fakeplayer.bot.no_presets" : "gui.fakeplayer.bot.select_preset"), 260, 111, 0xFFAAAAAA);
            return;
        }
        graphics.text(font, Component.literal(preset.id()), 166, 78, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.bot.player", preset.playerName()),
            166, 96, 0xFFC6C6C6, false);
        String description = preset.description().isBlank()
            ? Component.translatable("gui.fakeplayer.bot.no_description").getString()
            : preset.description();
        graphics.text(font, Component.literal(shorten(description, 31)), 166, 116, 0xFFCCCCCC, false);
        if (description.length() > 31) {
            graphics.text(font, Component.literal(shorten(description.substring(31), 31)),
                166, 128, 0xFFCCCCCC, false);
        }
    }

    private void drawGroupDetails(GuiGraphicsExtractor graphics) {
        GroupSummary group = selectedGroup();
        if (group == null) {
            graphics.centeredText(font, Component.translatable(menu.groups().isEmpty()
                ? "gui.fakeplayer.bot.no_groups" : "gui.fakeplayer.bot.select_group"), 260, 111, 0xFFAAAAAA);
            return;
        }
        graphics.text(font, Component.literal(group.id()), 166, 78, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.bot.members"), 166, 96, 0xFFC6C6C6, false);
        String members = group.presetIds().isEmpty()
            ? Component.translatable("commands.fakeplayer.none").getString()
            : String.join(", ", group.presetIds());
        graphics.text(font, Component.literal(shorten(members, 31)), 166, 112, 0xFFCCCCCC, false);
        if (members.length() > 31) {
            graphics.text(font, Component.literal(shorten(members.substring(31), 31)),
                166, 124, 0xFFCCCCCC, false);
        }
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 1)) + "…";
    }
}
