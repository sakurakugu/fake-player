package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;

/** 保存服务端最近一次确认的客户端设置。 */
public final class ClientGlobalSettings {
    private static Boolean containerTransferButtons;

    private ClientGlobalSettings() {
    }

    public static boolean containerTransferButtons() {
        return containerTransferButtons == null
            ? FakePlayerConfig.containerTransferButtons()
            : containerTransferButtons;
    }

    public static void setContainerTransferButtons(boolean enabled) {
        containerTransferButtons = enabled;
    }

    public static void clear() {
        containerTransferButtons = null;
    }
}
