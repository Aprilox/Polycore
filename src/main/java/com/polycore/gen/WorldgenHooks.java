package com.polycore.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.polycore.optim.Optimization;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Code appele par les mixins. Garde la logique hors des mixins pour qu'elle soit simple a lire et a tester.
 */
public final class WorldgenHooks {
    /**
     * Force l'utilisation (true) ou non (false) du pool pendant un benchmark. null = suit l'optimisation.
     */
    public static volatile Boolean poolOverride = null;

    private WorldgenHooks() {}

    public static boolean usePool() {
        Boolean o = poolOverride;
        if (o != null) return o && Optimization.WORLDGEN_POOL.blockReason() == null;
        return Optimization.WORLDGEN_POOL.isActive();
    }

    /**
     * Remplace l'executeur vanilla de CompletableFuture.supplyAsync(supplier, executor)
     * dans NoiseBasedChunkGenerator (createBiomes et fillFromNoise).
     */
    public static <U> CompletableFuture<U> dispatch(String what, Supplier<U> supplier, Executor vanillaExecutor,
                                                    Operation<CompletableFuture<U>> original) {
        Optimization.WORLDGEN_POOL.markHookSeen();
        final boolean pool = usePool();
        final boolean profile = StepProfiler.enabled();
        Supplier<U> timed = () -> {
            long t0 = System.nanoTime();
            try {
                return supplier.get();
            } finally {
                long dt = System.nanoTime() - t0;
                if (profile) StepProfiler.recordCalc(what, dt);
                if (pool) WorldgenPool.recordBusy(dt);
            }
        };
        Executor executor = pool ? WorldgenPool.get() : vanillaExecutor;
        return original.call(timed, executor);
    }
}
