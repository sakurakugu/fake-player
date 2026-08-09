package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.ProfileResolver;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 处理假人详情页中需要字符串参数的服务端操作。 */
public final class FakePlayerManagementActions {
    private FakePlayerManagementActions() {
    }

    public static void rename(ServerPlayer viewer, String name) {
        if (!(viewer.containerMenu instanceof FakePlayerInventoryMenu menu)
            || menu.view() != FakePlayerInventoryMenu.View.INVENTORY
            || !menu.canManageTarget(viewer)) {
            return;
        }
        if (!name.matches("[A-Za-z0-9_-]{1,16}")) {
            viewer.sendSystemMessage(Component.translatable("commands.fakeplayer.invalid_name").withColor(0xFF5555));
            return;
        }

        FakeServerPlayer target = menu.target();
        if (target.getGameProfile().name().equals(name)) {
            return;
        }
        viewer.sendSystemMessage(Component.translatable("commands.fakeplayer.resolving_profile", name));
        MinecraftServer server = viewer.level().getServer();
        ProfileResolver.resolve(server, name).whenCompleteAsync((profileResult, throwable) -> {
            if (throwable != null) {
                FakePlayerMod.LOGGER.error("解析假玩家新名称 {} 的档案时发生异常", name, throwable);
                failure(viewer, "commands.fakeplayer.profile_service_unavailable", name);
                return;
            }
            if (!profileResult.successful()) {
                failure(viewer, profileFailureKey(profileResult.status()), name);
                return;
            }
            if (target.hasDisconnected()) {
                failure(viewer, "commands.fakeplayer.not_found", target.getGameProfile().name());
                return;
            }
            completeRename(viewer, target, profileResult.profile());
        }, server);
    }

    private static void completeRename(
        ServerPlayer viewer,
        FakeServerPlayer target,
        com.mojang.authlib.GameProfile profile
    ) {
        String oldName = target.getGameProfile().name();
        FakePlayerManager.RenameResult result = FakePlayerManager.rename(target, profile);
        if (!result.successful()) {
            failure(viewer, result.messageKey(), profile.name());
            return;
        }

        viewer.sendSystemMessage(Component.translatable(
            "commands.fakeplayer.renamed", oldName, profile.name()));
        FakePlayerMenuOpener.openInventory(viewer, result.player());
    }

    private static String profileFailureKey(ProfileResolver.Status status) {
        return switch (status) {
            case BUSY -> "commands.fakeplayer.profile_busy";
            case SERVICE_UNAVAILABLE -> "commands.fakeplayer.profile_service_unavailable";
            default -> "commands.fakeplayer.profile_not_found";
        };
    }

    private static void failure(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments).withColor(0xFF5555));
    }
}
