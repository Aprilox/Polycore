package com.polycore.mixin;

import com.polycore.guard.GuardedMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TPS Guard, far mob AI for "brain" mobs (piglins, axolotls, goats...): the brain runs every 4 or 8 ticks while the
 * mob is far from every player. Villagers are never slowed (see TpsGuard.aiInterval).
 */
@Mixin(Brain.class)
abstract class BrainMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void polycore$throttle(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof GuardedMob mob && mob.polycore$skipAi()) ci.cancel();
    }
}
