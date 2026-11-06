package com.polycore.util;

import java.lang.management.ManagementFactory;

/**
 * Mesures du serveur : temps par tick (MSPT), TPS, CPU, memoire.
 * Les methodes onTick* sont appelees depuis le thread serveur.
 */
public final class ServerMetrics {
    private static final int SAMPLES = 100;
    private static final long[] TICK_NANOS = new long[SAMPLES];
    private static int index = 0;
    private static int filled = 0;
    private static long tickStart = 0;

    // Accumulateurs pour une phase de benchmark.
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

    /** Temps moyen d'un tick sur les 100 derniers ticks (ms). */
    public static double mspt() {
        return avgMspt;
    }

    public static double maxMspt() {
        return maxMspt;
    }

    /** TPS estime : 20 si le tick tient en 50 ms, moins sinon. */
    public static double tps() {
        double m = avgMspt;
        return m <= 50.0 ? 20.0 : 1000.0 / m;
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

    /** Charge CPU du processus Java (0 a 1), ou -1 si indisponible. */
    public static double processCpu() {
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getProcessCpuLoad();
            }
        } catch (Throwable ignored) {
        }
        return -1;
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
