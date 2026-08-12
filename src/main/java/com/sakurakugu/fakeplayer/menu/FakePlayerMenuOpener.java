package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerActions;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakePlayerPossession;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import com.sakurakugu.fakeplayer.persistence.FakePlayerSavedData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** 统一创建假人全局菜单和物品栏管理页面。 */
public final class FakePlayerMenuOpener {
    private FakePlayerMenuOpener() {
    }

    public static void openGlobal(ServerPlayer viewer) {
        openGlobal(viewer, false);
    }

    public static void openList(ServerPlayer viewer) {
        openGlobal(viewer, true);
    }

    public static void openBotManagement(ServerPlayer viewer) {
        openBotManagement(viewer, false);
    }

    public static void openBotManagement(ServerPlayer viewer, boolean openGroupsInitially) {
        FakePlayerSavedData savedData = FakePlayerPersistence.data(viewer.level().getServer());
        List<BotManagementMenu.PresetSummary> presets = savedData.presets().stream()
            .map(preset -> new BotManagementMenu.PresetSummary(
                preset.id(), preset.description(), preset.player().name()))
            .sorted(java.util.Comparator.comparing(
                BotManagementMenu.PresetSummary::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<BotManagementMenu.GroupSummary> groups = savedData.groups().stream()
            .map(group -> new BotManagementMenu.GroupSummary(group.id(), group.presetIds()))
            .sorted(java.util.Comparator.comparing(
                BotManagementMenu.GroupSummary::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<String> onlinePlayers = FakePlayerManager.all(viewer.level().getServer()).stream()
            .map(fake -> fake.getGameProfile().name())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        viewer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, player) -> new BotManagementMenu(
                    containerId, inventory, openGroupsInitially, presets, groups, onlinePlayers),
                Component.translatable("gui.fakeplayer.bot.title")
            ),
            data -> {
                data.writeBoolean(openGroupsInitially);
                data.writeVarInt(presets.size());
                presets.forEach(preset -> {
                    data.writeUtf(preset.id());
                    data.writeUtf(preset.description());
                    data.writeUtf(preset.playerName());
                });
                data.writeVarInt(groups.size());
                groups.forEach(group -> {
                    data.writeUtf(group.id());
                    writeStrings(data, group.presetIds());
                });
                writeStrings(data, onlinePlayers);
            }
        );
    }

    public static void openChunkLoaders(ServerPlayer viewer) {
        List<ChunkLoaderMenu.AnchorSummary> anchors = ChunkLoaderManager.data(viewer.level().getServer()).regions()
            .stream()
            .sorted(java.util.Comparator.comparing(
                com.sakurakugu.fakeplayer.chunkloading.ManualLoadRegion::name,
                String.CASE_INSENSITIVE_ORDER))
            .limit(ChunkLoaderMenu.MAX_ANCHORS)
            .map(region -> {
                long center = region.chunks().iterator().next();
                int minX = region.chunks().stream().mapToInt(net.minecraft.world.level.ChunkPos::getX).min().orElse(0);
                int maxX = region.chunks().stream().mapToInt(net.minecraft.world.level.ChunkPos::getX).max().orElse(0);
                int minZ = region.chunks().stream().mapToInt(net.minecraft.world.level.ChunkPos::getZ).min().orElse(0);
                int maxZ = region.chunks().stream().mapToInt(net.minecraft.world.level.ChunkPos::getZ).max().orElse(0);
                int radius = Math.max(maxX - minX, maxZ - minZ) / 2;
                return new ChunkLoaderMenu.AnchorSummary(region.name(), region.dimension().toString(),
                    net.minecraft.world.level.ChunkPos.getX(center) << 4, 0,
                    net.minecraft.world.level.ChunkPos.getZ(center) << 4, radius,
                    region.enabled(), region.mode() != com.sakurakugu.fakeplayer.chunkloading.ManualLoadMode.LOADED);
            })
            .toList();
        int maximumRadius = FakePlayerConfig.maxChunkLoadingRadius();
        viewer.openMenu(new SimpleMenuProvider(
            (containerId, inventory, player) -> new ChunkLoaderMenu(
                containerId, inventory, maximumRadius, anchors),
            Component.translatable("gui.fakeplayer.chunkloader.title")
        ), data -> {
            data.writeVarInt(maximumRadius);
            data.writeVarInt(anchors.size());
            anchors.forEach(anchor -> {
                data.writeUtf(anchor.name(), 32);
                data.writeUtf(anchor.dimension(), 256);
                data.writeInt(anchor.x());
                data.writeInt(anchor.y());
                data.writeInt(anchor.z());
                data.writeVarInt(anchor.radius());
                data.writeBoolean(anchor.enabled());
                data.writeBoolean(anchor.ticking());
            });
        });
    }

    private static void writeStrings(net.minecraft.network.RegistryFriendlyByteBuf data, List<String> values) {
        data.writeVarInt(values.size());
        values.forEach(data::writeUtf);
    }

    private static void openGlobal(ServerPlayer viewer, boolean openListInitially) {
        List<String> names = FakePlayerManager.all(viewer.level().getServer()).stream()
            .map(fake -> fake.getGameProfile().name())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        viewer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, player) -> new GlobalFakePlayerMenu(
                    containerId,
                    inventory,
                    openListInitially,
                    FakePlayerConfig.globalSettingsMask(),
                    names
                ),
                Component.translatable("gui.fakeplayer.global.title")
            ),
            data -> {
                data.writeBoolean(openListInitially);
                data.writeVarInt(FakePlayerConfig.globalSettingsMask());
                data.writeVarInt(names.size());
                names.forEach(name -> data.writeUtf(name, 64));
            }
        );
    }

    public static void openInventory(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.INVENTORY);
    }

    public static void openPossessedInventory(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.POSSESSED_INVENTORY);
    }

    public static void openEnderChest(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.ENDER_CHEST);
    }

    private static void openStorage(ServerPlayer viewer, FakeServerPlayer fake, FakePlayerInventoryMenu.View view) {
        if (!canManage(viewer, fake)) {
            return;
        }
        boolean possessedByViewer = FakePlayerPossession.isControlling(viewer, fake);
        boolean targetOccupied = FakePlayerPossession.isPossessed(fake);
        Component title = Component.translatable(
            view == FakePlayerInventoryMenu.View.ENDER_CHEST
                ? "gui.fakeplayer.ender_chest"
                : "gui.fakeplayer.inventory",
            fake.getGameProfile().name()
        );
        viewer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, player) -> new FakePlayerInventoryMenu(
                    containerId, inventory, fake, view, possessedByViewer, targetOccupied),
                title
            ),
            data -> {
                data.writeUtf(fake.getGameProfile().name());
                data.writeVarInt(view.ordinal());
                data.writeVarInt(fake.getId());
                data.writeBoolean(possessedByViewer);
                data.writeBoolean(targetOccupied);
                data.writeVarInt(FakePlayerInventoryMenu.automationMask(fake));
                data.writeVarInt(FakePlayerInventoryMenu.continuousControlMask(fake));
                data.writeVarInt(fake.actions().repeatInterval(
                    FakePlayerActions.ScheduledAction.ATTACK));
                data.writeVarInt(fake.actions().repeatInterval(
                    FakePlayerActions.ScheduledAction.USE));
                data.writeVarInt(fake.actions().repeatInterval(
                    FakePlayerActions.ScheduledAction.JUMP));
                data.writeVarInt(Math.round(fake.getXRot()));
                data.writeVarInt(Math.round(fake.getYRot()));
                data.writeVarInt(Math.round(fake.yBodyRot));
                data.writeBoolean(fake.actions().bodyFollowsHead());
            }
        );
    }

    private static boolean canManage(ServerPlayer viewer, FakeServerPlayer fake) {
        if (!FakePlayerPossession.isPossessed(fake)) {
            return true;
        }
        viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_locked"));
        return false;
    }
}
