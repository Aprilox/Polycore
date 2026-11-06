package com.polycore.settings;

import com.polycore.config.PolyConfig;
import com.polycore.gen.WorldgenPool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.ListTag;

/**
 * Every server setting exposed in the menu, the vanilla chest menu and /polycore set.
 */
public final class Settings {
    public static final String WORLDGEN_POOL = "worldgenPool.enabled";
    public static final String POOL_THREADS = "worldgenPool.threads";
    public static final String POOL_PRIORITY = "worldgenPool.priority";
    public static final String PARALLEL_STAGES = "parallelStages.enabled";
    public static final String PROFILER = "profiler.enabled";
    public static final String PREGEN_CONCURRENCY = "pregen.concurrency";
    public static final String PREGEN_KEEP_TPS = "pregen.keepTps";
    public static final String PREGEN_TARGET_MSPT = "pregen.targetMspt";
    public static final String PREGEN_SAVE_EVERY = "pregen.saveEvery";
    public static final String BENCH_RADIUS = "bench.defaultRadius";
    public static final String BENCH_BASELINE = "bench.baselineConcurrency";
    public static final String NON_OP_VIEW = "permissions.allowNonOpView";
    public static final String LAG_THRESHOLD = "lag.thresholdMs";
    public static final String LAG_AUTO = "lag.autoCapture";
    public static final String LAG_SECONDS = "lag.captureSeconds";

    private static final Map<String, Setting> ALL = new LinkedHashMap<>();

    static {
        add(Setting.bool(WORLDGEN_POOL, "Generation", "Dedicated worldgen pool",
                "Noise and biome computation run on Polycore's own threads.",
                () -> PolyConfig.get().worldgenPoolEnabled, v -> PolyConfig.get().worldgenPoolEnabled = v));
        add(Setting.bool(PARALLEL_STAGES, "Generation", "Parallel surface & carvers",
                "Surface and carver stages run in parallel instead of one chunk at a time.",
                () -> PolyConfig.get().parallelStagesEnabled, v -> PolyConfig.get().parallelStagesEnabled = v));
        add(Setting.integer(POOL_THREADS, "Generation", "Pool threads",
                "Threads used by the Polycore pool. Auto = cores - 2. Never more than this machine's logical cores.",
                0, PolyConfig::cores, "Auto", "",
                () -> PolyConfig.get().worldgenPoolThreads, v -> PolyConfig.get().worldgenPoolThreads = v));
        add(Setting.integer(POOL_PRIORITY, "Generation", "Pool thread priority",
                "1 to 10, 5 = normal. Lower it to favour the main server thread.", 1, () -> 10, null, "",
                () -> PolyConfig.get().worldgenPoolPriority, v -> PolyConfig.get().worldgenPoolPriority = v));
        add(Setting.bool(PROFILER, "Generation", "Stage profiler",
                "Measures the time of each generation stage. Very low overhead.",
                () -> PolyConfig.get().profilerEnabled, v -> PolyConfig.get().profilerEnabled = v));

        add(Setting.integer(PREGEN_CONCURRENCY, "Pre-generation", "Chunks in flight",
                "Maximum chunks requested at once. Auto = cores x 8 (32 to 512).", 0, () -> 4096, "Auto", "",
                () -> PolyConfig.get().pregenConcurrency, v -> PolyConfig.get().pregenConcurrency = v));
        add(Setting.bool(PREGEN_KEEP_TPS, "Pre-generation", "Keep TPS stable",
                "Slows pre-generation down automatically when the server starts to lag.",
                () -> PolyConfig.get().pregenKeepTps, v -> PolyConfig.get().pregenKeepTps = v));
        add(Setting.integer(PREGEN_TARGET_MSPT, "Pre-generation", "Target tick time",
                "Tick time the adaptive mode tries to stay under. 50 ms = 20 TPS.", 10, () -> 50, null, "ms",
                () -> PolyConfig.get().pregenTargetMspt, v -> PolyConfig.get().pregenTargetMspt = v));
        add(Setting.integer(PREGEN_SAVE_EVERY, "Pre-generation", "Save every",
                "Periodic save during pre-generation, in chunks. 0 = rely on autosave.", 0, () -> 1_000_000, "Never", "chunks",
                () -> PolyConfig.get().pregenSaveEvery, v -> PolyConfig.get().pregenSaveEvery = v));

        add(Setting.integer(BENCH_RADIUS, "Benchmark", "Default radius",
                "Radius of each benchmark phase, in chunks.", 2, () -> 64, null, "chunks",
                () -> PolyConfig.get().benchDefaultRadius, v -> PolyConfig.get().benchDefaultRadius = v));
        add(Setting.integer(BENCH_BASELINE, "Benchmark", "Baseline chunks in flight",
                "Chunks in flight in the baseline phase (32 = /neoforge generate).", 1, () -> 4096, null, "",
                () -> PolyConfig.get().benchBaselineConcurrency, v -> PolyConfig.get().benchBaselineConcurrency = v));

        add(Setting.integer(LAG_THRESHOLD, "Lag analyzer", "Lag threshold",
                "A tick longer than this is logged as a lag spike. When ticks stay over it for a few seconds, alerts "
                        + "and automatic analysis start. 50 ms = 20 TPS.", 20, () -> 1000, null, "ms",
                () -> PolyConfig.get().lagThresholdMs, v -> PolyConfig.get().lagThresholdMs = v));
        add(Setting.bool(LAG_AUTO, "Lag analyzer", "Automatic analysis",
                "Starts a detailed analysis by itself when the server lags (at most once every 10 minutes).",
                () -> PolyConfig.get().lagAutoCapture, v -> PolyConfig.get().lagAutoCapture = v));
        add(Setting.integer(LAG_SECONDS, "Lag analyzer", "Analysis length",
                "How long a detailed analysis measures every entity and block entity.", 5, () -> 300, null, "s",
                () -> PolyConfig.get().lagCaptureSeconds, v -> PolyConfig.get().lagCaptureSeconds = v));

        add(Setting.bool(NON_OP_VIEW, "Permissions", "Non-operators can view",
                "Every player can open the menu in read-only mode. Editing always needs operator rights.",
                () -> PolyConfig.get().allowNonOpView, v -> PolyConfig.get().allowNonOpView = v));
    }

    private Settings() {}

    private static void add(Setting s) {
        ALL.put(s.key, s);
    }

    public static Setting get(String key) {
        return ALL.get(key);
    }

    public static List<Setting> all() {
        return List.copyOf(ALL.values());
    }

    /** Applies a new value, saves the config and runs side effects. Returns an error message or null. */
    public static String set(String key, String raw) {
        Setting s = ALL.get(key);
        if (s == null) return "Unknown setting: " + key;
        String err = s.set(raw);
        if (err != null) return err;
        applyRuntime();
        return null;
    }

    /** Applies settings that need a live change (pool size and priority). */
    public static void applyRuntime() {
        if (WorldgenPool.isCreated()) {
            WorldgenPool.resize(PolyConfig.poolThreads());
            WorldgenPool.setPriority(PolyConfig.get().worldgenPoolPriority);
        }
    }

    public static ListTag toTag() {
        ListTag list = new ListTag();
        for (Setting s : ALL.values()) list.add(s.toTag());
        return list;
    }
}
