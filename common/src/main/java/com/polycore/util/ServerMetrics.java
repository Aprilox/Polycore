package com.polycore.util;

import java.lang.management.ManagementFactory;

/**
 * Server measurements: tick time (MSPT), TPS, CPU, memory.
 * The onTick* methods are called from the server thread.
 */
public final class ServerMetrics {
    private static final int SAMPLES = 100;
    private static final long[] TICK_NANOS = new long[SAMPLES];
    private static int index = 0;
    private static int filled = 0;
    private static long tickStart = 0;

    // Accumulators for one benchmark phase.
    private static long phaseSum = 0;
    private static long phaseCount = 0;
    private static long phaseMax = 0;

    private static volatile double avgMspt = 0;
    private static volatile double maxMspt = 0;

    private ServerMetrics() {}

    public static void onTickStart() {
        tickStart = System.nanoTime();
    }

    public static void onTickEnd() {
        if (tickStart == 0) return;
        long dt = System.nanoTime() - tickStart;
        TICK_NANOS[index] = dt;
        index = (index + 1) % SAMPLES;
        if (filled < SAMPLES) filled++;
        long sum = 0, max = 0;
        for (int i = 0; i < filled; i++) {
            sum += TICK_NANOS[i];
            max = Math.max(max, TICK_NANOS[i]);
        }
        avgMspt = sum / (double) filled / 1e6;
        maxMspt = max / 1e6;
        phaseSum += dt;
        phaseCount++;
        phaseMax = Math.max(phaseMax, dt);
    }

    /** Average tick time over the last 100 ticks (ms). */
    public static double mspt() {
        return avgMspt;
    }

    public static double maxMspt() {
        return maxMspt;
    }

    /** Real TPS: ticks actually completed per second over the last 5 s (see LagMonitor). */
    public static double tps() {
        return com.polycore.lag.LagMonitor.tps();
    }

    public static void phaseReset() {
        phaseSum = 0;
        phaseCount = 0;
        phaseMax = 0;
    }

    public static double phaseAvgMspt() {
        return phaseCount == 0 ? 0 : phaseSum / (double) phaseCount / 1e6;
    }

    public static double phaseMaxMspt() {
        return phaseMax / 1e6;
    }

    private static long lastCpuNanos = -1;
    private static long lastCpuWall = 0;
    private static volatile double cpuLoad = -1;

    /**
     * Called once per second on the server thread. The load is computed from the process CPU time (~0.02 ms)
     * because getProcessCpuLoad() takes about 100 ms per call on Windows and would lag the server every second.
     */
    public static void sampleCpu() {
        long cpu = -1;
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean sun) {
                cpu = sun.getProcessCpuTime();
            }
        } catch (Throwable ignored) {
        }
        if (cpu < 0) return;
        long wall = System.nanoTime();
        if (lastCpuNanos >= 0 && wall > lastCpuWall) {
            cpuLoad = Math.max(0, Math.min(1, (cpu - lastCpuNanos) / (double) ((wall - lastCpuWall) * cores())));
        }
        lastCpuNanos = cpu;
        lastCpuWall = wall;
    }

    /** CPU load of the Java process (0 to 1, normalized over all cores) over the last second, or -1 if unavailable. */
    public static double processCpu() {
        return cpuLoad;
    }

    public static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static long usedMemoryMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    public static long maxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }
}
