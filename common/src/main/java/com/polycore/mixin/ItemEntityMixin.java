package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.guard.TpsGuard;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TPS Guard, item merging: items look for stacks to merge with within 2 blocks instead of half a block.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {

    @WrapOperation(method = "mergeWithNeighbours", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB polycore$mergeRadius(AABB box, double x, double y, double z, Operation<AABB> original) {
        return TpsGuard.itemMergeActive() ? original.call(box, 2.0, y, 2.0) : original.call(box, x, y, z);
    }
}
