package com.polycore.gen;

import com.polycore.optim.Optimization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Measures the time spent in each chunk generation stage.
 * Thread-safe: called from the generation threads.
 */
public final class StepProfiler {
    /** Display order of the vanilla stages (others are appended). */
    private static final List<String> ORDER = List.of(
            "structure_starts", "structure_references", "biomes", "noise", "surface", "carvers",
            "features", "initialize_light", "light", "spawn", "full",
            "calc:biomes", "calc:noise");

    public static final class Stat {
        public final LongAdder count = new LongAdder();
        public final LongAdder totalNanos = new LongAdder();
        public final AtomicLong maxNanos = new AtomicLong();

        void add(long nanos) {
            count.increment();
            totalNanos.add(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }
    }

    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();

    // Finished chunks ("full" status) per second, over a sliding 64 s window.
    private static final int WINDOW = 64;
    private static final AtomicLongArray BUCKET_SECOND = new AtomicLongArray(WINDOW);
    private static final AtomicLongArray BUCKET_COUNT = new AtomicLongArray(WINDOW);
    private static final LongAdder TOTAL_FULL = new LongAdder();
    private static final LongAdder TOTAL_LOADED = new LongAdder();
    private static volatile long resetAtMillis = System.currentTimeMillis();

    private StepProfiler() {}

    public static boolean enabled() {
        return Optimization.STEP_PROFILER.isActive();
    }

    /** Records a stage duration (from the call to the end of the work, waiting included). */
    public static void record(String status, long nanos) {
        STATS.computeIfAbsent(status, k -> new Stat()).add(nanos);
        if ("full".equals(status)) {
            onChunkFull();
        }
    }

    /** Enregistre un temps de calcul pur (sans attente), ex. "calc:noise". */
    public static void recordCalc(String what, long nanos) {
        STATS.computeIfAbsent("calc:" + what, k -> new Stat()).add(nanos);
    }

    private static void onChunkFull() {
        TOTAL_FULL.increment();
        long sec = System.nanoTime() / 1_000_000_000L;
        int idx = (int) (sec % WINDOW);
        if (BUCKET_SECOND.get(idx) != sec) {
            BUCKET_SECOND.set(idx, sec);
            BUCKET_COUNT.set(idx, 0);
        }
        BUCKET_COUNT.incrementAndGet(idx);
    }

    /** Finished chunks per second, averaged over the last N complete seconds. */
    public static double chunksPerSecond(int seconds) {
        seconds = Math.max(1, Math.min(WINDOW - 2, seconds));
        long now = System.nanoTime() / 1_000_000_000L;
        long sum = 0;
        for (int i = 1; i <= seconds; i++) {
            long sec = now - i;
            int idx = (int) (sec % WINDOW);
            if (BUCKET_SECOND.get(idx) == sec) sum += BUCKET_COUNT.get(idx);
        }
        return sum / (double) seconds;
    }

    /** A chunk that was already generated and simply loaded from disk. */
    public static void recordLoaded() {
        TOTAL_LOADED.increment();
    }

    public static long totalLoaded() {
        return TOTAL_LOADED.sum();
    }

    public static long totalChunks() {
        return TOTAL_FULL.sum();
    }

    public static void reset() {
        STATS.clear();
        TOTAL_FULL.reset();
        TOTAL_LOADED.reset();
        for (int i = 0; i < WINDOW; i++) {
            BUCKET_SECOND.set(i, 0);
            BUCKET_COUNT.set(i, 0);
        }
        resetAtMillis = System.currentTimeMillis();
    }

    public static Map<String, Stat> raw() {
        return STATS;
    }

    /** Stage names in display order. */
    public static List<String> sortedKeys() {
        List<String> keys = new ArrayList<>(STATS.keySet());
        keys.sort((a, b) -> {
            int ia = ORDER.indexOf(a), ib = ORDER.indexOf(b);
            if (ia < 0) ia = 1000;
            if (ib < 0) ib = 1000;
            return ia != ib ? Integer.compare(ia, ib) : a.compareTo(b);
        });
        return keys;
    }

    /** Copy of the statistics, sent to the client menu. */
    public static ListTag snapshot() {
        ListTag list = new ListTag();
        for (String key : sortedKeys()) {
            Stat s = STATS.get(key);
            if (s == null) continue;
            CompoundTag t = new CompoundTag();
            t.putString("name", key);
            t.putLong("count", s.count.sum());
            t.putLong("total", s.totalNanos.sum());
            t.putLong("max", s.maxNanos.get());
            list.add(t);
        }
        return list;
    }

    /** Text table for reports and the console. */
    public static List<String> formatTable() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%-22s %9s %10s %10s", "Stage", "Count", "Avg ms", "Max ms"));
        for (String key : sortedKeys()) {
            Stat s = STATS.get(key);
            if (s == null) continue;
            long c = s.count.sum();
            double avg = c == 0 ? 0 : s.totalNanos.sum() / (double) c / 1e6;
            lines.add(String.format("%-22s %9d %10.2f %10.2f", key, c, avg, s.maxNanos.get() / 1e6));
        }
        return lines;
    }

    public static long resetAtMillis() {
        return resetAtMillis;
    }

    /** Nom court d'un statut ("minecraft:noise" -> "noise"). */
    public static String shortName(Object status) {
        String s = String.valueOf(status);
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
    }
}
