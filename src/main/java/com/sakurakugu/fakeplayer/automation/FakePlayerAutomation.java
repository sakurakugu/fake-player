package com.sakurakugu.fakeplayer.automation;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** 处理单个假玩家的补货、工具替换和自动钓鱼。 */
public final class FakePlayerAutomation {
    private static final int TOOL_DURABILITY_THRESHOLD = 10;
    private static final int FISH_REEL_DELAY = 5;
    private static final int FISH_RECAST_DELAY = 10;

    private final FakeServerPlayer player;
    private FishingHook pendingHook;
    private InteractionHand fishingHand;
    private int reelDelay = -1;
    private int recastDelay = -1;

    public FakePlayerAutomation(FakeServerPlayer player) {
        this.player = player;
    }

    public void tick() {
        if (FakePlayerConfig.autoReplaceTools()) {
            replaceWornTool(EquipmentSlot.MAINHAND);
            replaceWornTool(EquipmentSlot.OFFHAND);
        }
        if (FakePlayerConfig.autoReplenishment()) {
            replenish(player.getMainHandItem());
            replenish(player.getOffhandItem());
        }
        tickAutoFishing();
    }

    /** 该方法只由浮漂 Mixin 在咬钩状态由 false 切换为 true 时调用。 */
    public void onFishBite(FishingHook hook) {
        if (!FakePlayerConfig.autoFishing() || player.fishing != hook || pendingHook == hook) {
            return;
        }
        InteractionHand hand = findFishingRodHand();
        if (hand == null) {
            return;
        }
        pendingHook = hook;
        fishingHand = hand;
        reelDelay = FISH_REEL_DELAY;
        recastDelay = -1;
    }

    private void tickAutoFishing() {
        if (!FakePlayerConfig.autoFishing()) {
            clearFishingPlan();
            return;
        }
        if (reelDelay >= 0 && --reelDelay <= 0) {
            reelDelay = -1;
            if (pendingHook != null && pendingHook.isAlive() && player.fishing == pendingHook && useFishingRod(fishingHand)) {
                recastDelay = FISH_RECAST_DELAY;
            } else {
                clearFishingPlan();
            }
            return;
        }
        if (recastDelay >= 0 && --recastDelay <= 0) {
            InteractionHand hand = isFishingRod(fishingHand) ? fishingHand : findFishingRodHand();
            if (player.fishing == null && hand != null) {
                useFishingRod(hand);
            }
            clearFishingPlan();
        }
    }

    private boolean useFishingRod(InteractionHand hand) {
        if (!isFishingRod(hand)) {
            return false;
        }
        InteractionResult result = player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
        if (result instanceof InteractionResult.Success success
            && success.swingSource() != InteractionResult.SwingSource.NONE) {
            player.swing(hand);
        }
        if (result.consumesAction()) {
            player.resetLastActionTime();
            return true;
        }
        return false;
    }

    private InteractionHand findFishingRodHand() {
        if (isFishingRod(InteractionHand.MAIN_HAND)) {
            return InteractionHand.MAIN_HAND;
        }
        return isFishingRod(InteractionHand.OFF_HAND) ? InteractionHand.OFF_HAND : null;
    }

    private boolean isFishingRod(InteractionHand hand) {
        return hand != null && player.getItemInHand(hand).getItem() instanceof FishingRodItem;
    }

    private void clearFishingPlan() {
        pendingHook = null;
        fishingHand = null;
        reelDelay = -1;
        recastDelay = -1;
    }

    private void replaceWornTool(EquipmentSlot equipmentSlot) {
        ItemStack equipped = player.getItemBySlot(equipmentSlot);
        if (!equipped.isDamageableItem() || remainingDurability(equipped) > TOOL_DURABILITY_THRESHOLD) {
            return;
        }

        int bestSlot = -1;
        int bestDurability = TOOL_DURABILITY_THRESHOLD;
        for (int slot = 0; slot < 36; slot++) {
            // 副手替换不应抢走当前主手，主手自身也不是备用槽位。
            if (slot == player.getInventory().getSelectedSlot()) {
                continue;
            }
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate == equipped || candidate.isEmpty() || !candidate.is(equipped.getItem())) {
                continue;
            }
            int durability = remainingDurability(candidate);
            if (durability > bestDurability) {
                bestSlot = slot;
                bestDurability = durability;
            }
        }
        if (bestSlot < 0) {
            return;
        }

        ItemStack replacement = player.getInventory().getItem(bestSlot);
        player.getInventory().setItem(bestSlot, equipped);
        player.setItemSlot(equipmentSlot, replacement);
        player.getInventory().setChanged();
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
    }

    private void replenish(ItemStack held) {
        if (held.isEmpty()) {
            return;
        }
        int maxStackSize = held.getMaxStackSize();
        int threshold = maxStackSize / 8;
        int target = maxStackSize / 2;
        if (maxStackSize <= 1 || held.getCount() > threshold || target <= threshold) {
            return;
        }

        int needed = target - held.getCount();
        int originalNeeded = needed;
        for (int slot = 0; slot < 36; slot++) {
            // 只从备用槽位补货，避免副手反向抽空主手。
            if (slot == player.getInventory().getSelectedSlot()) {
                continue;
            }
            ItemStack candidate = player.getInventory().getItem(slot);
            if (needed <= 0) {
                break;
            }
            if (candidate == held || candidate.isEmpty() || !ItemStack.isSameItemSameComponents(candidate, held)) {
                continue;
            }
            int moved = Math.min(needed, candidate.getCount());
            candidate.shrink(moved);
            held.grow(moved);
            needed -= moved;
        }

        if (needed > 0 && FakePlayerConfig.autoReplenishmentFromShulkerBoxes()) {
            for (int slot = 0; slot < 36; slot++) {
                if (slot == player.getInventory().getSelectedSlot()) {
                    continue;
                }
                ItemStack candidate = player.getInventory().getItem(slot);
                if (needed <= 0) {
                    break;
                }
                needed -= takeFromShulkerBox(candidate, held, needed);
            }
        }
        if (needed < originalNeeded) {
            player.getInventory().setChanged();
        }
    }

    private static int takeFromShulkerBox(ItemStack container, ItemStack requested, int limit) {
        if (!(container.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof ShulkerBoxBlock)
            || limit <= 0) {
            return 0;
        }
        ItemContainerContents contents = container.get(DataComponents.CONTAINER);
        if (contents == null || contents.getSlots() == 0) {
            return 0;
        }

        List<ItemStack> updated = new ArrayList<>(contents.getSlots());
        int moved = 0;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (moved < limit && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, requested)) {
                int amount = Math.min(limit - moved, stack.getCount());
                stack.shrink(amount);
                moved += amount;
            }
            updated.add(stack);
        }
        if (moved > 0) {
            container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(updated));
            requested.grow(moved);
        }
        return moved;
    }
}
