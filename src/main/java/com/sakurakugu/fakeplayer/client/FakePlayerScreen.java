package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.menu.FakePlayerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** 假玩家控制菜单的客户端绘制界面。 */
public final class FakePlayerScreen extends AbstractContainerScreen<FakePlayerMenu> {
    // 复用原版六行容器纹理的顶部区域，当前菜单实际只显示三行。
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    public FakePlayerScreen(FakePlayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 71);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            BACKGROUND,
            leftPos,
            topPos,
            0.0F,
            0.0F,
            imageWidth,
            imageHeight,
            256,
            256
        );
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 控制菜单没有玩家背包区域，因此只绘制标题。
        graphics.text(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }
}
