package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.polycore.gen.ParallelStages;
import com.polycore.gen.StepProfiler;
import com.polycore.optim.Optimization;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatusTask;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into ChunkStep.apply: stage profiler (time from the call to the end of the work) and parallel stages.
 * The profiler also works with Moonrise, which calls ChunkStep.apply from its own scheduler.
 */
@Mixin(ChunkStep.class)
abstract class ChunkStepMixin {

    @Shadow
    public abstract ChunkStatus targetStatus();

    /**
     * Parallel stages: SURFACE and CARVERS run on the Polycore pool instead of the serial worldgen mailbox.
     */
    @WrapOperation(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/status/ChunkStatusTask;doWork(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<ChunkAccess> polycore$parallelStages(ChunkStatusTask task, WorldGenContext context, ChunkStep step,
                                                                  StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                                                  Operation<CompletableFuture<ChunkAccess>> original) {
        return ParallelStages.dispatch(this.targetStatus(), task, context, step, cache, chunk, original);
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void polycore$start(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir,
                                @Share("polycoreStart") LocalLongRef start,
                                @Share("polycoreGenerated") LocalBooleanRef generated) {
        start.set(System.nanoTime());
        // True if the chunk is really generated at this stage (false when it is just loaded from disk).
        generated.set(chunk != null && !chunk.getPersistedStatus().isOrAfter(this.targetStatus()));
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void polycore$end(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                              CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir,
                              @Share("polycoreStart") LocalLongRef start,
                              @Share("polycoreGenerated") LocalBooleanRef generated) {
        Optimization.STEP_PROFILER.markHookSeen();
        final String name = StepProfiler.shortName(this.targetStatus());
        final boolean full = "full".equals(name);
        if (!generated.get()) {
            if (full) StepProfiler.recordLoaded();
            return;
        }
        // Chunks are always counted (HUD, dashboard); stage times only when the profiler is on.
        final boolean profile = StepProfiler.enabled();
        if (!profile && !full) return;
        CompletableFuture<ChunkAccess> future = cir.getReturnValue();
        if (future == null) return;
        final long t0 = start.get();
        // Observation only: a callback is attached, the returned future is not changed.
        future.whenComplete((c, t) -> {
            if (profile) StepProfiler.record(name, System.nanoTime() - t0);
            else StepProfiler.countFull();
        });
    }
}
