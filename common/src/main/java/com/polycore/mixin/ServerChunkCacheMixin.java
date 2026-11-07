package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.lag.LagMonitor;
import com.polycore.lag.LagMonitor.Section;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lag analyzer: splits mob spawning and random ticks out of the chunk time measured in ServerLevelMixin.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tickChunks()V"))
    private void polycore$spawning(ServerChunkCache cache, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(cache);
        long dt = System.nanoTime() - t;
        LagMonitor.add(Section.SPAWNING, dt);
        LagMonitor.add(Section.CHUNKS, -dt); // already inside the CHUNKS measurement
    }
}
