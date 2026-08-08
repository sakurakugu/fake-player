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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

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
    public boolean isClientAuthoritative() {
        // 假玩家没有客户端负责物理运算，其自身和所控制载具都必须以服务端为权威。
        return false;
    }

    @Override
    public Vec3 getKnownMovement() {
        return knownServerMovement();
    }

    @Override
    public Vec3 getKnownSpeed() {
        return knownServerMovement();
    }

    private Vec3 knownServerMovement() {
        Entity vehicle = getVehicle();
        return vehicle != null && vehicle.getControllingPassenger() != this
            ? vehicle.getKnownMovement()
            : getDeltaMovement();
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
    public void showEndCredits() {
        // 假玩家没有客户端处理末地字幕和重生请求，直接保留当前实体返回重生点。
        seenCredits = true;
        teleport(findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force, boolean sendEventAndTriggers) {
        if (!super.startRiding(vehicle, force, sendEventAndTriggers)) {
            return false;
        }
        if (vehicle instanceof AbstractBoat) {
            // 真客户端收到乘客包时会同步船头方向，假玩家需要在服务端完成同样的初始化。
            yRotO = vehicle.getYRot();
            setYRot(vehicle.getYRot());
            setYHeadRot(vehicle.getYRot());
        }
        return true;
    }

    /** 移除假玩家前解除其与真人之间的骑乘关系，避免留下失效乘客引用。 */
    public void shakeOffPlayers() {
        if (getVehicle() instanceof Player) {
            stopRiding();
        }
        for (Entity passenger : getIndirectPassengers()) {
            if (passenger instanceof Player) {
                passenger.stopRiding();
            }
        }
    }

    @Override
    public ServerPlayer teleport(TeleportTransition transition) {
        ServerPlayer result = super.teleport(transition);
        if (result != null && result.isChangingDimension()) {
            // 真客户端会在接受传送后确认维度切换；假连接需要在服务端立即完成确认。
            result.hasChangedDimension();
        }
        return result;
    }

    @Override
    public void tick() {
        super.tick();
        boolean possessed = FakePlayerPossession.isPossessed(this);
        if (!possessed) {
            // 附身期间该实体承载原地躯壳，必须暂停所有自动行为。
            automation.tick();
            // 先设置移动和按键输入，再让实体刻处理物理、载具和持续使用。
            actions.tick();
        }
        // ServerPlayer 通常由网络监听器驱动 doTick，假连接不会替我们调用它。
        doTick();
        if (!possessed) {
            actions.restoreViewRotation();
        }
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
        shakeOffPlayers();
        super.die(source);
        // 延迟到服务器任务队列移除，避免在死亡处理过程中直接修改玩家列表。
        server.execute(() -> FakePlayerManager.remove(this));
    }
}
