package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.lag.LagCapture;
import com.polycore.lag.LagMonitor;
import com.polycore.lag.LagMonitor.Section;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lag analyzer: times each part of a level tick, and each entity during a detailed analysis.
 * Observation only: every call runs exactly as before.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    /** Block and fluid scheduled ticks (redstone, fluids...). */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"))
    private void polycore$scheduledTicks(LevelTicks<?> ticks, long time, int max, BiConsumer<?, ?> ticker, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(ticks, time, max, ticker);
        LagMonitor.add(Section.BLOCK_TICKS, System.nanoTime() - t);
    }

    /** Pistons, note blocks and other block events. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;runBlockEvents()V"))
    private void polycore$blockEvents(ServerLevel level, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(level);
        LagMonitor.add(Section.BLOCK_TICKS, System.nanoTime() - t);
    }

    /** Chunk loading, unloading and saving (mob spawning and random ticks are split off in ServerChunkCacheMixin). */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tick(Ljava/util/function/BooleanSupplier;Z)V"))
    private void polycore$chunks(ServerChunkCache cache, BooleanSupplier hasTimeLeft, boolean tickChunks, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(cache, hasTimeLeft, tickChunks);
        LagMonitor.add(Section.CHUNKS, System.nanoTime() - t);
    }

    /** Entity loading and unloading with their chunks. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;tick()V"))
    private void polycore$entityManager(PersistentEntitySectionManager<?> manager, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(manager);
        LagMonitor.add(Section.CHUNKS, System.nanoTime() - t);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void polycore$entities(EntityTickList list, Consumer<Entity> action, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(list, action);
        LagMonitor.add(Section.ENTITIES, System.nanoTime() - t);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickBlockEntities()V"))
    private void polycore$blockEntities(ServerLevel level, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(level);
        LagMonitor.add(Section.BLOCK_ENTITIES, System.nanoTime() - t);
    }

    /** Start of the entity being timed (0: none), set and read on the server thread only. */
    @Unique private long polycore$entityStart;

    // Two plain injections instead of @WrapMethod: NeoForge 1.21 ships MixinExtras 0.3.5, which predates @WrapMethod
    /** Detailed analysis: one entity with its passengers. */
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void polycore$entityStart(Entity entity, CallbackInfo ci) {
        polycore$entityStart = LagCapture.running() ? System.nanoTime() : 0;
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void polycore$entity(Entity entity, CallbackInfo ci) {
        if (polycore$entityStart != 0 && LagCapture.running()) LagCapture.entity(entity, System.nanoTime() - polycore$entityStart);
        polycore$entityStart = 0;
    }
}
