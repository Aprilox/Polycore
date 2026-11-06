package com.polycore.lag;

import com.polycore.Polycore;
import com.polycore.config.PolyConfig;
import com.polycore.util.Notifier;
import com.polycore.util.Permissions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Always-on tick breakdown: where each server tick's time goes, the real TPS, and a log of lag spikes.
 * The mixins add the time of each part of the tick; whatever is not measured is "other".
 * Cost: a few System.nanoTime() calls per level per tick. Server thread only.
 */
public final class LagMonitor {
    public enum Section {
        CHUNKS("Chunk loading & saving"),
        SPAWNING("Mob spawning & random ticks"),
        BLOCK_TICKS("Redstone & scheduled ticks"),
        ENTITIES("Entities"),
        BLOCK_ENTITIES("Block entities"),
        NETWORK("Network & chunk sending"),
        AUTOSAVE("Autosave"),
        FUNCTIONS("Datapack functions"),
        POLYCORE("Polycore tasks"),
        OTHER("Other mods & vanilla");

        public final String label;

        Section(String label) {
            this.label = label;
        }
    }

    static final Section[] SECTIONS = Section.values();
    static final int N = SECTIONS.length;
    private static final int OTHER = Section.OTHER.ordinal();
    private static final int WINDOW = 100; // ticks = 5 s
    private static final int QUIET_TICKS = 20; // a spike ends after 1 s of normal ticks
    private static final int MAX_SPIKES = 30;
    private static final long ALERT_COOLDOWN_MS = 5 * 60_000L;
    private static final long AUTO_CAPTURE_COOLDOWN_MS = 10 * 60_000L;

    private static final long[] tick = new long[N];
    private static final long[][] ring = new long[WINDOW][N];
    private static final long[] ringTotal = new long[WINDOW];
    private static final long[] ringInterval = new long[WINDOW];
    private static int head = 0, filled = 0;
    private static long tickStart = 0, lastTickStart = 0, interval = 0;
    private static float targetTps = 20f;

    private static final class Spike {
        final long startMillis = System.currentTimeMillis();
        long endMillis = startMillis;
        int slowTicks, quiet;
        long worstNanos;
        final long[] sections = new long[N];
    }

    private static Spike open = null;
    private static final Deque<Spike> spikes = new ArrayDeque<>();

    private static int secondTicks = 0;
    private static boolean wasLagging = false;
    private static long lastAlert = 0, lastAutoCapture = 0;

    private LagMonitor() {}

    /** Adds time to a section of the current tick (called by the mixins). */
    public static void add(Section s, long nanos) {
        tick[s.ordinal()] += nanos;
    }

    public static void tickStart() {
        long now = System.nanoTime();
        // Longer gaps are a paused singleplayer game, not lag: they would distort the TPS for 5 s.
        interval = lastTickStart == 0 || now - lastTickStart > 5_000_000_000L ? 0 : now - lastTickStart;
        lastTickStart = now;
        tickStart = now;
        Arrays.fill(tick, 0);
    }

    public static void tickEnd(MinecraftServer server) {
        if (tickStart == 0) return;
        long total = System.nanoTime() - tickStart;
        long measured = 0;
        for (int i = 0; i < N; i++) {
            if (i == OTHER) continue;
            if (tick[i] < 0) tick[i] = 0; // a nested section without its parent (injection skipped)
            measured += tick[i];
        }
        tick[OTHER] = Math.max(0, total - measured);
        System.arraycopy(tick, 0, ring[head], 0, N);
        ringTotal[head] = total;
        ringInterval[head] = interval;
        head = (head + 1) % WINDOW;
        if (filled < WINDOW) filled++;
        targetTps = server.tickRateManager().tickrate();

        trackSpike(total);
        LagCapture.onTick(tick, total, server);
        if (++secondTicks >= 20) {
            secondTicks = 0;
            checkSustainedLag(server);
        }
    }

    private static void trackSpike(long total) {
        if (total > PolyConfig.get().lagThresholdMs * 1_000_000L) {
            if (open == null) open = new Spike();
            open.slowTicks++;
            open.quiet = 0;
            open.endMillis = System.currentTimeMillis();
            open.worstNanos = Math.max(open.worstNanos, total);
            for (int i = 0; i < N; i++) open.sections[i] += tick[i];
        } else if (open != null && ++open.quiet >= QUIET_TICKS) {
            spikes.addFirst(open);
            while (spikes.size() > MAX_SPIKES) spikes.removeLast();
            open = null;
        }
    }

    /** Once per second: alerts and automatic analysis when the 5 s average goes over the threshold. */
    private static void checkSustainedLag(MinecraftServer server) {
        boolean lagging = filled >= WINDOW && avgMspt() > PolyConfig.get().lagThresholdMs;
        if (lagging && !wasLagging) {
            long now = System.currentTimeMillis();
            long[] sums = windowSums();
            int top = topSection(sums);
            String cause = String.format(Locale.ROOT, "%.1f TPS, %.0f ms per tick. Main cause: %s (%.1f ms)",
                    tps(), avgMspt(), SECTIONS[top].label, sums[top] / (double) filled / 1e6);
            if (now - lastAlert > ALERT_COOLDOWN_MS) {
                lastAlert = now;
                alert(server, Notifier.Level.WARN, "Server is lagging", cause);
            }
            if (PolyConfig.get().lagAutoCapture && !LagCapture.running() && now - lastAutoCapture > AUTO_CAPTURE_COOLDOWN_MS) {
                lastAutoCapture = now;
                LagCapture.start(PolyConfig.get().lagCaptureSeconds, null, true);
            }
        }
        wasLagging = lagging;
    }

