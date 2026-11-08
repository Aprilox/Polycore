package com.polycore.lag;

import com.polycore.Polycore;
import com.polycore.config.PolyConfig;
import com.polycore.pregen.Reports;
import com.polycore.util.Notifier;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * Detailed analysis on demand: times every entity and ticking block entity for a few seconds, then reports the
 * costliest types, mods, sources (with coordinates) and chunks. Server thread only.
 */
public final class LagCapture {
    /** One measured thing: an entity or a ticking block entity. */
    private static final class Source {
        final String type;
        final boolean entity;
        final ResourceKey<Level> dim;
        @Nullable final Entity ref; // entities: read the live position at the end
        @Nullable final BlockPos pos; // block entities
        long nanos;

        Source(String type, boolean entity, ResourceKey<Level> dim, @Nullable Entity ref, @Nullable BlockPos pos) {
            this.type = type;
            this.entity = entity;
            this.dim = dim;
            this.ref = ref;
            this.pos = pos;
        }

        BlockPos where() {
            return ref != null ? ref.blockPosition() : pos;
        }
    }

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static volatile boolean running = false;
    private static Thread owner = null;
    private static final Map<Object, Source> SOURCES = new IdentityHashMap<>();
    private static final long[] sections = new long[LagMonitor.N];
    private static long startNanos, durationNanos, totalNanos, worstNanos;
    private static int ticks, seconds;
    private static boolean auto;
    @Nullable
    private static CommandSourceStack requester;

    @Nullable
    private static CompoundTag last = null;
    private static final List<Source> lastTop = new ArrayList<>();

    /** Where each operator was before teleporting to a source (kept across several teleports). */
    private record Back(ResourceKey<Level> dim, double x, double y, double z, float yRot, float xRot) {}

    private static final Map<UUID, Back> BACK = new HashMap<>();

    private LagCapture() {}

    /** Read by the mixins on every entity and block entity tick: keep it a single volatile read. */
    public static boolean running() {
        return running;
    }

    @Nullable
    public static CompoundTag last() {
        return last;
    }

    /** Starts an analysis. Returns false if one is already running. */
    public static boolean start(int secs, @Nullable CommandSourceStack source, boolean automatic) {
        if (running) {
            if (source != null) Notifier.send(source, Notifier.Level.WARN, "Lag analysis", "An analysis is already running.");
            return false;
        }
        SOURCES.clear();
        Arrays.fill(sections, 0);
        totalNanos = worstNanos = 0;
        ticks = 0;
        seconds = PolyConfig.clamp(secs, 1, 300);
        durationNanos = seconds * 1_000_000_000L;
        startNanos = System.nanoTime();
        auto = automatic;
        requester = source;
        owner = Thread.currentThread();
        running = true;
        if (source != null) {
            Notifier.send(source, Notifier.Level.INFO, "Lag analysis started",
                    "Measuring every entity and block entity for " + seconds + " s.");
        } else {
            Polycore.LOGGER.info("[Polycore] Lag analysis started ({} s){}", seconds, automatic ? ", the server is lagging" : "");
        }
        return true;
    }

    public static void entity(Entity e, long nanos) {
        if (Thread.currentThread() != owner) return;
        Source s = SOURCES.get(e);
        if (s == null) {
            s = new Source(EntityType.getKey(e.getType()).toString(), true, e.getLevel().dimension(), e, null);
            SOURCES.put(e, s);
        }
        s.nanos += nanos;
    }

    public static void blockEntity(Level level, TickingBlockEntity be, long nanos) {
        // Also skips client levels in singleplayer, which tick on the client thread.
        if (Thread.currentThread() != owner) return;
        Source s = SOURCES.get(be);
        if (s == null) {
            s = new Source(typeOf(be), false, level.dimension(), null, be.getPos());
            SOURCES.put(be, s);
        }
        s.nanos += nanos;
    }

    private static String typeOf(TickingBlockEntity be) {
        try {
            String t = be.getType();
            return t == null ? "unknown" : t;
        } catch (Throwable t) { // vanilla builds the name from the registry; an unregistered type throws
            return "unknown";
        }
    }

