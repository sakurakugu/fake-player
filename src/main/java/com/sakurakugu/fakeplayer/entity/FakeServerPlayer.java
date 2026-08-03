package com.sakurakugu.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.fakeplayer.automation.FakePlayerAutomation;
import com.sakurakugu.fakeplayer.persistence.FakePlayerPersistence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** 没有真实客户端连接、但参与原版服务端玩家逻辑的假玩家实体。 */
public final class FakeServerPlayer extends ServerPlayer {
    private final MinecraftServer server;
    private final FakePlayerActions actions;
    private final FakePlayerAutomation automation;

    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        this(server, level, profile, ClientInformation.createDefault());
    }

    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
        this.server = server;
        this.actions = new FakePlayerActions(this);
        this.automation = new FakePlayerAutomation(this);
    }

    public FakePlayerActions actions() {
        return actions;
    }

    public FakePlayerAutomation automation() {
        return automation;
    }

    public MinecraftServer server() {
        return server;
    }

    @Override
    public Component getTabListDisplayName() {
        Component displayName = super.getTabListDisplayName();
        if (displayName == null) {
            displayName = getDisplayName();
        }
        return displayName.copy().append(Component.translatable("gui.fakeplayer.tab_marker").withStyle(ChatFormatting.DARK_GRAY));
    }

    public void showAllSkinLayers() {
        // 该位掩码对应披风、外套、袖子、裤腿和帽子等全部皮肤附加层。
        getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!FakePlayerPossession.isPossessed(this)) {
            // 附身期间该实体承载原地躯壳，必须暂停所有自动行为。
            automation.tick();
            // 先设置移动和按键输入，再让实体刻处理物理、载具和持续使用。
            actions.tick();
        }
        // ServerPlayer 通常由网络监听器驱动 doTick，假连接不会替我们调用它。
        doTick();
        FakePlayerPossession.tickTarget(this);

        // 定期刷新网络位置和区块追踪，保证移动后的假玩家对观察者可见。
        if (tickCount % 10 == 0) {
            connection.resetPosition();
            level().getChunkSource().move(this);
        }
        if (tickCount % 20 == 0 && !FakePlayerPossession.isPossessed(this)) {
            FakePlayerPersistence.track(this);
        }
    }

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        // 假玩家不出现在服务端状态查询返回的公开玩家样本中。
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 延迟到服务器任务队列移除，避免在死亡处理过程中直接修改玩家列表。
        server.execute(() -> FakePlayerManager.remove(this));
    }
}
