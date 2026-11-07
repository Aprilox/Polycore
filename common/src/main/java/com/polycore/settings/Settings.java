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
    public static final String FAST_AQUIFER = "fastAquifer.enabled";
    public static final String PROFILER = "profiler.enabled";
    public static final String PREGEN_CONCURRENCY = "pregen.concurrency";
    public static final String PREGEN_KEEP_TPS = "pregen.keepTps";
    public static final String PREGEN_TARGET_MSPT = "pregen.targetMspt";
    public static final String PREGEN_SAVE_EVERY = "pregen.saveEvery";
    public static final String BENCH_RADIUS = "bench.defaultRadius";
    public static final String PARITY_RECORD = "parity.recordTerrain";
    public static final String BENCH_BASELINE = "bench.baselineConcurrency";
    public static final String NON_OP_VIEW = "permissions.allowNonOpView";
    public static final String LAG_THRESHOLD = "lag.thresholdMs";
    public static final String LAG_AUTO = "lag.autoCapture";
    public static final String LAG_SECONDS = "lag.captureSeconds";
    public static final String GUARD_ENABLED = "guard.enabled";
    public static final String GUARD_THRESHOLD = "guard.thresholdMs";
    public static final String GUARD_ITEM_MERGE = "guard.itemMerge";
    public static final String GUARD_MOB_AI = "guard.mobAi";
    public static final String GUARD_MOB_AI_DISTANCE = "guard.mobAiDistance";
    public static final String GUARD_SPAWN_LIMIT = "guard.spawnLimit";
    public static final String GUARD_SPAWN_PERCENT = "guard.spawnPercent";
    public static final String GUARD_SIM_DISTANCE = "guard.simDistance";
    public static final String GUARD_SIM_DISTANCE_MIN = "guard.simDistanceMin";

    private static final Map<String, Setting> ALL = new LinkedHashMap<>();

    static {
        add(Setting.bool(WORLDGEN_POOL, "Generation", "Dedicated worldgen pool",
                "Noise and biome computation run on Polycore's own threads.",
                () -> PolyConfig.get().worldgenPoolEnabled, v -> PolyConfig.get().worldgenPoolEnabled = v));
        add(Setting.bool(PARALLEL_STAGES, "Generation", "Parallel surface & carvers",
                "Surface and carver stages run in parallel instead of one chunk at a time.",
                () -> PolyConfig.get().parallelStagesEnabled, v -> PolyConfig.get().parallelStagesEnabled = v));
        add(Setting.bool(FAST_AQUIFER, "Generation", "Fast aquifers",
                "Aquifers (underground water and lava) reuse the work shared by neighbouring blocks. Same world.",
                () -> PolyConfig.get().fastAquiferEnabled, v -> PolyConfig.get().fastAquiferEnabled = v));
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

        add(Setting.bool(PARITY_RECORD, "Benchmark", "Record terrain fingerprints",
                "Hashes every new chunk right after its carvers stage, so the parity fingerprint can compare terrain "
                        + "between worlds whatever the generation order. About 0.5 ms per new chunk on the generation "
                        + "threads, never on the server tick; off during benchmarks.",
                () -> PolyConfig.get().parityRecordTerrain, v -> PolyConfig.get().parityRecordTerrain = v));
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

        add(Setting.bool(GUARD_ENABLED, "TPS Guard", "TPS Guard",
                "When the server lags, turns on the measures below one at a time, then off again once it is fine.",
                () -> PolyConfig.get().guardEnabled, v -> PolyConfig.get().guardEnabled = v));
        add(Setting.integer(GUARD_THRESHOLD, "TPS Guard", "Starts above",
                "Average tick time over 3 s that turns the Guard on. Below 50 ms the server still runs at 20 TPS, so the "
                        + "Guard can act before players notice.", 20, () -> 200, null, "ms",
                () -> PolyConfig.get().guardThresholdMs, v -> PolyConfig.get().guardThresholdMs = v));
        add(Setting.bool(GUARD_ITEM_MERGE, "TPS Guard", "Item & XP merging",
                "Items and experience orbs on the ground merge within 2 blocks while the server lags.",
                () -> PolyConfig.get().guardItemMerge, v -> PolyConfig.get().guardItemMerge = v));
        add(Setting.bool(GUARD_MOB_AI, "TPS Guard", "Far mob AI",
                "Mobs far from every player think less often while the server lags.",
                () -> PolyConfig.get().guardMobAi, v -> PolyConfig.get().guardMobAi = v));
        add(Setting.integer(GUARD_MOB_AI_DISTANCE, "TPS Guard", "Far from players beyond",
                "Distance to the nearest player after which a mob counts as far.", 32, () -> 256, null, "blocks",
                () -> PolyConfig.get().guardMobAiDistance, v -> PolyConfig.get().guardMobAiDistance = v));
        add(Setting.bool(GUARD_SPAWN_LIMIT, "TPS Guard", "Mob spawn limit",
                "Natural mob caps are lowered while the server lags.",
                () -> PolyConfig.get().guardSpawnLimit, v -> PolyConfig.get().guardSpawnLimit = v));
        add(Setting.integer(GUARD_SPAWN_PERCENT, "TPS Guard", "Mob caps lowered to",
                "Share of the normal mob caps kept while the limit is on.", 10, () -> 90, null, "%",
                () -> PolyConfig.get().guardSpawnPercent, v -> PolyConfig.get().guardSpawnPercent = v));
        add(Setting.bool(GUARD_SIM_DISTANCE, "TPS Guard", "Simulation distance",
                "Lowers the simulation distance while the server lags. Off by default: farms far from players pause.",
                () -> PolyConfig.get().guardSimDistance, v -> PolyConfig.get().guardSimDistance = v));
        add(Setting.integer(GUARD_SIM_DISTANCE_MIN, "TPS Guard", "Simulation distance lowered to",
                "Simulation distance used while the measure is on. The view distance never changes.", 2, () -> 32, null, "chunks",
                () -> PolyConfig.get().guardSimDistanceMin, v -> PolyConfig.get().guardSimDistanceMin = v));

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

    /** Applies settings that need a live change (pool size and priority, TPS Guard measures). */
    public static void applyRuntime() {
        if (WorldgenPool.isCreated()) {
            WorldgenPool.resize(PolyConfig.poolThreads());
            WorldgenPool.setPriority(PolyConfig.get().worldgenPoolPriority);
        }
        com.polycore.guard.TpsGuard.refresh();
    }

    public static ListTag toTag() {
        ListTag list = new ListTag();
        for (Setting s : ALL.values()) list.add(s.toTag());
        return list;
    }
}
