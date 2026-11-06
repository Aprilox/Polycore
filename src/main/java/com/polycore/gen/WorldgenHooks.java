package com.polycore.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.polycore.config.PolyConfig;
import com.polycore.optim.Optimization;
import com.polycore.util.Conflicts;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Logic called from the mixins, kept out of them so it stays readable.
 */
public final class WorldgenHooks {
    /**
     * During a benchmark: true forces every generation optimization on, false forces them off. null = follow the config.
     */
    public static volatile Boolean benchOverride = null;

    private WorldgenHooks() {}

    /** Whether a generation optimization should act right now (takes the benchmark override into account). */
    public static boolean isActive(Optimization o) {
        Boolean override = benchOverride;
        if (override != null) return override && o.blockReason() == null;
        return o.isActive();
    }

    /** Read for every air block: a few volatile reads, no lookup. */
    public static boolean fastAquifer() {
        Boolean override = benchOverride;
        boolean on = override != null ? override : PolyConfig.get().fastAquiferEnabled;
        return on && !Conflicts.blocked(Conflicts.Feature.FAST_AQUIFER);
    }

    public static boolean usePool() {
        return isActive(Optimization.WORLDGEN_POOL);
    }

    /**
     * Replaces the vanilla executor of CompletableFuture.supplyAsync(supplier, executor)
     * in NoiseBasedChunkGenerator (createBiomes and fillFromNoise).
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
