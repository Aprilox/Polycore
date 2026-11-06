package com.polycore.util;

import com.polycore.Polycore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * Finds other mods that change, through Mixin, the same Minecraft methods as a Polycore optimization. No mod is known
 * by name: Polycore reads the mixins every other mod applied to those classes and looks at the methods they target.
 * A touched feature stays off with the reason shown in the menu and the console; the check runs at every start, so
 * the feature comes back by itself once the other mod is gone.
 */
public final class Conflicts {
    /** Methods of one class (internal name) that another mod must not change. "*" = any method of the class. */
    private record Spot(String owner, String... methods) {}

    public enum Feature {
        WORLDGEN_POOL("Dedicated worldgen pool",
                new Spot("net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator", "fillFromNoise", "createBiomes")),
        PARALLEL_STAGES("Parallel surface & carvers",
                new Spot("net/minecraft/world/level/chunk/status/ChunkStep", "apply"),
                new Spot("net/minecraft/server/level/GenerationChunkHolder", "applyStep", "scheduleChunkGenerationTask"),
                new Spot("net/minecraft/server/level/ChunkMap", "applyStep"),
                new Spot("net/minecraft/server/level/ChunkGenerationTask", "*")),
        FAST_AQUIFER("Fast aquifers",
                new Spot("net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer", "computeSubstance")),
        ITEM_MERGE("Item & XP merging",
                new Spot("net/minecraft/world/entity/item/ItemEntity", "mergeWithNeighbours", "tryToMerge", "isMergable", "areMergable", "merge"),
                new Spot("net/minecraft/world/entity/ExperienceOrb", "scanForEntities", "canMerge", "merge", "tryMergeToExisting")),
        MOB_AI("Far mob AI",
                new Spot("net/minecraft/world/entity/Mob", "serverAiStep"),
                new Spot("net/minecraft/world/entity/ai/Brain", "tick")),
        SPAWN_LIMIT("Mob spawn limit",
                new Spot("net/minecraft/world/entity/MobCategory", "getMaxInstancesPerChunk"),
                new Spot("net/minecraft/world/level/NaturalSpawner$SpawnState", "canSpawnForCategory"),
                new Spot("net/minecraft/world/level/LocalMobCapCalculator$MobCounts", "canSpawn")),
        SIM_DISTANCE("Simulation distance",
                new Spot("net/minecraft/server/players/PlayerList", "setSimulationDistance"),
                new Spot("net/minecraft/server/level/ServerChunkCache", "setSimulationDistance"),
                new Spot("net/minecraft/server/level/DistanceManager", "updateSimulationDistance"));

        public final String label;
        private final Spot[] spots;

        Feature(String label, Spot... spots) {
            this.label = label;
            this.spots = spots;
        }
    }

    private static final String OWN_CONFIG = "polycore.mixins.json";
    /**
     * Mixin keeps, for each target class, the mixins of every mod that target it, but only in a private field
     * (ClassInfo.getAppliedMixins() is filled on the mixin's own ClassInfo, not the target's). The self-test game test
     * fails if a Mixin update renames it.
     */
    @Nullable
    private static final java.lang.reflect.Field TARGETING_MIXINS = findTargetingField();
    private static volatile Map<Feature, String> reasons;

    @Nullable
    private static java.lang.reflect.Field findTargetingField() {
        try {
            java.lang.reflect.Field f = ClassInfo.class.getDeclaredField("mixins");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            Polycore.LOGGER.warn("[Polycore] Cannot read which mods change Minecraft's code (Mixin internals differ): "
                    + "conflicts with other mods will not be detected", t);
            return null;
        }
    }

    /** Mixins of every mod that target this class. */
    private static List<IMixinInfo> targeting(ClassInfo info) throws IllegalAccessException {
        List<IMixinInfo> out = new ArrayList<>();
        if (TARGETING_MIXINS == null) return out;
        if (TARGETING_MIXINS.get(info) instanceof java.util.Collection<?> set) {
            for (Object o : set) if (o instanceof IMixinInfo m) out.add(m);
        }
        return out;
    }

    private Conflicts() {}

    /** Why a feature is off because another mod changes the same code, or null. */
    @Nullable
    public static String reason(Feature f) {
        return reasons().get(f);
    }

    /** Read on hot paths (every air block for the aquifers): one volatile read and an array lookup once scanned. */
    public static boolean blocked(Feature f) {
        return reasons().containsKey(f);
    }

    public static Map<Feature, String> reasons() {
        Map<Feature, String> r = reasons;
        return r != null ? r : scan();
    }

