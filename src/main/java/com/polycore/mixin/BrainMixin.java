package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.guard.GuardedMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TPS Guard, far mob AI for "brain" mobs (piglins, axolotls, goats, wardens...): while the mob is far from every
 * player, its sensors and the start of new behaviors run every 4 or 8 ticks; running behaviors and memories tick
 * every tick as in vanilla. Villagers are never slowed (see TpsGuard.aiInterval).
 */
@Mixin(Brain.class)
abstract class BrainMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;tickSensors(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void polycore$sensors(Brain<?> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (!(entity instanceof GuardedMob mob) || !mob.polycore$skipAi()) original.call(brain, level, entity);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;startEachNonRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void polycore$startBehaviors(Brain<?> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (!(entity instanceof GuardedMob mob) || !mob.polycore$skipAi()) original.call(brain, level, entity);
    }
}
