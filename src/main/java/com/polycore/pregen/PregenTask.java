package com.polycore.pregen;

import com.polycore.PolyConfig;
import com.polycore.PolyCore;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Comparator;
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
 * Pre-generation parallele.
 *
 * Principe : on pose un ticket de chargement (niveau FULL) sur beaucoup de chunks a la fois.
 * Minecraft genere alors ces chunks avec tous ses threads. Chaque tick, on regarde lesquels sont
 * termines, on retire leur ticket (ils seront sauvegardes puis decharges) et on en demande d'autres.
 *
 * Tout ce qui touche aux tickets se fait sur le thread serveur (tick()).
 * Cette methode fonctionne avec le systeme de chunks vanilla comme avec Moonrise.
 */
public final class PregenTask {
    public static final TicketType<ChunkPos> TICKET =
            TicketType.create("polycore_pregen", Comparator.comparingLong(ChunkPos::toLong));

    public enum State { RUNNING, DONE, STOPPED }

    public final ServerLevel level;
    public final int centerX;
    public final int centerZ;
    public final int radius;
    public final int concurrency;
    public final String label;
    private final boolean skipExisting;

    private final SpiralIterator iterator;
    private final long total;

    /** Positions pretes a etre demandees (remplie par le thread de scan). */
    private final ConcurrentLinkedQueue<Long> ready = new ConcurrentLinkedQueue<>();
    /** Chunks demandes -> instant de la demande (nanos). Thread serveur uniquement. */
    private final Long2LongOpenHashMap inFlight = new Long2LongOpenHashMap();

    private final AtomicInteger skipped = new AtomicInteger();
    private int ok = 0;
    private int errors = 0;
    private int sinceSave = 0;

    private volatile boolean scannerDone = false;
    private volatile State state = State.RUNNING;
    private final long startNanos = System.nanoTime();
    private long endNanos = 0;
    private long lastProgressNanos = System.nanoTime();

    private final Consumer<String> progress;
    private final Consumer<PregenTask> onFinish;
    private Thread scanner;

    public PregenTask(ServerLevel level, int centerX, int centerZ, int radius, int concurrency, boolean skipExisting,
                      String label, Consumer<String> progress, Consumer<PregenTask> onFinish) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.concurrency = Math.max(1, concurrency);
        this.skipExisting = skipExisting;
        this.label = label;
        this.iterator = new SpiralIterator(radius);
        this.total = iterator.total();
        this.progress = progress;
        this.onFinish = onFinish;
    }

    public void start() {
        scanner = new Thread(this::scanLoop, "Polycore-Pregen-Scan");
        scanner.setDaemon(true);
        scanner.start();
        PolyCore.LOGGER.info("[Polycore] {}: {} chunks around ({}, {}), {} in parallel",
                label, total, centerX, centerZ, concurrency);
    }

    /** Thread de scan : prepare les positions et saute les chunks deja generes. */
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
            PolyCore.LOGGER.error("[Polycore] Error while scanning for pre-generation", t);
        } finally {
            scannerDone = true;
        }
    }

    private static boolean isFullyGenerated(ServerChunkCache source, ChunkPos pos) {
        try {
            CollectFields fields = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
            source.chunkMap.chunkScanner().scanChunk(pos, fields).join();
            if (fields.getResult() instanceof CompoundTag tag) {
                return tag.getString("Status").equals("minecraft:full");
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Appele a chaque fin de tick serveur. */
    public void tick() {
        if (state != State.RUNNING) return;
        ServerChunkCache source = level.getChunkSource();
        long now = System.nanoTime();
        long timeout = PolyConfig.safeGet(PolyConfig.PREGEN_TIMEOUT_SECONDS, 120) * 1_000_000_000L;

        // 1) Chunks termines ou expires : on retire le ticket.
        ObjectIterator<Long2LongMap.Entry> it = inFlight.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            long pos = e.getLongKey();
            int x = ChunkPos.getX(pos), z = ChunkPos.getZ(pos);
            boolean done = source.getChunkNow(x, z) != null;
            if (done || now - e.getLongValue() > timeout) {
                ChunkPos cp = new ChunkPos(x, z);
                source.removeRegionTicket(TICKET, cp, 0, cp);
                it.remove();
                if (done) {
                    ok++;
                    sinceSave++;
                } else {
                    errors++;
                    PolyCore.LOGGER.warn("[Polycore] Chunk ({}, {}) not finished after the timeout, skipped", x, z);
                }
            }
        }

        // 2) On remplit jusqu'a la limite de chunks en parallele.
        while (inFlight.size() < concurrency) {
            Long pos = ready.poll();
            if (pos == null) break;
            ChunkPos cp = new ChunkPos(pos);
            source.addRegionTicket(TICKET, cp, 0, cp);
            inFlight.put(pos.longValue(), now);
        }

        // 3) Sauvegarde reguliere pour ne pas tout perdre en cas de crash.
        int saveEvery = PolyConfig.safeGet(PolyConfig.PREGEN_SAVE_EVERY, 2000);
        if (saveEvery > 0 && sinceSave >= saveEvery) {
            sinceSave = 0;
            source.save(false);
        }

        // 4) Progression.
        if (now - lastProgressNanos > 5_000_000_000L) {
            lastProgressNanos = now;
            if (progress != null) progress.accept(progressLine());
        }

        // 5) Fin.
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
            source.removeRegionTicket(TICKET, cp, 0, cp);
        }
        inFlight.clear();
        ready.clear();
        if (scanner != null) scanner.interrupt();
        PolyCore.LOGGER.info("[Polycore] {} {} : {}", label, end == State.DONE ? "finished" : "stopped", progressLine());
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

    public int inFlight() {
        return inFlight.size();
    }

    public double elapsedSeconds() {
        long end = endNanos == 0 ? System.nanoTime() : endNanos;
        return (end - startNanos) / 1e9;
    }

    /** Chunks generes par seconde depuis le debut. */
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
