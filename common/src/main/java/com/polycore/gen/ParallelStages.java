package com.polycore.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.polycore.optim.Optimization;
import com.polycore.pregen.ParityHash;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatusTask;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

/**
 * Runs the SURFACE and CARVERS stages on the Polycore pool instead of the single "worldgen" mailbox thread.
 *
 * <p>In vanilla 1.21.1 every generation stage except biomes and noise runs synchronously on one mailbox,
 * one chunk at a time, which becomes the bottleneck once noise is parallel. SURFACE and CARVERS only read
 * and write their own chunk (biomes come from the thread-safe noise sampler), so they can run concurrently.
 * This is the same set of statuses Paper/Moonrise mark as "parallel capable". FEATURES (writes neighbours)
 * and LIGHT stay serial.</p>
 */
public final class ParallelStages {
    private ParallelStages() {}

    private static ChunkAccess recordTerrain(WorldGenContext context, ChunkAccess chunk) {
        if (chunk != null) ParityHash.recordTerrain(context.level(), chunk);
        return chunk;
    }

    public static boolean isParallelCapable(ChunkStatus status) {
        return status == ChunkStatus.SURFACE || status == ChunkStatus.CARVERS;
    }

    public static CompletableFuture<ChunkAccess> dispatch(ChunkStatus status, ChunkStatusTask task, WorldGenContext context,
                                                          ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
                                                          ChunkAccess chunk,
                                                          Operation<CompletableFuture<ChunkAccess>> original) {
        boolean generating = chunk != null && chunk.getPersistedStatus().isBefore(status);
        boolean record = generating && status == ChunkStatus.CARVERS && ParityHash.recording();
        if (!generating || !isParallelCapable(status) || !WorldgenHooks.isActive(Optimization.PARALLEL_STAGES)) {
            CompletableFuture<ChunkAccess> f = original.call(task, context, step, cache, chunk);
            return record ? f.thenApply(c -> recordTerrain(context, c)) : f;
        }
        Optimization.PARALLEL_STAGES.markHookSeen();
        final String name = status == ChunkStatus.SURFACE ? "surface" : "carvers";
        return CompletableFuture.supplyAsync(() -> {
            long t0 = System.nanoTime();
            try {
                CompletableFuture<ChunkAccess> f = original.call(task, context, step, cache, chunk);
                return record ? f.thenApply(c -> recordTerrain(context, c)) : f;
            } finally {
                long dt = System.nanoTime() - t0;
                WorldgenPool.recordBusy(dt);
                if (StepProfiler.enabled()) StepProfiler.recordCalc(name, dt);
            }
        }, WorldgenPool.get()).thenCompose(Function.identity());
    }
}
