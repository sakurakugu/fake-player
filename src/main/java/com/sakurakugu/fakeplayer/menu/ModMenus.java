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

    public static final DeferredHolder<MenuType<?>, MenuType<GlobalFakePlayerMenu>> GLOBAL_FAKE_PLAYER = MENUS.register(
        "global",
        () -> IMenuTypeExtension.create(GlobalFakePlayerMenu::new)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<FakePlayerInventoryMenu>> FAKE_PLAYER_INVENTORY = MENUS.register(
        "inventory",
        () -> IMenuTypeExtension.create(FakePlayerInventoryMenu::new)
    );

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
