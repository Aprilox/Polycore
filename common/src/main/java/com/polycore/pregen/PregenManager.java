package com.polycore.pregen;

import com.polycore.config.PolyConfig;
import com.polycore.Polycore;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenHooks;
import com.polycore.gen.WorldgenPool;
import com.polycore.optim.Optimization;
import com.polycore.util.Notifier;
import com.polycore.util.ServerMetrics;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Runs pre-generation and the A/B benchmark. Server thread only.
 * Feedback goes through {@link Notifier} (toasts / action bar), never the chat.
 * Players without Polycore on their client see progress in a boss bar.
 */
public final class PregenManager {
    private static PregenTask current;
    private static Bench bench;

    /** Owner of the running task (gets the boss bar if their client lacks Polycore). */
    private static UUID ownerId;
    private static ServerBossEvent bossBar;

    /** Last benchmark summary, shown on the dashboard. */
    private static CompoundTag lastBench = null;

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PregenManager() {}

    public static PregenTask current() {
        return current;
    }

    public static boolean isBusy() {
        return (current != null && current.state() == PregenTask.State.RUNNING) || bench != null;
    }

    public static boolean benchRunning() {
        return bench != null;
    }

    public static String benchPhase() {
        return bench == null ? "" : bench.phaseName();
    }

    public static CompoundTag lastBench() {
        return lastBench;
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (current != null) current.tick();
        if (bench != null) bench.tick();
        updateBossBar(server);
    }

    // ------------------------------------------------------------------ pre-generation

    public static boolean startPregen(ServerLevel level, int cx, int cz, int radius, int concurrency, boolean keepTps,
                                      @Nullable ServerPlayer owner, CommandSourceStack source) {
        if (isBusy()) {
            Notifier.send(source, Notifier.Level.WARN, "Busy", "A pre-generation or benchmark is already running.");
            return false;
        }
        int conc = concurrency > 0 ? concurrency : PolyConfig.pregenConcurrency();
        current = new PregenTask(level, cx, cz, radius, conc, keepTps, true, "Pre-generation",
                t -> Notifier.send(source, t.state() == PregenTask.State.DONE ? Notifier.Level.SUCCESS : Notifier.Level.WARN,
                        t.state() == PregenTask.State.DONE ? "Pre-generation finished" : "Pre-generation stopped",
                        String.format("%d chunks in %s (%.1f chunks/s)", t.ok(), PregenTask.formatSeconds(t.elapsedSeconds()), t.rate())));
        current.start();
        setOwner(owner);
        Notifier.send(source, Notifier.Level.INFO, "Pre-generation started",
                String.format("%d chunks around chunk %d, %d%s", current.total(), cx, cz, keepTps ? " (keeping TPS stable)" : ""));
        return true;
    }

    public static boolean stop(CommandSourceStack source) {
        boolean any = false;
        if (bench != null) {
            bench = null;
            any = true;
        }
        if (current != null && current.state() == PregenTask.State.RUNNING) {
            current.stop();
            any = true;
        }
        WorldgenHooks.benchOverride = null;
        Notifier.send(source, any ? Notifier.Level.INFO : Notifier.Level.WARN, "Stop", any ? "Task stopped." : "Nothing was running.");
        return any;
    }

    /** Silent stop (server shutting down). */
    public static void shutdown() {
        bench = null;
        if (current != null && current.state() == PregenTask.State.RUNNING) current.stop();
        WorldgenHooks.benchOverride = null;
        if (bossBar != null) bossBar.removeAllPlayers();
        bossBar = null;
        ownerId = null;
    }

    // ------------------------------------------------------------------ parity hash

    public static void hash(ServerLevel level, int cx, int cz, int radius, CommandSourceStack source) {
        ParityHash.Result r = ParityHash.compute(level, cx, cz, radius);
        if (r.terrainChunks() == r.chunks()) {
            Notifier.send(source, Notifier.Level.SUCCESS, "Terrain fingerprint " + r.terrain(),
                    r.chunks() + " chunks. Same seed and place must give the same value, whatever the generation order.");
        } else {
            Notifier.send(source, Notifier.Level.WARN, "Terrain fingerprint incomplete",
                    r.terrainChunks() + " of " + r.chunks() + " chunks were recorded: generate a fresh area with recording on. Full: " + r.full());
        }
    }

