package com.polycore.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polycore.Polycore;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
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

    /** A spot under the names the running game uses, with the way back to Mojang names for the messages. */
    private record Live(Spot mojang, String owner, String[] methods, Map<String, String> toMojang) {}

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
        ITEM_MERGE("Item merging",
                new Spot("net/minecraft/world/entity/item/ItemEntity", "mergeWithNeighbours", "tryToMerge", "isMergable", "areMergable", "merge")),
        XP_MERGE("XP orb merging",
                new Spot("net/minecraft/world/entity/ExperienceOrb", "scanForEntities", "canMerge", "merge", "tryMergeToExisting")),
        MOB_AI("Far mob AI",
                new Spot("net/minecraft/world/entity/Mob", "serverAiStep"),
                new Spot("net/minecraft/world/entity/ai/Brain", "tick")),
        SPAWN_LIMIT("Mob spawn limit",
                new Spot("net/minecraft/world/entity/MobCategory", "getMaxInstancesPerChunk"),
                new Spot("net/minecraft/world/level/NaturalSpawner$SpawnState", "canSpawnForCategoryGlobal", "canSpawnForCategoryLocal"),
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
        JsonObject names = runtimeTable();
        for (Feature f : Feature.values()) {
            for (Spot spot : f.spots) {
                String hit = otherModTouching(live(spot, names));
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

    private static final String TABLE = "polycore-spots.json";

    /**
     * Mojang name -> names of the running game, written at build time by the loaders whose production game does not
     * use Mojang's names (Fabric: intermediary); null when the jar has none.
     */
    @Nullable
    private static JsonObject bundledTable() {
        try (InputStream in = Conflicts.class.getClassLoader().getResourceAsStream(TABLE)) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            Polycore.LOGGER.warn("[Polycore] Cannot read {}: conflicts with other mods may not be detected", TABLE, e);
            return null;
        }
    }

    /** The bundled table when the game runs under its names (a production Fabric game), null under Mojang names. */
    @Nullable
    private static JsonObject runtimeTable() {
        // A resource lookup, not Class.forName: nothing is loaded. A development game has Mojang-named classes.
        if (Conflicts.class.getClassLoader().getResource("net/minecraft/server/level/ChunkMap.class") != null) return null;
        return bundledTable();
    }

    private static Live live(Spot s, @Nullable JsonObject table) {
        JsonObject c = table == null ? null : table.getAsJsonObject(s.owner());
        if (c == null) return new Live(s, s.owner(), s.methods(), Map.of());
        JsonObject ms = c.getAsJsonObject("methods");
        List<String> names = new ArrayList<>();
        Map<String, String> back = new HashMap<>();
        for (String m : s.methods()) {
            if (!ms.has(m)) { // "*"
                names.add(m);
                continue;
            }
            for (JsonElement e : ms.getAsJsonArray(m)) {
                names.add(e.getAsString());
                back.put(e.getAsString(), m);
            }
        }
        return new Live(s, c.get("name").getAsString(), names.toArray(String[]::new), back);
    }

    /** Self-test: "owner.method" in the names of the bundled table (whatever the game runs with), or null without one. */
    @Nullable
    public static String translated(String owner, String method) {
        JsonObject table = bundledTable();
        if (table == null) return null;
        Live l = live(new Spot(owner, method), table);
        return l.owner() + "." + String.join("|", l.methods());
    }

    /** Maps a target of another mod's mixin to the running game's names through that mod's refmap (unchanged without). */
    private static UnaryOperator<String> refmap(IMixinInfo mixin) {
        try {
            // MixinConfig (not public) has it; the IMixinConfig interface does not.
            java.lang.reflect.Method get = mixin.getConfig().getClass().getMethod("getReferenceMapper");
            get.setAccessible(true);
            if (get.invoke(mixin.getConfig()) instanceof IReferenceMapper r && !r.isDefault()) {
                return t -> {
                    String out = r.remap(mixin.getClassRef(), t);
                    return out != null ? out : t;
                };
            }
        } catch (Throwable ignored) {
            // no readable refmap: the targets are compared as written
        }
        return UnaryOperator.identity();
    }

    /** "modid also changes Class.method" for the first other mod changing one of the spot's methods, or null. */
    @Nullable
    private static String otherModTouching(Live spot) {
        try {
            // Loading the class (without running it) applies every mod's mixins and fills Mixin's metadata.
            Class.forName(spot.owner().replace('/', '.'), false, Conflicts.class.getClassLoader());
            ClassInfo info = ClassInfo.forName(spot.owner());
            if (info == null) return null;
            for (IMixinInfo mixin : targeting(info)) {
                String config = mixin.getConfig() != null ? mixin.getConfig().getName() : "unknown";
                if (OWN_CONFIG.equals(config)) continue;
                String method = touchedMethod(mixin.getClassNode(0), spot.owner(), spot.methods(), refmap(mixin));
                if (method != null) {
                    String mojang = spot.mojang().owner();
                    String mixinClass = mixin.getClassName().substring(mixin.getClassName().lastIndexOf('.') + 1);
                    return modName(config) + " also changes " + mojang.substring(mojang.lastIndexOf('/') + 1)
                            .replace('$', '.') + "." + spot.toMojang().getOrDefault(method, method) + " (mixin " + mixinClass + ")";
                }
            }
        } catch (Throwable t) {
            Polycore.LOGGER.debug("[Polycore] Could not check {} for other mods' changes", spot.mojang().owner(), t);
        }
        return null;
    }

    /**
     * First of our methods targeted by an injector (any annotation with a "method" value) or an @Overwrite. An
     * injector on every method ("*") only counts where its injection point really is: at the start or end of every
     * method, or inside one of ours (checked against Minecraft's own bytecode).
     */
    @Nullable
    static String touchedMethod(@Nullable ClassNode mixin, String owner, String[] ours, UnaryOperator<String> remap) {
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
                        String name = targetName(remap.apply(target));
                        String hit = name.equals("*") ? wildcardHit(a, owner, ours, remap) : match(name, ours);
                        if (hit != null) return hit;
                    }
                }
            }
        }
        return null;
    }

    /** For a "*" injector: the first of our methods that holds its injection point, or null. */
    @Nullable
    private static String wildcardHit(AnnotationNode injector, String owner, String[] ours, UnaryOperator<String> remap) {
        List<AnnotationNode> ats = new ArrayList<>();
        for (Object v : values(injector, "at")) {
            if (v instanceof AnnotationNode at) ats.add(at);
            else if (v instanceof List<?> l) for (Object o : l) if (o instanceof AnnotationNode at) ats.add(at);
        }
        if (ats.isEmpty()) return ours[0]; // no injection point we can read: assume it touches everything
        ClassNode vanilla = null;
        for (AnnotationNode at : ats) {
            String value = String.valueOf(first(at, "value"));
            Object targetValue = first(at, "target");
            if (!(targetValue instanceof String target) || target.isEmpty()) {
                // HEAD, RETURN, TAIL...: every method of the class, ours included. Unknown kinds: be careful too.
                if (!value.equals("CONSTANT") && !value.equals("LOAD") && !value.equals("STORE")) return ours[0];
                continue;
            }
            try {
                if (vanilla == null) vanilla = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode(owner);
            } catch (Throwable t) {
                return ours[0];
            }
            String hit = methodCalling(vanilla, ours, remap.apply(target));
            if (hit != null) return hit;
        }
        return null;
    }

    /** First of our methods whose vanilla code calls or reads the target ("Lowner;name(desc)" or "Lowner;name:type"). */
    @Nullable
    private static String methodCalling(ClassNode vanilla, String[] ours, String target) {
        String t = target.strip();
        int semi = t.indexOf(';');
        if (!t.startsWith("L") || semi < 0) return ours[0];
        String tOwner = t.substring(1, semi);
        String rest = t.substring(semi + 1);
        int end = rest.indexOf('(');
        if (end < 0) end = rest.indexOf(':');
        String tName = end < 0 ? rest : rest.substring(0, end);
        for (org.objectweb.asm.tree.MethodNode m : vanilla.methods) {
            String hit = match(m.name, ours);
            if (hit == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi && mi.owner.equals(tOwner) && mi.name.equals(tName)) return m.name;
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fi && fi.owner.equals(tOwner) && fi.name.equals(tName)) return m.name;
            }
        }
        return null;
    }

    private static List<Object> values(AnnotationNode a, String key) {
        List<Object> out = new ArrayList<>();
        if (a.values == null) return out;
        for (int i = 0; i + 1 < a.values.size(); i += 2) if (key.equals(a.values.get(i))) out.add(a.values.get(i + 1));
        return out;
    }

    @Nullable
    private static Object first(AnnotationNode a, String key) {
        List<Object> v = values(a, key);
        return v.isEmpty() ? null : v.get(0);
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
