package com.polycore.optim;

import com.polycore.settings.Setting;
import com.polycore.settings.Settings;
import com.polycore.util.Compat;

/**
 * Every optimization and tool shown in the menu, with its state.
 */
public enum Optimization {
    WORLDGEN_POOL(
            "worldgen_pool", "Dedicated worldgen pool", Category.GENERATION, Kind.ACTIVE, Settings.WORLDGEN_POOL,
            "Terrain noise and biome computation run on a reserved thread pool instead of Minecraft's shared "
                    + "background pool. That shared pool also runs the generation queue and lighting, so they no "
                    + "longer wait behind noise computation."),
    PARALLEL_STAGES(
            "parallel_stages", "Parallel surface & carvers", Category.GENERATION, Kind.ACTIVE, Settings.PARALLEL_STAGES,
            "In vanilla, every stage except noise and biomes runs on a single queue, one chunk at a time. Surface and "
                    + "carvers only touch their own chunk, so Polycore runs them in parallel on its pool."),
    PARALLEL_PREGEN(
            "parallel_pregen", "Parallel pre-generation", Category.GENERATION, Kind.TOOL, null,
            "Pre-generates an area with every core busy (cores x 8 chunks in flight versus 32 for /neoforge generate). "
                    + "The 'Keep TPS stable' mode slows down automatically when the server starts to lag."),
    TPS_GUARD(
            "tps_guard", "TPS Guard", Category.TICK, Kind.ACTIVE, Settings.GUARD_ENABLED,
            "Only while the server lags: merges items and XP orbs, slows the AI of mobs far from every player, lowers "
                    + "mob caps and, if allowed, the simulation distance. One measure at a time, from the least visible, "
                    + "and back to normal by itself. Each measure can be switched off on the TPS Guard page."),
    STEP_PROFILER(
            "step_profiler", "Stage profiler", Category.TOOL, Kind.ACTIVE, Settings.PROFILER,
            "Measures each generation stage (structures, biomes, noise, surface, carvers, features, light) to show "
                    + "where time goes. Very low overhead."),
    SIMD_NOISE(
            "simd_noise", "SIMD terrain noise", Category.GENERATION, Kind.PLANNED, null,
            "Planned: compute several noise samples per CPU instruction (Java Vector API), verified bit-for-bit "
                    + "against vanilla with the parity hash.");

    public enum Category { GENERATION, TICK, TOOL }

    /** ACTIVE = can be switched on/off, TOOL = always available, PLANNED = not implemented yet. */
    public enum Kind { ACTIVE, TOOL, PLANNED }

    public final String id;
    public final String displayName;
    public final Category category;
    public final Kind kind;
    public final String settingKey;
    public final String description;

    /** True once the mixin hook ran at least once (proves the injection is in place). */
    private volatile boolean hookSeen = false;

    Optimization(String id, String displayName, Category category, Kind kind, String settingKey, String description) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.kind = kind;
        this.settingKey = settingKey;
        this.description = description;
    }

    public boolean isEnabled() {
        if (kind == Kind.TOOL) return true;
        if (kind == Kind.PLANNED) return false;
        Setting s = Settings.get(settingKey);
        return s == null || s.getBool();
    }

    /** Persists the new state to the config file. Returns an error or null. */
    public String setEnabled(boolean enabled) {
        if (!isToggleable()) return displayName + " cannot be toggled.";
        return Settings.set(settingKey, Boolean.toString(enabled));
    }

    public boolean isHookSeen() {
        return hookSeen;
    }

    public void markHookSeen() {
        if (!hookSeen) hookSeen = true;
    }

    public boolean isToggleable() {
        return kind == Kind.ACTIVE && settingKey != null && blockReason() == null;
    }

    /** Why it cannot run (conflicting mod), or null. */
    public String blockReason() {
        return switch (this) {
            case WORLDGEN_POOL, PARALLEL_STAGES -> Compat.worldgenPoolBlockReason();
            default -> null;
        };
    }

    public boolean isActive() {
        return kind != Kind.PLANNED && isEnabled() && blockReason() == null;
    }

    /** Short status for menus: ACTIVE, OFF, BLOCKED, PLANNED, READY. */
    public String status() {
        if (kind == Kind.PLANNED) return "PLANNED";
        if (blockReason() != null) return "BLOCKED";
        if (kind == Kind.TOOL) return "READY";
        return isEnabled() ? "ACTIVE" : "OFF";
    }

    public static Optimization byId(String id) {
        for (Optimization o : values()) {
            if (o.id.equalsIgnoreCase(id)) return o;
        }
        return null;
    }
}
