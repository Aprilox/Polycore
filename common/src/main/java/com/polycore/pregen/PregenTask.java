package com.polycore.pregen;

import com.polycore.config.PolyConfig;
import com.polycore.util.ServerMetrics;
import com.polycore.Polycore;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Parallel pre-generation.
 *
 * <p>A load ticket (FULL level) is placed on many chunks at once, so Minecraft generates them with all of its
 * threads. Every tick, finished chunks get their ticket removed (they are saved, then unloaded) and new ones
 * are requested.</p>
 *
 * <p>Tickets are only touched on the server thread ({@link #tick()}). Works with the vanilla chunk system
 * and with Moonrise.</p>
 */
public final class PregenTask {
    // Not registered: only persisted tickets need a registry name (Ticket.CODEC), this one is never saved.
    public static final TicketType TICKET = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

    public enum State { RUNNING, DONE, STOPPED }

    public final ServerLevel level;
    public final int centerX;
    public final int centerZ;
    public final int radius;
    /** Maximum chunks in flight. */
    public final int concurrency;
    /** Adaptive mode: lowers the chunks in flight when the tick time goes over the target. */
    public final boolean keepTps;
    public final String label;
    /** Current limit (equals concurrency unless keepTps throttles it). */
    private int currentLimit;
    private int adjustTicks = 0;
    private final boolean skipExisting;

    private final SpiralIterator iterator;
    private final long total;

    /** Positions ready to be requested (filled by the scanner thread). */
    private final ConcurrentLinkedQueue<Long> ready = new ConcurrentLinkedQueue<>();
    /** Requested chunk -> request time (ns). Server thread only. */
    private final Long2LongOpenHashMap inFlight = new Long2LongOpenHashMap();

    private final AtomicInteger skipped = new AtomicInteger();
    private int ok = 0;
    private int errors = 0;
    private int sinceSave = 0;

    private volatile boolean scannerDone = false;
    private volatile State state = State.RUNNING;
    private final long startNanos = System.nanoTime();
    private long endNanos = 0;

    private final Consumer<PregenTask> onFinish;
    private Thread scanner;

    public PregenTask(ServerLevel level, int centerX, int centerZ, int radius, int concurrency, boolean keepTps,
                      boolean skipExisting, String label, Consumer<PregenTask> onFinish) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.concurrency = Math.max(1, concurrency);
        this.keepTps = keepTps;
        this.currentLimit = keepTps ? Math.max(8, this.concurrency / 4) : this.concurrency;
        this.skipExisting = skipExisting;
        this.label = label;
        this.iterator = new SpiralIterator(radius);
        this.total = iterator.total();
        this.onFinish = onFinish;
    }

    public void start() {
        scanner = new Thread(this::scanLoop, "Polycore-Pregen-Scan");
        scanner.setDaemon(true);
        scanner.start();
        Polycore.LOGGER.info("[Polycore] {}: {} chunks around ({}, {}), {} in parallel",
                label, total, centerX, centerZ, concurrency);
    }

    /** Scanner thread: prepares positions and skips chunks that are already fully generated. */
    private void scanLoop() {
        try {
            ServerChunkCache source = level.getChunkSource();
            while (state == State.RUNNING && iterator.hasNext()) {
                if (ready.size() >= concurrency * 2) {
                    Thread.sleep(2);
                    continue;
                }
                long rel = iterator.next();
                int x = centerX + SpiralIterator.unpackX(rel);
                int z = centerZ + SpiralIterator.unpackZ(rel);
                if (skipExisting && isFullyGenerated(source, new ChunkPos(x, z))) {
                    skipped.incrementAndGet();
                    continue;
                }
                ready.add(ChunkPos.asLong(x, z));
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Polycore.LOGGER.error("[Polycore] Error while scanning for pre-generation", t);
        } finally {
            scannerDone = true;
        }
    }

    private static boolean isFullyGenerated(ServerChunkCache source, ChunkPos pos) {
        try {
            CollectFields fields = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
            source.chunkMap.chunkScanner().scanChunk(pos, fields).join();
            if (fields.getResult() instanceof CompoundTag tag) {
                return tag.getStringOr("Status", "").equals("minecraft:full");
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Called at the end of every server tick. */
    public void tick() {
        if (state != State.RUNNING) return;
        ServerChunkCache source = level.getChunkSource();
        long now = System.nanoTime();
        long timeout = PolyConfig.get().pregenTimeoutSeconds * 1_000_000_000L;

        // 1) Finished or timed-out chunks: remove their ticket.
        ObjectIterator<Long2LongMap.Entry> it = inFlight.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            long pos = e.getLongKey();
            int x = ChunkPos.getX(pos), z = ChunkPos.getZ(pos);
            boolean done = source.getChunkNow(x, z) != null;
            if (done || now - e.getLongValue() > timeout) {
                ChunkPos cp = new ChunkPos(x, z);
                source.removeTicketWithRadius(TICKET, cp, 0);
                it.remove();
                if (done) {
                    ok++;
                    sinceSave++;
                } else {
                    errors++;
                    Polycore.LOGGER.warn("[Polycore] Chunk ({}, {}) not finished after the timeout, skipped", x, z);
                }
            }
        }

        // 2) Adaptive mode: adjust the limit once per second from the server tick time.
        if (keepTps && ++adjustTicks >= 20) {
            adjustTicks = 0;
            double target = PolyConfig.get().pregenTargetMspt;
            double mspt = ServerMetrics.mspt();
            if (mspt > target) {
                currentLimit = Math.max(4, (int) (currentLimit * 0.75));
            } else if (mspt < target * 0.7) {
                currentLimit = Math.min(concurrency, currentLimit + Math.max(4, concurrency / 10));
            }
        }

        // 3) Fill up to the current limit.
        while (inFlight.size() < currentLimit) {
            Long pos = ready.poll();
            if (pos == null) break;
            ChunkPos cp = new ChunkPos(pos);
            source.addTicketWithRadius(TICKET, cp, 0);
            inFlight.put(pos.longValue(), now);
        }

        // 4) Periodic save so a crash does not lose everything.
        int saveEvery = PolyConfig.get().pregenSaveEvery;
        if (saveEvery > 0 && sinceSave >= saveEvery) {
            sinceSave = 0;
            source.save(false);
        }

        // 5) Done?
        if (scannerDone && ready.isEmpty() && inFlight.isEmpty()) {
            finish(State.DONE);
        }
    }

    public void stop() {
        if (state != State.RUNNING) return;
        finish(State.STOPPED);
    }

    private void finish(State end) {
        state = end;
        endNanos = System.nanoTime();
        ServerChunkCache source = level.getChunkSource();
        for (long pos : inFlight.keySet().toLongArray()) {
            ChunkPos cp = new ChunkPos(pos);
            source.removeTicketWithRadius(TICKET, cp, 0);
        }
        inFlight.clear();
        ready.clear();
        if (scanner != null) scanner.interrupt();
        Polycore.LOGGER.info("[Polycore] {} {} : {}", label, end == State.DONE ? "finished" : "stopped", progressLine());
        if (onFinish != null) onFinish.accept(this);
    }

    public State state() {
        return state;
    }

    public long total() {
        return total;
    }

    public int ok() {
        return ok;
    }

    public int errors() {
        return errors;
    }

    public int skipped() {
        return skipped.get();
    }

    public int currentLimit() {
        return currentLimit;
    }

    public int inFlight() {
        return inFlight.size();
    }

    public double elapsedSeconds() {
        long end = endNanos == 0 ? System.nanoTime() : endNanos;
        return (end - startNanos) / 1e9;
    }

    /** Generated chunks per second since the start. */
    public double rate() {
        double s = elapsedSeconds();
        return s <= 0 ? 0 : ok / s;
    }

    public double progressFraction() {
        return total == 0 ? 1 : (ok + errors + skipped.get()) / (double) total;
    }

    public String progressLine() {
        long doneCount = ok + errors + skipped.get();
        double rate = rate();
        long remaining = total - doneCount;
        String eta = rate > 0 ? formatSeconds(remaining / rate) : "?";
        return String.format("%d/%d (%.1f%%) - %.1f chunks/s - %d in flight - %d skipped - %d errors - ~%s left",
                doneCount, total, progressFraction() * 100, rate, inFlight.size(), skipped.get(), errors, eta);
    }

    public static String formatSeconds(double s) {
        long t = Math.round(s);
        if (t < 60) return t + "s";
        if (t < 3600) return (t / 60) + "min " + (t % 60) + "s";
        return (t / 3600) + "h " + ((t % 3600) / 60) + "min";
    }
}
