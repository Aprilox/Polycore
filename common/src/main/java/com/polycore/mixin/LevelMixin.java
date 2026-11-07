package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.lag.LagCapture;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lag analyzer, detailed analysis: times each ticking block entity. Costs one volatile read per block entity
 * when no analysis is running.
 */
@Mixin(Level.class)
abstract class LevelMixin {

    @WrapOperation(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void polycore$blockEntity(TickingBlockEntity blockEntity, Operation<Void> original) {
        if (!LagCapture.running()) {
            original.call(blockEntity);
            return;
        }
        long t = System.nanoTime();
        original.call(blockEntity);
        LagCapture.blockEntity((Level) (Object) this, blockEntity, System.nanoTime() - t);
    }
}
