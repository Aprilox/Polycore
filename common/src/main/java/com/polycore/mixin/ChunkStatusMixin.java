package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.polycore.gen.ParallelStages;
import com.polycore.gen.StepProfiler;
import com.polycore.optim.Optimization;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ToFullChunk;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into ChunkStatus.generate (1.20.6: called only for a chunk not yet at this status; ChunkStatus.load handles
 * chunks read from disk): stage profiler (time from the call to the end of the work) and parallel stages.
 */
@Mixin(ChunkStatus.class)
abstract class ChunkStatusMixin {

    @WrapMethod(method = "generate")
    private CompletableFuture<ChunkAccess> polycore$generate(WorldGenContext context, Executor executor, ToFullChunk toFullChunk,
                                                            List<ChunkAccess> cache, Operation<CompletableFuture<ChunkAccess>> original) {
        ChunkStatus status = (ChunkStatus) (Object) this;
        ChunkAccess chunk = cache.get(cache.size() / 2);
        final long t0 = System.nanoTime();
        // Parallel stages: SURFACE and CARVERS run on the Polycore pool instead of the serial worldgen mailbox.
        CompletableFuture<ChunkAccess> future = ParallelStages.dispatch(
                status, context, chunk, () -> original.call(context, executor, toFullChunk, cache));
        Optimization.STEP_PROFILER.markHookSeen();
        final String name = StepProfiler.shortName(status);
        final boolean profile = StepProfiler.enabled();
        // Chunks are always counted (HUD, dashboard); stage times only when the profiler is on.
        if (future == null || (!profile && !"full".equals(name))) return future;
        // Observation only: a callback is attached, the returned future is not changed.
        future.whenComplete((c, t) -> {
            if (profile) StepProfiler.record(name, System.nanoTime() - t0);
            else StepProfiler.countFull();
        });
        return future;
    }

    /** A chunk read from disk reaches FULL through load(), not generate(). */
    @Inject(method = "load", at = @At("HEAD"))
    private void polycore$load(CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if ((Object) this == ChunkStatus.FULL) StepProfiler.recordLoaded();
    }
}