    private static synchronized Map<Feature, String> scan() {
        if (reasons != null) return reasons;
        Map<Feature, String> found = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            for (Spot spot : f.spots) {
                String hit = otherModTouching(spot);
                if (hit != null) {
                    found.put(f, hit);
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            Polycore.LOGGER.info("[Polycore] No other mod changes the code Polycore optimizes: every optimization can run.");
        } else {
            found.forEach((f, why) -> Polycore.LOGGER.warn("[Polycore] {} is off: {}. It turns back on by itself once that "
                    + "mod is removed.", f.label, why));
        }
        reasons = Collections.unmodifiableMap(found);
        return reasons;
    }

    /** "modid also changes Class.method" for the first other mod changing one of the spot's methods, or null. */
    @Nullable
    private static String otherModTouching(Spot spot) {
        try {
            // Loading the class (without running it) applies every mod's mixins and fills Mixin's metadata.
            Class.forName(spot.owner().replace('/', '.'), false, Conflicts.class.getClassLoader());
            ClassInfo info = ClassInfo.forName(spot.owner());
            if (info == null) return null;
            for (IMixinInfo mixin : targeting(info)) {
                String config = mixin.getConfig() != null ? mixin.getConfig().getName() : "unknown";
                if (OWN_CONFIG.equals(config)) continue;
                String method = touchedMethod(mixin.getClassNode(0), spot.methods());
                if (method != null) {
                    return modName(config) + " also changes " + spot.owner().substring(spot.owner().lastIndexOf('/') + 1)
                            .replace('$', '.') + "." + method;
                }
            }
        } catch (Throwable t) {
            Polycore.LOGGER.debug("[Polycore] Could not check {} for other mods' changes", spot.owner(), t);
        }
        return null;
    }

    /** First of our methods targeted by an injector (any annotation with a "method" value) or an @Overwrite. */
    @Nullable
    static String touchedMethod(@Nullable ClassNode mixin, String[] ours) {
        if (mixin == null || mixin.methods == null) return null;
        for (MethodNode m : mixin.methods) {
            for (List<AnnotationNode> list : Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
                if (list == null) continue;
                for (AnnotationNode a : list) {
                    if ("Lorg/spongepowered/asm/mixin/Overwrite;".equals(a.desc)) {
                        String hit = match(m.name, ours);
                        if (hit != null) return hit;
                    }
                    for (String target : methodValues(a)) {
                        String hit = match(targetName(target), ours);
                        if (hit != null) return hit;
                    }
                }
            }
        }
        return null;
    }

    private static List<String> methodValues(AnnotationNode a) {
        List<String> out = new ArrayList<>();
        if (a.values == null) return out;
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            if (!"method".equals(a.values.get(i))) continue;
            Object v = a.values.get(i + 1);
            if (v instanceof String s) out.add(s);
            else if (v instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
        }
        return out;
    }

    /** "Lowner;name(desc)ret", "name(desc)ret" or "name" -> "name". */
    public static String targetName(String target) {
        String s = target.strip();
        int semi = s.indexOf(';'), paren = s.indexOf('(');
        if (s.startsWith("L") && semi > 0 && (paren < 0 || semi < paren)) s = s.substring(semi + 1);
        paren = s.indexOf('(');
        return paren >= 0 ? s.substring(0, paren) : s;
    }

    @Nullable
    private static String match(String name, String[] ours) {
        if (name.equals("<init>") || name.equals("<clinit>")) return null;
        for (String o : ours) {
            if (o.equals("*") || name.equals("*") || o.equals(name)) return name.equals("*") ? o : name;
        }
        return null;
    }

    /** "clumps.mixins.json" or "mixins.clumps.json" -> "clumps". */
    public static String modName(String config) {
        String s = config;
        if (s.endsWith(".json")) s = s.substring(0, s.length() - 5);
        if (s.startsWith("mixins.")) s = s.substring(7);
        if (s.endsWith(".mixins")) s = s.substring(0, s.length() - 7);
        return s;
    }

    /** Diagnostic: the mixins Mixin reports as applied to a class, with their config. */
    public static String applied(String owner) {
        try {
            Class.forName(owner.replace('/', '.'), false, Conflicts.class.getClassLoader());
            ClassInfo info = ClassInfo.forName(owner);
            if (info == null) return "no ClassInfo";
            StringBuilder sb = new StringBuilder();
            for (IMixinInfo m : targeting(info)) {
                sb.append(m.getClassName()).append(" (").append(m.getConfig() == null ? "?" : m.getConfig().getName()).append(") ");
            }
            return sb.length() == 0 ? "none" : sb.toString();
        } catch (Throwable t) {
            return "error: " + t;
        }
    }

    public static ListTag toTag() {
        ListTag list = new ListTag();
        reasons().forEach((f, why) -> {
            CompoundTag t = new CompoundTag();
            t.putString("feature", f.label);
            t.putString("reason", why);
            list.add(t);
        });
        return list;
    }
}
