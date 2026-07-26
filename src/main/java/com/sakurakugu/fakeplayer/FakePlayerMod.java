package com.sakurakugu.fakeplayer;

import com.sakurakugu.fakeplayer.menu.ModMenus;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** 模组入口，负责注册需要挂载到模组事件总线的内容。 */
@Mod(FakePlayerMod.MOD_ID)
public final class FakePlayerMod {
    public static final String MOD_ID = "fakeplayer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FakePlayerMod(IEventBus modBus) {
        // 菜单类型必须在模组加载阶段注册，客户端界面则由客户端事件单独绑定。
        ModMenus.register(modBus);
    }
}
