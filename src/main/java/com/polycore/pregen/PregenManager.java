package com.polycore.pregen;

import com.polycore.PolyConfig;
import com.polycore.PolyCore;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenHooks;
import com.polycore.gen.WorldgenPool;
import com.polycore.optim.Optimization;
import com.polycore.util.ServerMetrics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Gere la pre-generation en cours et le benchmark A/B. Thread serveur uniquement.
 */
public final class PregenManager {
    private static PregenTask current;
    private static Bench bench;

    private static List<String> lastReport = List.of("No benchmark has been run yet.",
            "Run /polycore bench or use the 'A/B benchmark' button in the menu.");
    private static String lastReportFile = "";

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

    public static List<String> lastReport() {
        return lastReport;
    }

    public static String lastReportFile() {
        return lastReportFile;
    }

    public static void tick() {
        if (current != null) current.tick();
        if (bench != null) bench.tick();
    }

    /** Lance une pre-generation. Retourne un message d'erreur, ou null si tout va bien. */
    public static String startPregen(ServerLevel level, int cx, int cz, int radius, int concurrency,
                                     Consumer<Component> out) {
        if (isBusy()) return "A pre-generation or benchmark is already running. Use /polycore pregen stop.";
        if (!Optimization.PARALLEL_PREGEN.isActive()) return "Parallel pre-generation is disabled.";
        int conc = concurrency > 0 ? concurrency : PolyConfig.pregenConcurrency();
        current = new PregenTask(level, cx, cz, radius, conc, true, "Pre-generation",
                line -> out.accept(Component.literal("[Polycore] " + line)),
                t -> out.accept(Component.literal("[Polycore] Pre-generation "
                        + (t.state() == PregenTask.State.DONE ? "finished" : "stopped") + ": " + t.progressLine())));
        current.start();
        out.accept(Component.literal(String.format(
                "[Polycore] Pre-generation started: %d chunks around chunk (%d, %d), %d in parallel.",
                current.total(), cx, cz, conc)));
        return null;
    }

    public static boolean stop() {
        boolean any = false;
        if (bench != null) {
            bench.abort();
            bench = null;
            any = true;
        }
        if (current != null && current.state() == PregenTask.State.RUNNING) {
            current.stop();
            any = true;
        }
        WorldgenHooks.poolOverride = null;
        return any;
    }

    /** Lance le benchmark A/B. Retourne un message d'erreur, ou null. */
    public static String startBench(ServerLevel level, int radius, Consumer<Component> out) {
        if (isBusy()) return "A pre-generation or benchmark is already running. Use /polycore pregen stop.";
        bench = new Bench(level, radius, out);
        bench.next();
        return null;
    }

    // ------------------------------------------------------------------------------------------

    private record PhaseResult(String name, int chunks, double seconds, double rate, double avgMspt, double maxMspt,
                               double avgCpu, boolean pool, int concurrency, List<String> steps) {}

    /**
     * Benchmark A/B : genere deux zones neuves de meme taille, loin de tout, une fois en mode "reference"
     * (sans le pool, 32 chunks en parallele comme /neoforge generate) puis en mode "Polycore".
     * Une petite phase de chauffe passe avant, pour que le JIT de Java ne favorise pas la 2e phase.
     */
    private static final class Bench {
        private final ServerLevel level;
        private final int radius;
        private final Consumer<Component> out;
        private final int baseX;
        private final int baseZ;
        private final List<PhaseResult> results = new ArrayList<>();
        private int phase = -1; // 0 = chauffe, 1 = reference, 2 = Polycore
        private double cpuSum = 0;
        private int cpuSamples = 0;
        private int tickCounter = 0;

