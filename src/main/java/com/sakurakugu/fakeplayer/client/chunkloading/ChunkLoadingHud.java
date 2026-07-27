package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload.AnchorView;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 在左上角显示玩家当前所在的本模组加载范围。 */
public final class ChunkLoadingHud {
    private ChunkLoadingHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        var snapshot = ClientChunkLoadingState.snapshot();
        if (!ClientChunkLoadingState.hudEnabled() || snapshot == null || minecraft.player == null
            || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        int chunkX = minecraft.player.chunkPosition().x();
        int chunkZ = minecraft.player.chunkPosition().z();
        List<AnchorView> current = snapshot.anchors().stream()
            .filter(anchor -> anchor.contains(chunkX, chunkZ)).toList();
        if (current.isEmpty()) {
            return;
        }
        int width = Math.max(150, current.stream().map(anchor -> minecraft.font.width(anchor.name()))
            .max(Integer::compareTo).orElse(100) + 48);
        int height = 17 + current.size() * 11;
        graphics.fill(5, 5, 5 + width, 5 + height, 0xB0101417);
        graphics.text(minecraft.font, Component.translatable("hud.fakeplayer.chunkloader.title"), 10, 9, 0xFF72D6A2);
        int y = 21;
        for (AnchorView anchor : current) {
            graphics.text(minecraft.font, Component.translatable("hud.fakeplayer.chunkloader.entry", anchor.name(),
                anchor.radius(), Component.translatable(anchor.ticking()
                    ? "commands.fakeplayer.chunkloader.mode_ticking"
                    : "commands.fakeplayer.chunkloader.mode_loading")), 10, y, 0xFFFFFFFF);
            y += 11;
        }
    }
}
