package com.polycore.pregen;

import com.polycore.config.PolyConfig;
import com.polycore.gen.WorldgenHooks;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Fingerprints of generated terrain, to check that an optimization does not change the world.
 *
 * <p>Two fingerprints per chunk:</p>
 * <ul>
 *   <li><b>terrain</b>: recorded by the generation hooks the moment a chunk's carvers stage finishes (relief, caves,
 *   aquifers), before any decoration. Decorations (trees, lakes...) spill into neighbouring chunks, so the finished
 *   chunk depends on the order chunks were generated in; the terrain does not. Same seed = same terrain fingerprint,
 *   whatever the order, so this is the one that proves parity.</li>
 *   <li><b>full</b>: every block of the finished chunk, for information.</li>
 * </ul>
 */
public final class ParityHash {
    private static final int MAX_RECORDED_PER_DIMENSION = 100_000;
    private static final long NONE = Long.MIN_VALUE;

    /** Dimension -> chunk position -> terrain fingerprint, newest last (oldest evicted). */
    private static final Map<ResourceKey<Level>, Long2LongLinkedOpenHashMap> TERRAIN = new HashMap<>();
    /** Block states are canonical instances: identity equality is what we want. */
    private static final ConcurrentHashMap<BlockState, Long> STATE_HASH = new ConcurrentHashMap<>();

    private ParityHash() {}

    public record Result(String terrain, int terrainChunks, String full, int chunks, Path file) {}

    // ------------------------------------------------------------------ recording (generation threads)

    public static boolean recording() {
        // Not during benchmarks: the hash would add to the stage being timed.
        return PolyConfig.get().parityRecordTerrain && WorldgenHooks.benchOverride == null;
    }

    /** Called when a chunk's carvers stage has just finished, from the thread that ran it. Never throws. */
    public static void recordTerrain(ServerLevel level, ChunkAccess chunk) {
        try {
            long h = hashChunk(chunk);
            synchronized (TERRAIN) {
                Long2LongLinkedOpenHashMap map = TERRAIN.computeIfAbsent(level.dimension(), k -> {
                    Long2LongLinkedOpenHashMap m = new Long2LongLinkedOpenHashMap();
                    m.defaultReturnValue(NONE);
                    return m;
                });
                map.put(chunk.getPos().toLong(), h);
                while (map.size() > MAX_RECORDED_PER_DIMENSION) map.removeFirstLong();
            }
        } catch (Throwable ignored) {
            // A fingerprint is only a diagnostic: never let it break generation.
        }
    }

    /** Recorded terrain fingerprint of a chunk, or null if it was not generated while recording. */
    public static Long terrain(ResourceKey<Level> dimension, long chunkPos) {
        synchronized (TERRAIN) {
            Long2LongLinkedOpenHashMap map = TERRAIN.get(dimension);
            if (map == null) return null;
            long v = map.get(chunkPos);
            return v == NONE ? null : v;
        }
    }

    public static void clear() {
        synchronized (TERRAIN) {
            TERRAIN.clear();
        }
    }

    // ------------------------------------------------------------------ report (server thread)

    public static Result compute(ServerLevel level, int centerX, int centerZ, int radius) {
        List<String> lines = new ArrayList<>();
        long full = 0xcbf29ce484222325L, terrain = 0xcbf29ce484222325L;
        int count = 0, recorded = 0;
        lines.add("# Polycore - generation fingerprint");
        lines.add("# seed=" + level.getSeed() + " dimension=" + level.dimension().location()
                + " center=" + centerX + "," + centerZ + " radius=" + radius);
        lines.add("# terrain = after the carvers stage, independent of generation order: this one proves parity.");
        lines.add("# full = the finished chunk; decorations depend on the order chunks were generated in.");
        lines.add("# chunk terrain full");
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                ChunkAccess chunk = level.getChunk(x, z);
                long h = hashChunk(chunk);
                Long t = terrain(level.dimension(), ChunkPos.asLong(x, z));
                lines.add(x + "," + z + " " + (t == null ? "-" : Long.toHexString(t)) + " " + Long.toHexString(h));
                full = mix(full, h);
                if (t != null) {
                    terrain = mix(terrain, t);
                    recorded++;
                }
                count++;
            }
        }
        String fullHex = String.format("%016x", full);
        String terrainHex = recorded == count ? String.format("%016x", terrain) : recorded == 0 ? "none" : "partial";
        lines.add(1, "# global=" + fullHex);
        lines.add(2, "# terrain=" + terrainHex + " (" + recorded + " of " + count + " chunks recorded)");
        if (recorded < count) {
            lines.add(3, "# Chunks without a terrain fingerprint were generated before recording was on (setting "
                    + "parity.recordTerrain), by an older Polycore, or during a benchmark.");
        }
        String name = "hash-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + centerX + "_" + centerZ + "-r" + radius + ".txt";
        Path file = Reports.write(name, lines);
        return new Result(terrainHex, recorded, fullHex, count, file);
    }

    // ------------------------------------------------------------------ hashing

    static long hashChunk(ChunkAccess chunk) {
        long h = 0xcbf29ce484222325L;
        LevelChunkSection[] sections = chunk.getSections();
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection section = sections[s];
            if (section == null || section.hasOnlyAir()) {
                h = mix(h, 0x9E3779B97F4A7C15L + s);
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        h = mix(h, stateHash(section.getBlockState(x, y, z)));
                    }
                }
            }
        }
        return h;
    }

    /** Stable across launches and mod lists: based on the state's text, not on an internal numeric id. */
    private static long stateHash(BlockState st) {
        Long v = STATE_HASH.get(st);
        if (v == null) {
            v = fnv(st.toString());
            STATE_HASH.put(st, v);
        }
        return v;
    }

    private static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001b3L;
        h ^= (h >>> 29);
        return h;
    }
}
