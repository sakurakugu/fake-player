package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.menu.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 仅在物理客户端加载的界面注册事件。 */
@EventBusSubscriber(modid = FakePlayerMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // 将服务端打开的菜单类型映射到客户端实际绘制的界面。
        event.register(ModMenus.FAKE_PLAYER.get(), FakePlayerScreen::new);
        event.register(ModMenus.GLOBAL_FAKE_PLAYER.get(), GlobalFakePlayerScreen::new);
    }
}