    // ------------------------------------------------------------------ alerts (opt-in, operators only)

    public static boolean alertsOn(ServerPlayer p) {
        return PolyConfig.get().lagAlertPlayers.contains(p.getStringUUID());
    }

    public static void setAlerts(ServerPlayer p, boolean on) {
        List<String> list = PolyConfig.get().lagAlertPlayers;
        list.remove(p.getStringUUID());
        if (on) list.add(p.getStringUUID());
        PolyConfig.save();
    }

    /** Toast (or action bar) to the operators who turned alerts on; the console always gets the text. */
    static void alert(MinecraftServer server, Notifier.Level level, String title, String message) {
        boolean sent = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Permissions.canEdit(p) && alertsOn(p)) {
                Notifier.send(p, level, title, message);
                sent = true;
            }
        }
        if (!sent) Polycore.LOGGER.info("[Polycore] {}: {}", title, message);
    }

    // ------------------------------------------------------------------ figures

    private static long[] windowSums() {
        long[] sums = new long[N];
        for (int t = 0; t < filled; t++) for (int i = 0; i < N; i++) sums[i] += ring[t][i];
        return sums;
    }

    static int topSection(long[] sums) {
        int best = 0;
        for (int i = 1; i < sums.length; i++) if (sums[i] > sums[best]) best = i;
        return best;
    }

    /** Average tick time over the last 5 s (ms). */
    public static double avgMspt() {
        if (filled == 0) return 0;
        long sum = 0;
        for (int t = 0; t < filled; t++) sum += ringTotal[t];
        return sum / (double) filled / 1e6;
    }

    public static double worstMspt() {
        long max = 0;
        for (int t = 0; t < filled; t++) max = Math.max(max, ringTotal[t]);
        return max / 1e6;
    }

    /** Ticks really completed per second (time between tick starts), over the last 5 s. */
    public static double tps() {
        long sum = 0;
        int n = 0;
        for (int t = 0; t < filled; t++) {
            if (ringInterval[t] > 0) {
                sum += ringInterval[t];
                n++;
            }
        }
        return n == 0 ? targetTps : Math.min(targetTps, 1e9 / (sum / (double) n));
    }

    public static CompoundTag toTag(ServerPlayer viewer) {
        CompoundTag t = new CompoundTag();
        t.putDouble("tps", tps());
        t.putDouble("mspt", avgMspt());
        t.putDouble("worst", worstMspt());
        long[] sums = windowSums();
        ListTag sections = new ListTag();
        for (Section s : SECTIONS) {
            CompoundTag e = new CompoundTag();
            e.putString("name", s.label);
            e.putDouble("ms", filled == 0 ? 0 : sums[s.ordinal()] / (double) filled / 1e6);
            sections.add(e);
        }
        t.put("sections", sections);
        ListTag list = new ListTag();
        if (open != null) list.add(spikeTag(open, true));
        for (Spike s : spikes) {
            if (list.size() >= 10) break;
            list.add(spikeTag(s, false));
        }
        t.put("spikes", list);
        t.putBoolean("alerts", alertsOn(viewer));
        t.putInt("threshold", PolyConfig.get().lagThresholdMs);
        t.put("capture", LagCapture.toTag());
        return t;
    }

    private static CompoundTag spikeTag(Spike s, boolean ongoing) {
        CompoundTag t = new CompoundTag();
        t.putLong("start", s.startMillis);
        t.putLong("end", s.endMillis);
        t.putInt("ticks", s.slowTicks);
        t.putDouble("worst", s.worstNanos / 1e6);
        int top = topSection(s.sections);
        t.putString("cause", SECTIONS[top].label);
        t.putDouble("causeMs", s.sections[top] / (double) Math.max(1, s.slowTicks) / 1e6);
        t.putBoolean("ongoing", ongoing);
        return t;
    }

    /** Text for the console (/polycore lag). */
    public static List<String> textSummary() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "=== Lag analyzer: %.1f TPS, %.1f ms per tick (worst %.1f ms), last 5 s ===", tps(), avgMspt(), worstMspt()));
        long[] sums = windowSums();
        for (Section s : SECTIONS) {
            lines.add(String.format(Locale.ROOT, "  %-30s %7.2f ms", s.label, filled == 0 ? 0 : sums[s.ordinal()] / (double) filled / 1e6));
        }
        int shown = 0;
        for (Spike s : spikes) {
            if (shown++ >= 5) break;
            int top = topSection(s.sections);
            lines.add(String.format(Locale.ROOT, "  Spike at %tT: %d slow ticks, worst %.0f ms, mostly %s", s.startMillis, s.slowTicks,
                    s.worstNanos / 1e6, SECTIONS[top].label));
        }
        lines.addAll(LagCapture.textSummary());
        return lines;
    }

    public static void reset() {
        Arrays.fill(tick, 0);
        head = filled = secondTicks = 0;
        tickStart = lastTickStart = interval = 0;
        open = null;
        spikes.clear();
        wasLagging = false;
    }
}
