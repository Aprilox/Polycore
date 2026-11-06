package com.polycore.optim;

import com.polycore.PolyConfig;
import com.polycore.util.Compat;

/**
 * Registre de toutes les optimisations et outils, affiche dans le menu et par /polycore status.
 */
public enum Optimization {
    WORLDGEN_POOL(
            "worldgen_pool",
            "Dedicated worldgen thread pool",
            Category.GENERATION,
            Kind.ACTIVE,
            "Terrain noise and biome computation run on a reserved thread pool instead of Minecraft's shared background "
                    + "pool. That shared pool also runs the generation queue (one task at a time) and lighting: freeing it "
                    + "means the slowest stage no longer waits behind noise computation."),
    STEP_PROFILER(
            "step_profiler",
            "Worldgen stage profiler",
            Category.TOOL,
            Kind.ACTIVE,
            "Measures the time spent in each chunk stage (structures, biomes, noise, surface, carvers, features, "
                    + "light...) to show where time goes. Very low overhead."),
    PARALLEL_PREGEN(
            "parallel_pregen",
            "Parallel pre-generation",
            Category.GENERATION,
            Kind.ACTIVE,
            "/polycore pregen requests many chunks at once (cores x 8 by default, versus 32 for "
                    + "/neoforge generate) to keep every core busy. Players no longer see terrain being generated."),
    PARALLEL_SCHEDULER(
            "parallel_scheduler",
            "Parallel stage scheduler",
            Category.GENERATION,
            Kind.PLANNED,
            "Planned for v2: run stages that only touch their own chunk (surface, carvers, structures) in parallel. "
                    + "In vanilla 1.21.1 they all go through a queue that processes one at a time."),
    SIMD_NOISE(
            "simd_noise",
            "SIMD terrain noise",
            Category.GENERATION,
            Kind.PLANNED,
            "Planned for v2: compute several noise samples per instruction (Java Vector API), "
                    + "verified bit-for-bit against vanilla with /polycore hash.");

    public enum Category { GENERATION, TOOL }

    public enum Kind { ACTIVE, PLANNED }

    public final String id;
    public final String displayName;
    public final Category category;
    public final Kind kind;
    public final String description;

    private volatile boolean enabled = true;
    /** Vrai des que le crochet (mixin) a ete execute au moins une fois. */
    private volatile boolean hookSeen = false;

    Optimization(String id, String displayName, Category category, Kind kind, String description) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.kind = kind;
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHookSeen() {
        return hookSeen;
    }

    public void markHookSeen() {
        if (!hookSeen) hookSeen = true;
    }

    public boolean isToggleable() {
        return kind == Kind.ACTIVE && blockReason() == null;
    }

    /** Raison du blocage (mod incompatible, etc.), ou null. */
    public String blockReason() {
        return switch (this) {
            case WORLDGEN_POOL -> Compat.worldgenPoolBlockReason();
            default -> null;
        };
    }

    /** L'optimisation agit-elle en ce moment ? */
    public boolean isActive() {
        return kind == Kind.ACTIVE && enabled && blockReason() == null;
    }

    public static Optimization byId(String id) {
        for (Optimization o : values()) {
            if (o.id.equalsIgnoreCase(id)) return o;
        }
        return null;
    }

    /** Charge l'etat initial depuis la config. */
    public static void loadFromConfig() {
        WORLDGEN_POOL.enabled = PolyConfig.safeGet(PolyConfig.WORLDGEN_POOL_ENABLED, true);
        STEP_PROFILER.enabled = PolyConfig.safeGet(PolyConfig.PROFILER_ENABLED, true);
        PARALLEL_PREGEN.enabled = true;
    }
}
