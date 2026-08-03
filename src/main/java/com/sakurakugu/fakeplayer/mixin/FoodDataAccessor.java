package com.sakurakugu.fakeplayer.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 补足原版饥饿数据缺少的读写接口，以便身体状态完整交换。 */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
    @Accessor("exhaustionLevel")
    float fakeplayer$getExhaustionLevel();

    @Accessor("exhaustionLevel")
    void fakeplayer$setExhaustionLevel(float value);

    @Accessor("tickTimer")
    int fakeplayer$getTickTimer();

    @Accessor("tickTimer")
    void fakeplayer$setTickTimer(int value);
}