    static void onTick(long[] tick, long total, MinecraftServer server) {
        if (!running) return;
        ticks++;
        for (int i = 0; i < tick.length; i++) sections[i] += tick[i];
        totalNanos += total;
        worstNanos = Math.max(worstNanos, total);
        if (System.nanoTime() - startNanos >= durationNanos) finish(server);
    }

    // ------------------------------------------------------------------ results

    private static void finish(MinecraftServer server) {
        running = false;
        double elapsed = (System.nanoTime() - startNanos) / 1e9;
        double perTick = Math.max(1, ticks) * 1e6; // nanos -> ms per tick
        List<Source> all = new ArrayList<>(SOURCES.values());
        SOURCES.clear();
        all.sort(Comparator.comparingLong((Source s) -> s.nanos).reversed());

        Map<String, long[]> types = new HashMap<>(); // "E|type" -> nanos, count
        Map<String, long[]> mods = new HashMap<>(); // namespace -> nanos
        Map<String, long[]> chunks = new HashMap<>(); // "dim|cx|cz" -> nanos, entities, block entities
        for (Source s : all) {
            long[] t = types.computeIfAbsent((s.entity ? "E|" : "B|") + s.type, k -> new long[2]);
            t[0] += s.nanos;
            t[1]++;
            mods.computeIfAbsent(namespace(s.type), k -> new long[1])[0] += s.nanos;
            BlockPos p = s.where();
            long[] c = chunks.computeIfAbsent(s.dim.location() + "|" + (p.getX() >> 4) + "|" + (p.getZ() >> 4), k -> new long[3]);
            c[0] += s.nanos;
            c[s.entity ? 1 : 2]++;
        }

        CompoundTag sum = new CompoundTag();
        String date = LocalDateTime.now().format(SHOW_TIME);
        double mspt = totalNanos / perTick;
        double tps = Math.min(20.0f, ticks / Math.max(0.001, elapsed));
        sum.putString("date", date);
        sum.putInt("seconds", seconds);
        sum.putInt("ticks", ticks);
        sum.putDouble("mspt", mspt);
        sum.putDouble("tps", tps);
        sum.putDouble("worst", worstNanos / 1e6);
        sum.putBoolean("auto", auto);

        Integer[] order = new Integer[LagMonitor.N];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Long.compare(sections[b], sections[a]));
        ListTag secList = new ListTag();
        for (int i : order) {
            CompoundTag e = new CompoundTag();
            e.putString("name", LagMonitor.SECTIONS[i].label);
            e.putDouble("ms", sections[i] / perTick);
            secList.add(e);
        }
        sum.put("sections", secList);

        lastTop.clear();
        ListTag top = new ListTag();
        for (Source s : all) {
            if (top.size() >= 10) break;
            lastTop.add(s);
            BlockPos p = s.where();
            CompoundTag e = new CompoundTag();
            e.putString("type", s.type);
            e.putBoolean("entity", s.entity);
            e.putString("dim", s.dim.location().toString());
            e.putInt("x", p.getX());
            e.putInt("y", p.getY());
            e.putInt("z", p.getZ());
            e.putDouble("ms", s.nanos / perTick);
            top.add(e);
        }
        sum.put("top", top);

        ListTag typeList = new ListTag();
        for (Map.Entry<String, long[]> en : sorted(types, 10)) {
            CompoundTag e = new CompoundTag();
            e.putString("type", en.getKey().substring(2));
            e.putBoolean("entity", en.getKey().startsWith("E|"));
            e.putLong("count", en.getValue()[1]);
            e.putDouble("ms", en.getValue()[0] / perTick);
            typeList.add(e);
        }
        sum.put("types", typeList);

        ListTag modList = new ListTag();
        for (Map.Entry<String, long[]> en : sorted(mods, 8)) {
            CompoundTag e = new CompoundTag();
            e.putString("mod", en.getKey());
            e.putDouble("ms", en.getValue()[0] / perTick);
            modList.add(e);
        }
        sum.put("mods", modList);

