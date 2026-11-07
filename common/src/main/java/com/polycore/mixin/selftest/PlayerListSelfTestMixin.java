package com.polycore.mixin.selftest;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plays "another mod" that changes the simulation distance code. Does nothing; test runs only. */
@Mixin(PlayerList.class)
abstract class PlayerListSelfTestMixin {
    @Inject(method = "setSimulationDistance", at = @At("HEAD"))
    private void polycore$selfTest(int simulationDistance, CallbackInfo ci) {}
}
