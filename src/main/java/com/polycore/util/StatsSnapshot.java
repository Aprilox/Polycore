package com.polycore.util;

import com.polycore.config.PolyConfig;
import com.polycore.gen.StepProfiler;
import com.polycore.gen.WorldgenHooks;
import com.polycore.gen.WorldgenPool;
import com.polycore.network.PolyNetwork;
import com.polycore.optim.Optimization;
import com.polycore.platform.Services;
import com.polycore.pregen.PregenManager;
import com.polycore.pregen.PregenTask;
import com.polycore.pregen.Reports;
import com.polycore.settings.Settings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the full server state sent to the client menu and HUD.
 */
public final class StatsSnapshot {
    private static long lastBusy = 0;
    private static long lastSampleNanos = 0;
    private static volatile double poolUtilization = 0;

    private StatsSnapshot() {}

    /** Called once per second from the server thread. */
    public static void sample() {
        long now = System.nanoTime();
        long busy = WorldgenPool.busyNanos();
        if (lastSampleNanos != 0) {
            double capacity = (now - lastSampleNanos) * (double) WorldgenPool.size();
            poolUtilization = capacity <= 0 ? 0 : Math.min(1.0, (busy - lastBusy) / capacity);
        }
        lastBusy = busy;
        lastSampleNanos = now;
    }

    public static double poolUtilization() {
        return poolUtilization;
    }

    public static CompoundTag build(ServerPlayer player) {
        CompoundTag root = new CompoundTag();
        root.putString("protocol", PolyNetwork.PROTOCOL);
        root.putString("version", Services.PLATFORM.modVersion());
        root.putString("loader", Services.PLATFORM.loaderName());
        root.putString("mcVersion", Services.PLATFORM.minecraftVersion());
        root.putBoolean("dedicated", player.server.isDedicatedServer());
        root.putBoolean("owner", Permissions.isOwner(player));
        boolean canView = Permissions.canView(player);
        root.putBoolean("canView", canView);
        root.putBoolean("canEdit", Permissions.canEdit(player));
        if (!canView) return root;

        ListTag opts = new ListTag();
        for (Optimization o : Optimization.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", o.id);
            t.putString("name", o.displayName);
            t.putString("desc", o.description);
            t.putString("category", o.category.name());
            t.putString("kind", o.kind.name());
            t.putString("status", o.status());
            t.putBoolean("enabled", o.isEnabled());
            t.putBoolean("hook", o.isHookSeen());
            t.putBoolean("toggleable", o.isToggleable());
            String block = o.blockReason();
            if (block != null) t.putString("block", block);
            opts.add(t);
        }
        root.put("opts", opts);
        root.put("settings", Settings.toTag());

        root.putDouble("mspt", ServerMetrics.mspt());
        root.putDouble("msptMax", ServerMetrics.maxMspt());
        root.putDouble("tps", ServerMetrics.tps());
        root.putDouble("cpu", ServerMetrics.processCpu());
        root.putInt("cores", ServerMetrics.cores());
        root.putLong("memUsed", ServerMetrics.usedMemoryMb());
        root.putLong("memMax", ServerMetrics.maxMemoryMb());

        root.putInt("poolSize", WorldgenPool.size());
        root.putInt("poolMax", WorldgenPool.maxThreads());
        root.putInt("poolActive", WorldgenPool.active());
        root.putInt("poolQueued", WorldgenPool.queued());
        root.putLong("poolTasks", WorldgenPool.tasks());
        root.putDouble("poolUtil", poolUtilization);
        root.putBoolean("poolInUse", WorldgenHooks.usePool());
        root.putBoolean("genPolycore", WorldgenHooks.usePool() || WorldgenHooks.isActive(Optimization.PARALLEL_STAGES));

        root.putDouble("cps1", StepProfiler.chunksPerSecond(1));
        root.putDouble("cps10", StepProfiler.chunksPerSecond(10));
        root.putLong("chunksGenerated", StepProfiler.totalChunks());
        root.putLong("chunksLoaded", StepProfiler.totalLoaded());
        root.put("steps", StepProfiler.snapshot());
        root.put("history", History.toTag());
        root.put("lag", com.polycore.lag.LagMonitor.toTag(player));

        PregenTask task = PregenManager.current();
        boolean running = task != null && task.state() == PregenTask.State.RUNNING;
        root.putBoolean("taskRunning", running);
        if (task != null) {
            CompoundTag t = new CompoundTag();
            t.putString("label", task.label);
            t.putString("state", task.state().name());
            t.putDouble("fraction", task.progressFraction());
            t.putDouble("rate", task.rate());
            t.putLong("total", task.total());
            t.putInt("done", task.ok() + task.errors() + task.skipped());
            t.putInt("errors", task.errors());
            t.putInt("skipped", task.skipped());
            t.putInt("inFlight", task.inFlight());
            t.putInt("limit", task.currentLimit());
            t.putInt("maxLimit", task.concurrency);
            t.putBoolean("keepTps", task.keepTps);
            t.putDouble("elapsed", task.elapsedSeconds());
            t.putInt("radius", task.radius);
            t.putInt("centerX", task.centerX);
            t.putInt("centerZ", task.centerZ);
            root.put("task", t);
        }
        root.putBoolean("benchRunning", PregenManager.benchRunning());
        root.putString("benchPhase", PregenManager.benchPhase());
        if (PregenManager.lastBench() != null) root.put("lastBench", PregenManager.lastBench());
        root.put("reports", Reports.list(30));
        root.putInt("benchDefaultRadius", PolyConfig.get().benchDefaultRadius);

        root.putBoolean("moonrise", Compat.moonrise());
        root.putBoolean("c2me", Compat.c2me());
        return root;
    }
}
