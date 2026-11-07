package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.guard.TpsGuard;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TPS Guard, XP merging: experience orbs merge within 2 blocks instead of half a block, unless another mod already
 * manages how XP orbs merge (util/Conflicts, XP_MERGE).
 */
@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {

    @WrapOperation(method = "scanForEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"))
    private AABB polycore$mergeRadius(AABB box, double radius, Operation<AABB> original) {
        return original.call(box, TpsGuard.xpMergeActive() ? 2.0 : radius);
    }
}
