package com.sakurakugu.fakeplayer.client;

import com.sakurakugu.fakeplayer.network.OpenPossessedInventoryPayload;
import com.sakurakugu.fakeplayer.network.PossessionInputPayload;
import com.sakurakugu.fakeplayer.network.PossessionStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 客户端附身状态，负责切换摄像机并发送当前按键。 */
public final class ClientPossession {
    private static int targetEntityId = PossessionStatePayload.NONE;
    private static ClientInput savedInput;
    private static LocalPlayer inputOwner;

    private ClientPossession() {
    }

    public static void accept(PossessionStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.targetEntityId() == PossessionStatePayload.NONE) {
            clear(minecraft);
            return;
        }

        clear(minecraft);
        targetEntityId = payload.targetEntityId();
        if (minecraft.player != null) {
            inputOwner = minecraft.player;
            savedInput = minecraft.player.input;
            minecraft.player.input = new ClientInput();
        }
        minecraft.setScreen(null);
        updateCamera(minecraft);
    }

    public static boolean active() {
        return targetEntityId != PossessionStatePayload.NONE;
    }

    public static void tickPre(Minecraft minecraft) {
        if (!active()) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.player != inputOwner) {
            clear(minecraft);
            return;
        }
        while (minecraft.options.keyInventory.consumeClick()) {
            if (minecraft.screen == null) {
                ClientPacketDistributor.sendToServer(new OpenPossessedInventoryPayload());
            }
        }
    }

    public static void tickPost(Minecraft minecraft) {
        if (!active() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Entity target = updateCamera(minecraft);
        if (target == null) {
            return;
        }

        copyViewRotation();
        boolean acceptsMovement = minecraft.screen == null;
        float forward = acceptsMovement ? axis(minecraft.options.keyUp.isDown(), minecraft.options.keyDown.isDown()) : 0.0F;
        float strafe = acceptsMovement ? axis(minecraft.options.keyLeft.isDown(), minecraft.options.keyRight.isDown()) : 0.0F;
        ClientPacketDistributor.sendToServer(new PossessionInputPayload(
            forward,
            strafe,
            minecraft.player.getYRot(),
            minecraft.player.getXRot(),
            acceptsMovement && minecraft.options.keyJump.isDown(),
            acceptsMovement && minecraft.options.keyShift.isDown(),
            acceptsMovement && minecraft.options.keySprint.isDown(),
            acceptsMovement && minecraft.options.keyAttack.isDown(),
            acceptsMovement && minecraft.options.keyUse.isDown()
        ));
    }

    /** 鼠标处理器每次更新本体朝向后，同步到当前摄像机实体以保证视角流畅。 */
    public static void copyViewRotation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetEntityId);
        if (target != null) {
            target.setYRot(minecraft.player.getYRot());
            target.setXRot(minecraft.player.getXRot());
            target.setYHeadRot(minecraft.player.getYRot());
        }
    }

    private static Entity updateCamera(Minecraft minecraft) {
        Entity target = minecraft.level == null ? null : minecraft.level.getEntity(targetEntityId);
        if (target != null && minecraft.getCameraEntity() != target) {
            minecraft.setCameraEntity(target);
        }
        return target;
    }

    private static void clear(Minecraft minecraft) {
        if (inputOwner != null && inputOwner == minecraft.player && savedInput != null) {
            inputOwner.input = savedInput;
        }
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        targetEntityId = PossessionStatePayload.NONE;
        savedInput = null;
        inputOwner = null;
    }

    private static float axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
    }
}
