package com.polycore.guard;

import com.polycore.Polycore;
import com.polycore.config.PolyConfig;
import com.polycore.settings.Setting;
import com.polycore.settings.Settings;
import com.polycore.util.Conflicts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * TPS Guard: when ticks stay above the threshold, turns on the enabled measures one at a time (least visible first,
 * one more every 10 s while still needed) and turns them off again one by one after 30 s of calm. Nothing changes
 * while the server runs normally. Server thread only; the mixins read the volatile flags.
 */
public final class TpsGuard {
    public enum Measure {
        ITEM_MERGE("item_merge", "Item & XP merging", Conflicts.Feature.ITEM_MERGE, Settings.GUARD_ITEM_MERGE, null,
                "Items and experience orbs on the ground merge within 2 blocks instead of half a block, so there are "
                        + "fewer entities to tick."),
        MOB_AI("mob_ai", "Far mob AI", Conflicts.Feature.MOB_AI, Settings.GUARD_MOB_AI, Settings.GUARD_MOB_AI_DISTANCE,
                "Mobs far from every player choose what to do 2 to 4 times less often; they keep moving normally. Never "
                        + "slowed: villagers, tamed, named or leashed mobs, bosses and mobs that are fighting. Create "
                        + "contraptions are not mobs and are never touched."),
        SPAWN_LIMIT("spawn_limit", "Mob spawn limit", Conflicts.Feature.SPAWN_LIMIT, Settings.GUARD_SPAWN_LIMIT, Settings.GUARD_SPAWN_PERCENT,
                "Natural mob caps are lowered, so fewer new mobs spawn. Mob spawners are not affected."),
        SIM_DISTANCE("sim_distance", "Simulation distance", Conflicts.Feature.SIM_DISTANCE, Settings.GUARD_SIM_DISTANCE, Settings.GUARD_SIM_DISTANCE_MIN,
                "Lowers the simulation distance: chunks far from players stop ticking, so farms there pause. Off by "
                        + "default. The view distance never changes.");

        public final String id;
        public final String label;
        public final String settingKey;
        @Nullable
        public final String paramKey;
        public final String description;
        private final Conflicts.Feature feature;
        volatile boolean active = false;

        Measure(String id, String label, Conflicts.Feature feature, String settingKey, @Nullable String paramKey, String description) {
            this.id = id;
            this.label = label;
            this.feature = feature;
            this.settingKey = settingKey;
            this.paramKey = paramKey;
            this.description = description;
        }

        public boolean isEnabled() {
            Setting s = Settings.get(settingKey);
            return s != null && s.getBool();
        }

        public boolean isActive() {
            return active;
        }

        /** Why it cannot run: another mod changes the same code, or manages the simulation distance. */
        @Nullable
        public String blockReason() {
            String r = Conflicts.reason(feature);
            return r == null && this == SIM_DISTANCE ? externalSim : r;
        }
    }

    private record Episode(long start, long end, int peak) {}

    static final long ESCALATE_MS = 10_000;
    static final long CALM_MS = 30_000;

    private static volatile int level = 0;
    private static long engagedAt = 0, levelAt = 0, calmSince = 0;
    private static int peak = 0;
    private static double recentMs = 0;
    @Nullable
    private static MinecraftServer lastServer = null;
    private static int savedSimDistance = -1;
    /** Last simulation distance seen; a change Polycore did not make means something else manages it. */
    private static int lastSeenSim = -1;
    @Nullable
    private static volatile String externalSim = null;
    private static int slowedCounter = 0, slowedShown = 0, windowTicks = 0;
    private static final Deque<Episode> history = new ArrayDeque<>();

    private TpsGuard() {}

    // ------------------------------------------------------------------ read by the mixins

    public static boolean itemMergeActive() {
        return Measure.ITEM_MERGE.active;
    }

    /** XP orbs follow the item merging measure, unless another mod already manages how they merge. */
    public static boolean xpMergeActive() {
        return Measure.ITEM_MERGE.active && !Conflicts.blocked(Conflicts.Feature.XP_MERGE);
    }

    public static boolean mobAiActive() {
        return Measure.MOB_AI.active;
    }

    public static int scaleMobCap(int max) {
        if (!Measure.SPAWN_LIMIT.active || max <= 0) return max;
        return Math.max(1, max * PolyConfig.get().guardSpawnPercent / 100);
    }