        Bench(ServerLevel level, int radius, Consumer<Component> out) {
            this.level = level;
            this.radius = radius;
            this.out = out;
            Random r = new Random();
            // Zone lointaine et neuve (en chunks), bien a l'interieur de la bordure du monde.
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
            phase++;
            if (phase > 2) {
                finishReport();
                return;
            }
            int phaseRadius = phase == 0 ? Math.min(4, radius) : radius;
            int offset = (2 * radius + 1) + 64;
            int cx = baseX + phase * offset;
            int cz = baseZ;
            boolean pool = phase != 1;
            int conc = phase == 1 ? PolyConfig.safeGet(PolyConfig.BENCH_BASELINE_CONCURRENCY, 32)
                    : PolyConfig.pregenConcurrency();
            WorldgenHooks.poolOverride = pool;
            StepProfiler.reset();
            ServerMetrics.phaseReset();
            cpuSum = 0;
            cpuSamples = 0;
            out.accept(Component.literal(String.format("[Polycore] Benchmark - phase %d/3 (%s): radius %d, pool %s, %d in parallel",
                    phase + 1, phaseName(), phaseRadius, pool ? "ON" : "OFF", conc)));
            current = new PregenTask(level, cx, cz, phaseRadius, conc, false, "Benchmark " + phaseName(),
                    line -> out.accept(Component.literal("[Polycore] " + phaseName() + ": " + line)),
                    this::onPhaseDone);
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
            if (t.state() != PregenTask.State.DONE) return;
            if (phase >= 1) {
                results.add(new PhaseResult(phaseName(), t.ok(), t.elapsedSeconds(), t.rate(),
                        ServerMetrics.phaseAvgMspt(), ServerMetrics.phaseMaxMspt(),
                        cpuSamples == 0 ? -1 : cpuSum / cpuSamples,
                        Boolean.TRUE.equals(WorldgenHooks.poolOverride) && Optimization.WORLDGEN_POOL.blockReason() == null,
                        t.concurrency, StepProfiler.formatTable()));
            }
            // La phase suivante demarre au prochain tick, pas dans le rappel de la tache.
            PolyCore.runNextTick(this::next);
        }

        void abort() {
            WorldgenHooks.poolOverride = null;
            out.accept(Component.literal("[Polycore] Benchmark cancelled."));
        }

        private void finishReport() {
            WorldgenHooks.poolOverride = null;
            bench = null;
            List<String> lines = new ArrayList<>();
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            lines.add("=== Benchmark Polycore - " + date + " ===");
            lines.add(String.format("Machine: %d logical cores, max RAM %d MB, Polycore pool %d threads",
                    ServerMetrics.cores(), ServerMetrics.maxMemoryMb(), WorldgenPool.size()));
            lines.add(String.format("Area: radius %d (%d chunks per phase), dimension %s",
                    radius, (2 * radius + 1) * (2 * radius + 1), level.dimension().location()));
            String block = Optimization.WORLDGEN_POOL.blockReason();
            if (block != null) lines.add("Warning: pool disabled (" + block + "), only concurrency changes.");
            lines.add("");
            for (PhaseResult r : results) {
                lines.add(String.format("[%s] pool %s, %d in parallel", r.name(), r.pool() ? "ON" : "OFF", r.concurrency()));
                lines.add(String.format("  %d chunks in %.1f s -> %.1f chunks/s", r.chunks(), r.seconds(), r.rate()));
                lines.add(String.format("  Avg MSPT %.1f ms (max %.1f ms), avg CPU %s",
                        r.avgMspt(), r.maxMspt(), r.avgCpu() < 0 ? "?" : String.format("%.0f%%", r.avgCpu() * 100)));
                for (String s : r.steps()) lines.add("    " + s);
                lines.add("");
            }
            if (results.size() == 2) {
                PhaseResult a = results.get(0), b = results.get(1);
                double gain = a.rate() > 0 ? (b.rate() / a.rate() - 1) * 100 : 0;
                lines.add(String.format("RESULT: %.1f -> %.1f chunks/s, %+.0f%% generation speed",
                        a.rate(), b.rate(), gain));
                lines.add("Note: the two areas have different terrain; with a small radius the result can vary by 5-10%.");
                lines.add("Run again with a larger radius (e.g. /polycore bench 16) for a more reliable result.");
            }
            lastReport = lines;
            try {
                Path dir = FMLPaths.GAMEDIR.get().resolve("polycore-reports");
                Files.createDirectories(dir);
                Path file = dir.resolve("bench-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
                Files.write(file, lines, StandardCharsets.UTF_8);
                lastReportFile = file.toString();
            } catch (IOException e) {
                PolyCore.LOGGER.error("[Polycore] Could not write the report", e);
            }
            for (String l : lines) {
                if (!l.startsWith("    ")) out.accept(Component.literal(l));
            }
            if (!lastReportFile.isEmpty()) out.accept(Component.literal("Full report: " + lastReportFile));
            PolyCore.LOGGER.info("[Polycore] Benchmark report:\n{}", String.join("\n", lines));
        }
    }
}