    // ------------------------------------------------------------------ boss bar for vanilla clients

    private static void setOwner(@Nullable ServerPlayer owner) {
        ownerId = owner == null ? null : owner.getUUID();
        if (bossBar != null) bossBar.removeAllPlayers();
        bossBar = null;
        if (owner != null && !Notifier.hasClientMod(owner)) {
            bossBar = new ServerBossEvent(Component.literal("Polycore"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
            bossBar.addPlayer(owner);
        }
    }

    private static void updateBossBar(net.minecraft.server.MinecraftServer server) {
        if (bossBar == null) return;
        if (!isBusy()) {
            bossBar.removeAllPlayers();
            bossBar = null;
            return;
        }
        PregenTask t = current;
        if (t == null) return;
        String phase = bench != null ? "Benchmark (" + bench.phaseName() + ")" : t.label;
        bossBar.setName(Component.literal(String.format("Polycore - %s: %.0f%% - %.1f chunks/s", phase, t.progressFraction() * 100, t.rate())));
        bossBar.setProgress((float) Math.max(0, Math.min(1, t.progressFraction())));
        if (ownerId != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(ownerId);
            if (p != null && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        }
    }

    // ------------------------------------------------------------------ benchmark

    public static boolean startBench(ServerLevel level, int radius, @Nullable ServerPlayer owner, CommandSourceStack source) {
        if (isBusy()) {
            Notifier.send(source, Notifier.Level.WARN, "Busy", "A pre-generation or benchmark is already running.");
            return false;
        }
        bench = new Bench(level, radius, source);
        setOwner(owner);
        Notifier.send(source, Notifier.Level.INFO, "Benchmark started",
                "3 phases of " + (2 * radius + 1) * (2 * radius + 1) + " chunks in fresh areas. Use a test world.");
        bench.next();
        return true;
    }

    private record PhaseResult(String name, int chunks, double seconds, double rate, double avgMspt, double maxMspt,
                               double avgCpu, boolean optimized, int concurrency, List<String> steps) {}

    /**
     * A/B benchmark: two fresh areas of the same size, far from everything. "Baseline" runs with every Polycore
     * generation optimization off and 32 chunks in flight (like /neoforge generate); "Polycore" runs with them on.
     * A short warm-up runs first so the JIT does not favour the second phase.
     */
    private static final class Bench {
        private final ServerLevel level;
        private final int radius;
        private final CommandSourceStack source;
        private final int baseX;
        private final int baseZ;
        private final List<PhaseResult> results = new ArrayList<>();
        private int phase = -1; // 0 = warm-up, 1 = baseline, 2 = Polycore
        private double cpuSum = 0;
        private int cpuSamples = 0;
        private int tickCounter = 0;

        Bench(ServerLevel level, int radius, CommandSourceStack source) {
            this.level = level;
            this.radius = radius;
            this.source = source;
            Random r = new Random();
            this.baseX = 100_000 + r.nextInt(50_000);
            this.baseZ = 100_000 + r.nextInt(50_000);
        }

        String phaseName() {
            return switch (phase) {
                case 0 -> "warm-up";
                case 1 -> "baseline";
                case 2 -> "Polycore";
                default -> "";
            };
        }

        void next() {
            if (bench != this) return;
            phase++;
            if (phase > 2) {
                finishReport();
                return;
            }
            int phaseRadius = phase == 0 ? Math.min(4, radius) : radius;
            int offset = (2 * radius + 1) + 64;
            boolean optimized = phase != 1;
            int conc = phase == 1 ? PolyConfig.get().benchBaselineConcurrency : PolyConfig.pregenConcurrency();
            WorldgenHooks.benchOverride = optimized;
            StepProfiler.reset();
            ServerMetrics.phaseReset();
            cpuSum = 0;
            cpuSamples = 0;
            current = new PregenTask(level, baseX + phase * offset, baseZ, phaseRadius, conc, false, false,
                    "Benchmark " + phaseName(), this::onPhaseDone);
            current.start();
        }

        void tick() {
            if (++tickCounter % 20 == 0) {
                double cpu = ServerMetrics.processCpu();
                if (cpu >= 0) {
                    cpuSum += cpu;
                    cpuSamples++;
                }
            }
        }

        private void onPhaseDone(PregenTask t) {
            if (bench != this || t.state() != PregenTask.State.DONE) return;
            if (phase >= 1) {
                results.add(new PhaseResult(phaseName(), t.ok(), t.elapsedSeconds(), t.rate(),
                        ServerMetrics.phaseAvgMspt(), ServerMetrics.phaseMaxMspt(),
                        cpuSamples == 0 ? -1 : cpuSum / cpuSamples, phase == 2, t.concurrency, StepProfiler.formatTable()));
            }
            Polycore.runNextTick(this::next);
        }

        private void finishReport() {
            WorldgenHooks.benchOverride = null;
            bench = null;
            List<String> lines = new ArrayList<>();
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            lines.add("=== Polycore benchmark - " + date + " ===");
            lines.add(String.format("Machine: %d logical cores, max RAM %d MB, Polycore pool %d threads",
                    ServerMetrics.cores(), ServerMetrics.maxMemoryMb(), WorldgenPool.size()));
            lines.add(String.format("Area: radius %d (%d chunks per phase), dimension %s",
                    radius, (2 * radius + 1) * (2 * radius + 1), level.dimension().location()));
            String block = Optimization.WORLDGEN_POOL.blockReason();
            if (block != null) lines.add("Warning: " + block + " - only concurrency changes between phases.");
            lines.add("");
            for (PhaseResult r : results) {
                lines.add(String.format("[%s] optimizations %s, %d chunks in flight", r.name(), r.optimized() ? "ON" : "OFF", r.concurrency()));
                lines.add(String.format("  %d chunks in %.1f s -> %.1f chunks/s", r.chunks(), r.seconds(), r.rate()));
                lines.add(String.format("  Avg MSPT %.1f ms (max %.1f ms), avg CPU %s",
                        r.avgMspt(), r.maxMspt(), r.avgCpu() < 0 ? "?" : String.format("%.0f%%", r.avgCpu() * 100)));
                for (String s : r.steps()) lines.add("    " + s);
                lines.add("");
            }
            CompoundTag summary = new CompoundTag();
            if (results.size() == 2) {
                PhaseResult a = results.get(0), b = results.get(1);
                double gain = a.rate() > 0 ? (b.rate() / a.rate() - 1) * 100 : 0;
                lines.add(String.format("RESULT: %.1f -> %.1f chunks/s, %+.0f%% generation speed", a.rate(), b.rate(), gain));
                lines.add("Note: the two areas have different terrain; small radii can vary by 5-10%.");
                summary.putDouble("baseline", a.rate());
                summary.putDouble("polycore", b.rate());
                summary.putDouble("gain", gain);
                summary.putDouble("baselineMspt", a.avgMspt());
                summary.putDouble("polycoreMspt", b.avgMspt());
                summary.putString("date", date);
                summary.putInt("radius", radius);
            }
            Path file = Reports.write("bench-" + LocalDateTime.now().format(FILE_TIME) + ".txt", lines);
            if (file != null) summary.putString("file", file.getFileName().toString());
            lastBench = summary;
            Polycore.LOGGER.info("[Polycore] Benchmark report:\n{}", String.join("\n", lines));
            if (summary.contains("gain")) {
                Notifier.send(source, Notifier.Level.SUCCESS, "Benchmark finished",
                        String.format("%.1f -> %.1f chunks/s (%+.0f%%)", summary.getDouble("baseline"),
                                summary.getDouble("polycore"), summary.getDouble("gain")));
            } else {
                Notifier.send(source, Notifier.Level.WARN, "Benchmark finished", "Incomplete results, see the report.");
            }
        }
    }
}
