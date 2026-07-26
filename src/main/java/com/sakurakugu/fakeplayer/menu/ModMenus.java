package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 集中声明并注册模组使用的菜单类型。 */
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FakePlayerMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FakePlayerMenu>> FAKE_PLAYER = MENUS.register(
        "control",
        // 扩展菜单类型允许 NeoForge 将打开菜单时的附加网络数据交给客户端构造器。
        () -> IMenuTypeExtension.create(FakePlayerMenu::new)
    );

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