    /** Full-AI interval for a mob: 1 = every evaluation (normal), 4 or 8 = slowed. Re-checked every 20 ticks. */
    public static int aiInterval(Mob m) {
        if (m instanceof AbstractVillager || m.hasCustomName() || m.isLeashed() || m.isPassenger() || m.isVehicle()
                || (m instanceof TamableAnimal t && t.isTame()) || (m instanceof AbstractHorse h && h.isTamed())
                || m instanceof EnderDragon || m instanceof WitherBoss || m.getTarget() != null || m.hurtTime > 0) {
            return 1;
        }
        double nearest = Double.MAX_VALUE;
        for (Player p : m.level().players()) {
            if (!p.isSpectator()) nearest = Math.min(nearest, p.distanceToSqr(m));
        }
        double r = PolyConfig.get().guardMobAiDistance;
        if (nearest < r * r) return 1;
        slowedCounter++;
        return nearest < 4 * r * r ? 4 : 8;
    }

    // ------------------------------------------------------------------ state machine

    /** Every server tick: slowed mobs are counted over 20-tick windows (each mob re-checks once per 20 ticks). */
    public static void tick() {
        if (++windowTicks >= 20) {
            windowTicks = 0;
            slowedShown = slowedCounter;
            slowedCounter = 0;
        }
    }

    /** Once per second with the average tick time of the last 3 s. */
    public static void update(MinecraftServer server, double recentMspt, long now) {
        lastServer = server;
        watchSimDistance(server);
        recentMs = recentMspt;
        int max = enabledMeasures().size();
        double threshold = PolyConfig.get().guardThresholdMs;
        if (max == 0) {
            level = 0;
        } else if (recentMspt > threshold) {
            calmSince = 0;
            if (level == 0) {
                level = 1;
                engagedAt = now;
                levelAt = now;
            } else if (level < max && now - levelAt >= ESCALATE_MS) {
                level++;
                levelAt = now;
            }
        } else if (level > 0 && recentMspt < threshold * 0.75) {
            if (calmSince == 0) {
                calmSince = now;
            } else if (now - calmSince >= CALM_MS) {
                level--;
                levelAt = now;
                calmSince = now;
            }
        } else {
            calmSince = 0;
        }
        recompute(server, now);
    }

    /** A setting changed: apply it right away (a measure switched off stops immediately). */
    public static void refresh() {
        recompute(lastServer, System.currentTimeMillis());
    }

    private static List<Measure> enabledMeasures() {
        List<Measure> list = new ArrayList<>();
        if (!PolyConfig.get().guardEnabled) return list;
        for (Measure m : Measure.values()) if (m.isEnabled() && m.blockReason() == null) list.add(m);
        return list;
    }

    private static void recompute(@Nullable MinecraftServer server, long now) {
        List<Measure> on = enabledMeasures();
        if (level > on.size()) level = on.size();
        String before = activeNames();
        for (Measure m : Measure.values()) {
            int i = on.indexOf(m);
            m.active = i >= 0 && i < level;
        }
        if (server != null) applySimDistance(server);
        peak = Math.max(peak, level);
        if (level == 0 && engagedAt > 0) {
            history.addFirst(new Episode(engagedAt, now, peak));
            while (history.size() > 10) history.removeLast();
            Polycore.LOGGER.info("[Polycore] TPS Guard: back to normal after {} s", (now - engagedAt) / 1000);
            engagedAt = 0;
            peak = 0;
        }
        String after = activeNames();
        if (!after.equals(before) && level > 0) {
            Polycore.LOGGER.info(String.format(Locale.ROOT, "[Polycore] TPS Guard level %d (%.1f ms per tick): %s", level, recentMs, after));
        }
    }

    private static String activeNames() {
        List<String> names = new ArrayList<>();
        for (Measure m : Measure.values()) if (m.active) names.add(m.label);
        return String.join(", ", names);
    }

    private static void applySimDistance(MinecraftServer server) {
        PlayerList players = server.getPlayerList();
        if (Measure.SIM_DISTANCE.active) {
            int target = PolyConfig.get().guardSimDistanceMin;
            if (savedSimDistance < 0 && players.getSimulationDistance() > target) {
                savedSimDistance = players.getSimulationDistance();
                players.setSimulationDistance(target);
                lastSeenSim = target;
            }
        } else if (savedSimDistance > 0) {
            players.setSimulationDistance(savedSimDistance);
            lastSeenSim = savedSimDistance;
            savedSimDistance = -1;
        }
    }