        ListTag chunkList = new ListTag();
        for (Map.Entry<String, long[]> en : sorted(chunks, 5)) {
            String[] k = en.getKey().split("\\|");
            CompoundTag e = new CompoundTag();
            e.putString("dim", k[0]);
            e.putInt("x", Integer.parseInt(k[1]));
            e.putInt("z", Integer.parseInt(k[2]));
            e.putLong("entities", en.getValue()[1]);
            e.putLong("blocks", en.getValue()[2]);
            e.putDouble("ms", en.getValue()[0] / perTick);
            chunkList.add(e);
        }
        sum.put("chunks", chunkList);

        String result = resultLine(sum);
        sum.putString("result", result);
        Path file = Reports.write("lag-" + LocalDateTime.now().format(FILE_TIME) + ".txt", reportLines(sum, all.size()));
        if (file != null) sum.putString("file", file.getFileName().toString());
        last = sum;

        if (requester != null) Notifier.send(requester, Notifier.Level.SUCCESS, "Lag analysis ready", result);
        if (auto) LagMonitor.alertReady(server, result);
        requester = null;
    }

    private static List<Map.Entry<String, long[]>> sorted(Map<String, long[]> map, int max) {
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(max)
                .toList();
    }

    private static String namespace(String type) {
        int i = type.indexOf(':');
        return i < 0 ? "minecraft" : type.substring(0, i);
    }

    private static String shortDim(String dim) {
        return dim.startsWith("minecraft:") ? dim.substring(10) : dim;
    }

    private static String where(CompoundTag e) {
        return shortDim(e.getString("dim")) + " " + e.getInt("x") + " " + e.getInt("y") + " " + e.getInt("z");
    }

    private static String resultLine(CompoundTag sum) {
        CompoundTag main = sum.getList("sections", 10).getCompound(0);
        String s = String.format(Locale.ROOT, "%.1f ms per tick (%.1f TPS). Main cause: %s (%.1f ms)",
                sum.getDouble("mspt"), sum.getDouble("tps"), main.getString("name"), main.getDouble("ms"));
        ListTag top = sum.getList("top", 10);
        if (!top.isEmpty()) {
            CompoundTag t = top.getCompound(0);
            s += String.format(Locale.ROOT, ". Top source: %s at %s (%.2f ms)", t.getString("type"), where(t), t.getDouble("ms"));
        }
        return s;
    }

    private static List<String> reportLines(CompoundTag sum, int sources) {
        List<String> l = new ArrayList<>();
        l.add("Polycore lag analysis - " + sum.getString("date") + (sum.getBoolean("auto") ? " (automatic, the server was lagging)" : ""));
        l.add(String.format(Locale.ROOT, "%d s, %d ticks, %d entities and block entities measured. Worst tick %.1f ms.",
                sum.getInt("seconds"), sum.getInt("ticks"), sources, sum.getDouble("worst")));
        l.add("RESULT: " + sum.getString("result"));
        l.add("");
        l.add("=== Where the tick time goes (average ms per tick; 50 ms = 20 TPS)");
        for (CompoundTag e : tags(sum, "sections")) l.add(String.format(Locale.ROOT, "  %-30s %8.2f ms", e.getString("name"), e.getDouble("ms")));
        l.add("");
        l.add("=== Costliest sources");
        int i = 1;
        for (CompoundTag e : tags(sum, "top")) {
            l.add(String.format(Locale.ROOT, "  %2d. %7.3f ms  %-13s %s", i++, e.getDouble("ms"), e.getBoolean("entity") ? "entity" : "block entity", e.getString("type")));
            l.add("    at " + where(e));
        }
        l.add("");
        l.add("=== By type");
        for (CompoundTag e : tags(sum, "types")) {
            l.add(String.format(Locale.ROOT, "  %7.3f ms  %5d x %s (%s)", e.getDouble("ms"), e.getLong("count"), e.getString("type"),
                    e.getBoolean("entity") ? "entity" : "block entity"));
        }
        l.add("");
        l.add("=== By mod");
        for (CompoundTag e : tags(sum, "mods")) l.add(String.format(Locale.ROOT, "  %7.3f ms  %s", e.getDouble("ms"), e.getString("mod")));
        l.add("");
        l.add("=== Busiest chunks");
        for (CompoundTag e : tags(sum, "chunks")) {
            l.add(String.format(Locale.ROOT, "  %7.3f ms  %s chunk %d, %d (blocks %d, %d): %d entities, %d block entities", e.getDouble("ms"),
                    shortDim(e.getString("dim")), e.getInt("x"), e.getInt("z"), e.getInt("x") * 16 + 8, e.getInt("z") * 16 + 8,
                    e.getLong("entities"), e.getLong("blocks")));
        }
        l.add("");
        l.add("Times are averages per tick over the analysis. Entities include their passengers.");
        return l;
    }

    private static List<CompoundTag> tags(CompoundTag parent, String key) {
        ListTag list = parent.getList(key, 10);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i));
        return out;
    }

    // ------------------------------------------------------------------ menu, teleport, console

    static CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("running", running);
        t.putInt("seconds", running ? seconds : PolyConfig.get().lagCaptureSeconds);
        if (running) t.putInt("remaining", (int) Math.ceil(Math.max(0, durationNanos - (System.nanoTime() - startNanos)) / 1e9));
        t.putBoolean("auto", PolyConfig.get().lagAutoCapture);
        if (last != null) t.put("last", last);
        return t;
    }

    /** Teleports an operator to a source of the last analysis (index in its top list). */
    public static void teleport(ServerPlayer p, int index) {
        if (index < 0 || index >= lastTop.size()) {
            Notifier.send(p, Notifier.Level.ERROR, "Lag analysis", "This source is not in the last analysis anymore.");
            return;
        }
        Source s = lastTop.get(index);
        boolean alive = s.ref != null && !s.ref.isRemoved();
        ServerLevel level = p.server.getLevel(alive ? s.ref.getLevel().dimension() : s.dim);
        if (level == null) {
            Notifier.send(p, Notifier.Level.ERROR, "Lag analysis", "Dimension " + s.dim.location() + " is not loaded.");
            return;
        }
        BlockPos pos = s.where();
        BACK.putIfAbsent(p.getUUID(), new Back(p.getLevel().dimension(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
        // Block entities: stand on top of the block. Entities: at their current position.
        p.teleportTo(level, pos.getX() + 0.5, s.entity ? pos.getY() : pos.getY() + 1, pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        Notifier.send(p, Notifier.Level.INFO, "Teleported", s.type + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** Takes an operator back to where they were before their first teleport (go = false just forgets it). */
    public static void back(ServerPlayer p, boolean go) {
        Back b = BACK.remove(p.getUUID());
        if (!go || b == null) return;
        ServerLevel level = p.server.getLevel(b.dim());
        if (level == null) {
            Notifier.send(p, Notifier.Level.ERROR, "Lag analysis", "Dimension " + b.dim().location() + " is not loaded.");
            return;
        }
        p.teleportTo(level, b.x(), b.y(), b.z(), b.yRot(), b.xRot());
        Notifier.send(p, Notifier.Level.INFO, "Back", "You are back where you were.");
    }

    static void putBack(CompoundTag t, ServerPlayer viewer) {
        Back b = BACK.get(viewer.getUUID());
        if (b == null) return;
        CompoundTag e = new CompoundTag();
        e.putString("dim", b.dim().location().toString());
        e.putInt("x", (int) Math.floor(b.x()));
        e.putInt("y", (int) Math.floor(b.y()));
        e.putInt("z", (int) Math.floor(b.z()));
        t.put("back", e);
    }

    static List<String> textSummary() {
        List<String> l = new ArrayList<>();
        if (running) l.add("  Analysis running...");
        if (last == null) {
            l.add("  No detailed analysis yet: /polycore lag analyze [seconds]");
            return l;
        }
        l.add("  Last analysis (" + last.getString("date") + "): " + last.getString("result"));
        int i = 1;
        for (CompoundTag e : tags(last, "top")) {
            if (i > 5) break;
            l.add(String.format(Locale.ROOT, "   %d. %.3f ms  %s at %s", i++, e.getDouble("ms"), e.getString("type"), where(e)));
        }
        if (last.contains("file")) l.add("  Full report: polycore-reports/" + last.getString("file"));
        return l;
    }

    public static void reset() {
        running = false;
        SOURCES.clear();
        lastTop.clear();
        last = null;
        requester = null;
        BACK.clear();
    }
}
