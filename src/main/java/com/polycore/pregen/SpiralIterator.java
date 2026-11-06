package com.polycore.pregen;

import java.util.NoSuchElementException;

/**
 * Walks a square of chunks in a spiral from the center.
 * Neighbouring chunks are generated one after another, which reuses the work already done on their edges.
 * Returns positions relative to the center, packed in a long (x in the low 32 bits, z in the high bits).
 */
public final class SpiralIterator {
    private final int radius;
    private final long total;
    private long emitted = 0;
    private int x = 0, z = 0;
    private int dx = 1, dz = 0;
    private int segmentLength = 1, segmentPassed = 0, turns = 0;

    public SpiralIterator(int radius) {
        this.radius = radius;
        long d = 2L * radius + 1;
        this.total = d * d;
    }

    public long total() {
        return total;
    }

    public long emitted() {
        return emitted;
    }

    public boolean hasNext() {
        return emitted < total;
    }

    /** Prochaine position relative (x, z) encodee. */
    public long next() {
        if (!hasNext()) throw new NoSuchElementException();
        while (true) {
            int cx = x, cz = z;
            step();
            if (Math.abs(cx) <= radius && Math.abs(cz) <= radius) {
                emitted++;
                return pack(cx, cz);
            }
        }
    }

    private void step() {
        x += dx;
        z += dz;
        segmentPassed++;
        if (segmentPassed == segmentLength) {
            segmentPassed = 0;
            // turn 90 degrees
            int t = dx;
            dx = -dz;
            dz = t;
            turns++;
            if (turns % 2 == 0) segmentLength++;
        }
    }

    public static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    public static int unpackX(long p) {
        return (int) p;
    }

    public static int unpackZ(long p) {
        return (int) (p >> 32);
    }
}