    /** Another mod, a command or the singleplayer options changed the simulation distance: leave it to them. */
    private static void watchSimDistance(MinecraftServer server) {
        int now = server.getPlayerList().getSimulationDistance();
        if (lastSeenSim >= 0 && now != lastSeenSim && externalSim == null) {
            externalSim = "something else changed the simulation distance (" + lastSeenSim + " -> " + now
                    + "), so Polycore leaves it alone until the next start";
            savedSimDistance = -1; // never put back an old value over theirs
            Polycore.LOGGER.warn("[Polycore] TPS Guard, simulation distance measure off: {}", externalSim);
        }
        lastSeenSim = now;
    }

    /** Server stopping (or tests): everything back to normal. */
    public static void reset(@Nullable MinecraftServer server) {
        level = 0;
        calmSince = 0;
        recompute(server, System.currentTimeMillis());
        history.clear();
        slowedCounter = slowedShown = windowTicks = 0;
        lastSeenSim = -1;
        externalSim = null;
        lastServer = null;
    }

    public static int level() {
        return level;
    }

    // ------------------------------------------------------------------ menu and console

    public static CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("enabled", PolyConfig.get().guardEnabled);
        t.putInt("level", level);
        t.putInt("max", enabledMeasures().size());
        t.putLong("since", engagedAt);
        t.putDouble("recent", recentMs);
        t.putInt("threshold", PolyConfig.get().guardThresholdMs);
        ListTag measures = new ListTag();
        for (Measure m : Measure.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", m.id);
            e.putString("name", m.label);
            e.putString("desc", m.description);
            e.putString("key", m.settingKey);
            if (m.paramKey != null) e.putString("param", m.paramKey);
            e.putBoolean("enabled", m.isEnabled());
            e.putBoolean("active", m.active);
            String block = m.blockReason();
            if (block != null) e.putString("block", block);
            e.putString("detail", detail(m));
            measures.add(e);
        }
        t.put("measures", measures);
        ListTag hist = new ListTag();
        for (Episode ep : history) {
            if (hist.size() >= 5) break;
            CompoundTag e = new CompoundTag();
            e.putLong("start", ep.start());
            e.putLong("end", ep.end());
            e.putInt("peak", ep.peak());
            hist.add(e);
        }
        t.put("history", hist);
        return t;
    }

    private static String detail(Measure m) {
        PolyConfig.Data c = PolyConfig.get();
        return switch (m) {
            case ITEM_MERGE -> Conflicts.blocked(Conflicts.Feature.XP_MERGE)
                    ? (m.active ? "Items within 2 blocks; XP left to another mod" : "Items only; XP left to another mod")
                    : m.active ? "Merging within 2 blocks" : "Normal: half a block";
            case MOB_AI -> m.active ? "About " + slowedShown + " mobs slowed" : "Beyond " + c.guardMobAiDistance + " blocks";
            case SPAWN_LIMIT -> m.active ? "Mob caps at " + c.guardSpawnPercent + "%" : "Would lower caps to " + c.guardSpawnPercent + "%";
            case SIM_DISTANCE -> m.active && savedSimDistance > 0 ? "Simulation " + savedSimDistance + " -> " + c.guardSimDistanceMin + " chunks"
                    : "Would lower it to " + c.guardSimDistanceMin + " chunks";
        };
    }

    public static List<String> textSummary() {
        List<String> l = new ArrayList<>();
        PolyConfig.Data c = PolyConfig.get();
        String state = !c.guardEnabled ? "off" : level == 0 ? "standing by" : "level " + level + " of " + enabledMeasures().size();
        l.add(String.format(Locale.ROOT, "=== TPS Guard: %s (starts above %d ms, last 3 s: %.1f ms) ===", state, c.guardThresholdMs, recentMs));
        for (Measure m : Measure.values()) {
            String block = m.blockReason();
            String st = block != null ? "BLOCKED" : !c.guardEnabled || !m.isEnabled() ? "OFF" : m.active ? "ACTIVE" : "STANDBY";
            l.add("  " + m.label + ": " + st + " - " + (block != null ? block : detail(m)));
        }
        return l;
    }
}
