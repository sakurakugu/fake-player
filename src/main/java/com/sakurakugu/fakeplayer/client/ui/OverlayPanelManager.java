package com.sakurakugu.fakeplayer.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;

/** 按注册顺序管理重叠面板；后注册的面板位于更上层。 */
public final class OverlayPanelManager {
    private final List<Panel> panels = new ArrayList<>();

    public Panel addPanel() {
        Panel panel = new Panel();
        panels.add(panel);
        return panel;
    }

    private void refresh() {
        boolean blocked = false;
        for (int index = panels.size() - 1; index >= 0; index--) {
            Panel panel = panels.get(index);
            panel.applyState(blocked);
            if (panel.open) {
                blocked = true;
            }
        }
    }

    public final class Panel {
        private final List<AbstractWidget> contents = new ArrayList<>();
        private AbstractWidget tab;
        private boolean open;
        // 上层面板展开时暂时阻止本面板绘制展开内容和接收输入。
        private boolean blocked;

        private Panel() {
        }

        public void bind(AbstractWidget tab, AbstractWidget... contents) {
            this.tab = tab;
            this.contents.clear();
            this.contents.addAll(Arrays.asList(contents));
            refresh();
        }

        public boolean isOpen() {
            return open && !blocked;
        }

        public void toggle() {
            setOpen(!open);
        }

        public void setOpen(boolean open) {
            this.open = open;
            refresh();
        }

        private void applyState(boolean blocked) {
            this.blocked = blocked;
            if (tab != null) {
                tab.active = !blocked;
            }
            boolean contentEnabled = open && !blocked;
            for (AbstractWidget content : contents) {
                // 不可见控件不会采集 tooltip，因此遮挡时必须同时修改 visible。
                content.visible = contentEnabled;
                content.active = contentEnabled;
            }
        }
    }
}
