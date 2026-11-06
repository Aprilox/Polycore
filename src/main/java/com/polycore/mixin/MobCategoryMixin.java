package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.polycore.guard.TpsGuard;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TPS Guard, mob spawn limit: natural mob caps (global and per player) are scaled down while the measure is active.
 */
@Mixin(MobCategory.class)
abstract class MobCategoryMixin {

    @ModifyReturnValue(method = "getMaxInstancesPerChunk", at = @At("RETURN"))
    private int polycore$spawnLimit(int max) {
        return TpsGuard.scaleMobCap(max);
    }
}
