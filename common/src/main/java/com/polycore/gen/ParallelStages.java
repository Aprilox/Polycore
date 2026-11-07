package com.polycore.gen;

import com.mojang.datafixers.util.Either;
import com.polycore.optim.Optimization;
import com.polycore.pregen.ParityHash;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

/**
 * Runs the SURFACE and CARVERS stages on the Polycore pool instead of the single "worldgen" mailbox thread.
 *
 * <p>In vanilla every generation stage except biomes and noise runs synchronously on one mailbox,
 * one chunk at a time, which becomes the bottleneck once noise is parallel. SURFACE and CARVERS only read
 * and write their own chunk (biomes come from the thread-safe noise sampler), so they can run concurrently.
 * This is the same set of statuses Paper/Moonrise mark as "parallel capable". FEATURES (writes neighbours)
 * and LIGHT stay serial.</p>
 */
public final class ParallelStages {
    private ParallelStages() {}

    private static Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> recordTerrain(
            ServerLevel level, Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result) {
        result.ifLeft(c -> ParityHash.recordTerrain(level, c));
        return result;
    }

    public static boolean isParallelCapable(ChunkStatus status) {
        return status == ChunkStatus.SURFACE || status == ChunkStatus.CARVERS;
    }

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> dispatch(
            ChunkStatus status, ServerLevel level, ChunkAccess chunk,
            Supplier<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> original) {
        boolean generating = chunk != null && !chunk.getStatus().isOrAfter(status);
        boolean record = generating && status == ChunkStatus.CARVERS && ParityHash.recording();
        if (!generating || !isParallelCapable(status) || !WorldgenHooks.isActive(Optimization.PARALLEL_STAGES)) {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> f = original.get();
            return record ? f.thenApply(r -> recordTerrain(level, r)) : f;
        }
        Optimization.PARALLEL_STAGES.markHookSeen();
        final String name = status == ChunkStatus.SURFACE ? "surface" : "carvers";
        return CompletableFuture.supplyAsync(() -> {
            long t0 = System.nanoTime();
            try {
                CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> f = original.get();
                return record ? f.thenApply(r -> recordTerrain(level, r)) : f;
            } finally {
                long dt = System.nanoTime() - t0;
                WorldgenPool.recordBusy(dt);
                if (StepProfiler.enabled()) StepProfiler.recordCalc(name, dt);
            }
        }, WorldgenPool.get()).thenCompose(Function.identity());
    }
}
