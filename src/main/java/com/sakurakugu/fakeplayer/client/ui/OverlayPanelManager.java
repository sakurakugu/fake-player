package com.sakurakugu.fakeplayer.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 按注册顺序管理可展开的重叠面板；面板本身负责外框、标签和内容显隐。 */
public final class OverlayPanelManager {
    private final Font font;
    private final List<Panel> panels = new ArrayList<>();

    public OverlayPanelManager(Font font) {
        this.font = font;
    }

    /** 相对于界面锚点的面板外框布局。 */
    public record Layout(int top, int width, int height, int tabWidth, int tabHeight) {
    }

    public Panel addRightPanel(String id, int anchorX, int anchorY, Layout layout, Component title) {
        return addPanel(id, anchorX, anchorY + layout.top(), layout, title, Side.RIGHT);
    }

    public Panel addLeftPanel(String id, int anchorX, int anchorY, Layout layout, Component title) {
        return addPanel(id, anchorX, anchorY + layout.top(), layout, title, Side.LEFT);
    }

    private Panel addPanel(String id, int x, int y, Layout layout, Component title, Side side) {
        Panel panel = new Panel(id, x, y, layout, title, side);
        panels.add(panel);
        return panel;
    }

    /** 返回当前展开面板的稳定 ID，未展开时返回 null。 */
    public String openPanelId() {
        for (Panel panel : panels) {
            if (panel.open) {
                return panel.id;
            }
        }
        return null;
    }

    /** 根据稳定 ID 恢复重新初始化前展开的面板。 */
    public void restoreOpenPanel(String id) {
        if (id == null) {
            return;
        }
        for (Panel panel : panels) {
            if (panel.id.equals(id)) {
                panel.setOpen(true);
                return;
            }
        }
    }

    private void refresh() {
        boolean blocked = false;
        for (Panel panel : panels) {
            panel.applyState(blocked);
            if (panel.open) {
                blocked = true;
            }
        }
    }

    private enum Side {
        LEFT,
        RIGHT
    }

    @FunctionalInterface
    public interface ContentRenderer {
        void render(GuiGraphicsExtractor graphics, int x, int y);
    }

    /** 可加入 Screen 的面板覆盖层；基础背景仍可通过 drawBackground 按所需层级绘制。 */
    public final class Panel extends Button {
        private final List<AbstractWidget> contents = new ArrayList<>();
        private final String id;
        private final Layout layout;
        private final Component title;
        private final Side side;
        private AbstractWidget tab;
        private ContentRenderer contentRenderer = (graphics, x, y) -> { };
        private boolean open;

        private Panel(
            String id, int x, int y, Layout layout, Component title, Side side
        ) {
            super(x, y, layout.width(), layout.height(), Component.empty(), ignored -> { }, DEFAULT_NARRATION);
            this.id = id;
            this.layout = layout;
            this.title = title;
            this.side = side;
            active = false;
            visible = false;
        }

        public IconTabButton createTab(ItemStack icon) {
            return createTab(icon, 0);
        }

        public IconTabButton createTab(ItemStack icon, int iconOffsetX) {
            return createTab(icon, iconOffsetX, 0);
        }

        public IconTabButton createTab(ItemStack icon, int iconOffsetX, int iconOffsetY) {
            IconTabButton button = new IconTabButton(
                tabX(), getY(), layout.tabWidth(), layout.tabHeight(),
                icon, iconOffsetX, iconOffsetY, title, ignored -> toggle());
            bindTab(button);
            return button;
        }

        public IconTabButton createTab(Identifier icon) {
            return createTab(icon, title);
        }

        public IconTabButton createTab(Identifier icon, Component tooltip) {
            IconTabButton button = new IconTabButton(
                tabX(), getY(), layout.tabWidth(), layout.tabHeight(), icon, tooltip, ignored -> toggle());
            bindTab(button, tooltip);
            return button;
        }

        private int tabX() {
            return side == Side.RIGHT ? getX() : getX() + layout.width() - layout.tabWidth();
        }

        public int contentWidth() {
            return layout.width();
        }

        public int contentHeight() {
            return layout.height();
        }

        private void bindTab(AbstractWidget tab) {
            bindTab(tab, title);
        }

        private void bindTab(AbstractWidget tab, Component tooltip) {
            this.tab = tab;
            tab.setTooltip(Tooltip.create(tooltip));
            refresh();
        }

        public void bindContents(AbstractWidget... contents) {
            this.contents.clear();
            this.contents.addAll(Arrays.asList(contents));
            refresh();
        }

        public void setContentRenderer(ContentRenderer contentRenderer) {
            this.contentRenderer = contentRenderer;
        }

        public boolean isOpen() {
            return open;
        }

        public void toggle() {
            setOpen(!open);
        }

        public void setOpen(boolean open) {
            if (open) {
                // 面板互斥：打开当前面板时立即收起其他面板。
                for (Panel panel : panels) {
                    if (panel != this) {
                        panel.open = false;
                    }
                }
            }
            this.open = open;
            refresh();
        }

        /** 绘制当前展开或收起状态的外框，供 Screen 控制基础层的绘制顺序。 */
        public void drawBackground(GuiGraphicsExtractor graphics) {
            int width = open ? layout.width() : layout.tabWidth();
            int height = open ? layout.height() : layout.tabHeight();
            int x = side == Side.RIGHT || open ? getX() : tabX();
            if (side == Side.RIGHT) {
                PixelGui.drawRightTabBackground(graphics, x, getY(), width, height);
            } else {
                PixelGui.drawLeftTabBackground(graphics, x, getY(), width, height);
            }
            if (open) {
                int titleX = side == Side.RIGHT ? getX() + 22 : getX() + 7;
                int titleY = getY() + (side == Side.RIGHT ? 8 : 7);
                graphics.text(font, title, titleX, titleY, 0xFF404040, false);
                contentRenderer.render(graphics, getX(), getY());
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // 面板背景由所属 Screen 按遮挡顺序统一绘制。
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // 覆盖层只负责绘制，不能拦截内部控件。
            return false;
        }

        private void applyState(boolean blocked) {
            if (tab != null) {
                // 展开的上方面板会遮住后续标签，隐藏的同时必须禁用点击。
                tab.visible = !blocked;
                tab.active = !blocked;
            }
            boolean contentEnabled = open && !blocked;
            visible = contentEnabled;
            for (AbstractWidget content : contents) {
                // 不可见控件不会采集 tooltip，因此遮挡时必须同时修改 visible。
                content.visible = contentEnabled;
                content.active = contentEnabled;
            }
        }
    }
}
