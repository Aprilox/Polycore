package com.polycore.util;

import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenHooks;
import com.polycore.gen.WorldgenPool;
import com.polycore.optim.Optimization;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * Construit l'etat complet du mod (optimisations, stats, outils) envoye au menu client.
 */
public final class StatsSnapshot {
    private static long lastBusy = 0;
    private static long lastSampleNanos = 0;
    private static volatile double poolUtilization = 0;

    private StatsSnapshot() {}

    /** Appele une fois par seconde depuis le thread serveur. */
    public static void sample() {
        long now = System.nanoTime();
        long busy = WorldgenPool.busyNanos();
        if (lastSampleNanos != 0) {
            double elapsed = (now - lastSampleNanos) * (double) WorldgenPool.size();
            poolUtilization = elapsed <= 0 ? 0 : Math.min(1.0, (busy - lastBusy) / elapsed);
        }
        lastBusy = busy;
        lastSampleNanos = now;
    }

    public static double poolUtilization() {
        return poolUtilization;
    }

    public static CompoundTag build(boolean canEdit) {
        CompoundTag root = new CompoundTag();
        root.putBoolean("canEdit", canEdit);

        ListTag opts = new ListTag();
        for (Optimization o : Optimization.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", o.id);
            t.putString("name", o.displayName);
            t.putString("desc", o.description);
            t.putString("category", o.category.name());
            t.putString("kind", o.kind.name());
            t.putBoolean("enabled", o.isEnabled());
            t.putBoolean("active", o.isActive());
            t.putBoolean("hook", o.isHookSeen());
            t.putBoolean("toggleable", o.isToggleable());
            String block = o.blockReason();
            if (block != null) t.putString("block", block);
            opts.add(t);
        }
        root.put("opts", opts);

        root.putDouble("mspt", ServerMetrics.mspt());
        root.putDouble("msptMax", ServerMetrics.maxMspt());
        root.putDouble("tps", ServerMetrics.tps());
        root.putDouble("cpu", ServerMetrics.processCpu());
        root.putInt("cores", ServerMetrics.cores());
        root.putLong("memUsed", ServerMetrics.usedMemoryMb());
        root.putLong("memMax", ServerMetrics.maxMemoryMb());

        root.putInt("poolSize", WorldgenPool.size());
        root.putInt("poolActive", WorldgenPool.active());
        root.putInt("poolQueued", WorldgenPool.queued());
        root.putLong("poolTasks", WorldgenPool.tasks());
        root.putDouble("poolUtil", poolUtilization);
        root.putBoolean("poolInUse", WorldgenHooks.usePool());

        root.putDouble("cps1", StepProfiler.chunksPerSecond(1));
        root.putDouble("cps10", StepProfiler.chunksPerSecond(10));
        root.putLong("chunksGenerated", StepProfiler.totalChunks());
        root.putLong("chunksLoaded", StepProfiler.totalLoaded());
        root.put("steps", StepProfiler.snapshot());

        PregenTask task = PregenManager.current();
        boolean running = task != null && task.state() == PregenTask.State.RUNNING;
        root.putBoolean("pregenRunning", running);
        if (task != null) {
            root.putString("pregenLabel", task.label);
            root.putString("pregenLine", task.progressLine());
            root.putDouble("pregenFraction", task.progressFraction());
            root.putString("pregenState", task.state().name());
        }
        root.putBoolean("benchRunning", PregenManager.benchRunning());
        root.putString("benchPhase", PregenManager.benchPhase());

        ListTag report = new ListTag();
        for (String l : PregenManager.lastReport()) report.add(StringTag.valueOf(l));
        root.put("report", report);
        root.putString("reportFile", PregenManager.lastReportFile());

        root.putBoolean("moonrise", Compat.moonrise());
        root.putBoolean("c2me", Compat.c2me());
        return root;
    }
}
