package com.polycore;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Fichier config/polycore-common.toml.
 * Les bascules faites en jeu (menu ou /polycore toggle) sont temporaires :
 * pour les rendre permanentes, modifie ce fichier.
 */
public final class PolyConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue WORLDGEN_POOL_ENABLED;
    public static final ModConfigSpec.IntValue WORLDGEN_POOL_THREADS;
    public static final ModConfigSpec.IntValue WORLDGEN_POOL_PRIORITY;

    public static final ModConfigSpec.BooleanValue PROFILER_ENABLED;

    public static final ModConfigSpec.IntValue PREGEN_CONCURRENCY;
    public static final ModConfigSpec.IntValue PREGEN_SAVE_EVERY;
    public static final ModConfigSpec.IntValue PREGEN_TIMEOUT_SECONDS;

    public static final ModConfigSpec.IntValue BENCH_DEFAULT_RADIUS;
    public static final ModConfigSpec.IntValue BENCH_BASELINE_CONCURRENCY;

    static {
        B.comment("Thread pool dedicated to terrain computation (noise + biomes)").push("worldgenPool");
        WORLDGEN_POOL_ENABLED = B
                .comment("Enables the dedicated pool. Frees Minecraft's background pool for the generation queue and lighting.")
                .define("enabled", true);
        WORLDGEN_POOL_THREADS = B
                .comment("Number of threads. 0 = automatic (cores - 2, minimum 1).")
                .defineInRange("threads", 0, 0, 256);
        WORLDGEN_POOL_PRIORITY = B
                .comment("Thread priority (1 to 10, 5 = normal). Mostly effective on Windows.")
                .defineInRange("priority", 5, 1, 10);
        B.pop();

        B.comment("Worldgen stage profiler").push("profiler");
        PROFILER_ENABLED = B
                .comment("Measures the time of each stage (biomes, noise, surface, features...). Very low overhead.")
                .define("enabled", true);
        B.pop();

        B.comment("Parallel pre-generation (/polycore pregen)").push("pregen");
        PREGEN_CONCURRENCY = B
                .comment("Number of chunks requested at once. 0 = automatic (cores x 8, between 32 and 512).",
                        "For comparison, /neoforge generate requests 32.")
                .defineInRange("concurrency", 0, 0, 4096);
        PREGEN_SAVE_EVERY = B
                .comment("Save chunks every N generated chunks (0 = never, regular autosave is enough).")
                .defineInRange("saveEvery", 2000, 0, 1_000_000);
        PREGEN_TIMEOUT_SECONDS = B
                .comment("Give up on a chunk that is not done after N seconds (counted as an error).")
                .defineInRange("timeoutSeconds", 120, 10, 3600);
        B.pop();

        B.comment("Benchmark A/B (/polycore bench)").push("bench");
        BENCH_DEFAULT_RADIUS = B
                .comment("Radius in chunks of each benchmark phase (10 = 441 chunks per phase).")
                .defineInRange("defaultRadius", 10, 2, 64);
        BENCH_BASELINE_CONCURRENCY = B
                .comment("Parallel chunks for the 'baseline' phase (32 = same as /neoforge generate).")
                .defineInRange("baselineConcurrency", 32, 1, 4096);
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private PolyConfig() {}

    public static int autoPoolThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, cores - 2);
    }

    public static int poolThreads() {
        int v = safeGet(WORLDGEN_POOL_THREADS, 0);
        return v <= 0 ? autoPoolThreads() : v;
    }

    public static int pregenConcurrency() {
        int v = safeGet(PREGEN_CONCURRENCY, 0);
        if (v > 0) return v;
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(32, Math.min(512, cores * 8));
    }

    /** Lit une valeur meme si la config n'est pas encore chargee (retourne la valeur par defaut). */
    public static int safeGet(ModConfigSpec.IntValue v, int def) {
        try {
            return v.get();
        } catch (Throwable t) {
            return def;
        }
    }

    public static boolean safeGet(ModConfigSpec.BooleanValue v, boolean def) {
        try {
            return v.get();
        } catch (Throwable t) {
            return def;
        }
    }
}
