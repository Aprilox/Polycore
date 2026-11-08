package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import com.polycore.gen.ParallelStages;
import com.polycore.gen.StepProfiler;
import com.polycore.optim.Optimization;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into ChunkStatus.generate (1.19.4: with a regenerate flag; called only for a chunk not yet at this status; ChunkStatus.load handles
 * chunks read from disk): stage profiler (time from the call to the end of the work) and parallel stages.
 */
@Mixin(ChunkStatus.class)
abstract class ChunkStatusMixin {

    @WrapMethod(method = "generate")
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> polycore$generate(
            Executor executor, ServerLevel level, ChunkGenerator generator, StructureTemplateManager templates,
            ThreadedLevelLightEngine light,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> task,
            List<ChunkAccess> cache, boolean regenerate, Operation<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> original) {
        ChunkStatus status = (ChunkStatus) (Object) this;
        ChunkAccess chunk = cache.get(cache.size() / 2);
        final long t0 = System.nanoTime();
        // Parallel stages: SURFACE and CARVERS run on the Polycore pool instead of the serial worldgen mailbox.
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = ParallelStages.dispatch(
                status, level, chunk, () -> original.call(executor, level, generator, templates, light, task, cache, regenerate));
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
    private void polycore$load(CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if ((Object) this == ChunkStatus.FULL) StepProfiler.recordLoaded();
    }
}
