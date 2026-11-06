package com.polycore.util;

import com.polycore.gen.StepProfiler;
import net.minecraft.nbt.CompoundTag;

/**
 * Last two minutes of key metrics, one sample per second, for the menu graphs.
 * Values are stored as ints: chunks/s and MSPT x10, CPU and pool utilization in percent.
 */
public final class History {
    public static final int SIZE = 120;

    private static final int[] CPS = new int[SIZE];
    private static final int[] MSPT = new int[SIZE];
    private static final int[] CPU = new int[SIZE];
    private static final int[] POOL = new int[SIZE];
    private static int head = 0;
    private static int count = 0;

    private History() {}

    /** Called once per second from the server thread. */
    public static synchronized void sample() {
        CPS[head] = (int) Math.round(StepProfiler.chunksPerSecond(1) * 10);
        MSPT[head] = (int) Math.round(ServerMetrics.mspt() * 10);
        double cpu = ServerMetrics.processCpu();
        CPU[head] = cpu < 0 ? 0 : (int) Math.round(cpu * 100);
        POOL[head] = (int) Math.round(StatsSnapshot.poolUtilization() * 100);
        head = (head + 1) % SIZE;
        if (count < SIZE) count++;
    }

    /** Oldest sample first. */
    private static int[] ordered(int[] ring) {
        int[] out = new int[count];
        int start = (head - count + SIZE) % SIZE;
        for (int i = 0; i < count; i++) out[i] = ring[(start + i) % SIZE];
        return out;
    }

    public static synchronized CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putIntArray("cps", ordered(CPS));
        t.putIntArray("mspt", ordered(MSPT));
        t.putIntArray("cpu", ordered(CPU));
        t.putIntArray("pool", ordered(POOL));
        return t;
    }

    public static synchronized void reset() {
        head = 0;
        count = 0;
    }
}
