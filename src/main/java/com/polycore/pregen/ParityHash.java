package com.polycore.pregen;

import com.polycore.PolyCore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Empreinte (hash) des blocs d'une zone, pour verifier qu'une optimisation ne change pas le monde genere.
 * Usage : meme seed, meme zone, une fois sans le mod (ou optimisations coupees) et une fois avec.
 * Les deux empreintes doivent etre identiques.
 */
public final class ParityHash {
    private ParityHash() {}

    public record Result(String hash, int chunks, Path file) {}

    public static Result compute(ServerLevel level, int centerX, int centerZ, int radius) {
        Map<BlockState, Long> stateHash = new IdentityHashMap<>();
        List<String> lines = new ArrayList<>();
        long global = 0xcbf29ce484222325L;
        int count = 0;
        lines.add("# Polycore - generation fingerprint");
        lines.add("# seed=" + level.getSeed() + " dimension=" + level.dimension().location()
                + " center=" + centerX + "," + centerZ + " radius=" + radius);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                long h = hashChunk(chunk, stateHash);
                lines.add(x + "," + z + " " + Long.toHexString(h));
                global = mix(global, h);
                count++;
            }
        }
        String hex = String.format("%016x", global);
        lines.add(1, "# global=" + hex);
        Path file = null;
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("polycore-reports");
            Files.createDirectories(dir);
            file = dir.resolve("hash-" + level.getSeed() + "-" + centerX + "_" + centerZ + "-r" + radius + "-"
                    + System.currentTimeMillis() + ".txt");
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            PolyCore.LOGGER.error("[Polycore] Could not write the hash file", e);
        }
        return new Result(hex, count, file);
    }

    private static long hashChunk(LevelChunk chunk, Map<BlockState, Long> cache) {
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
                        BlockState st = section.getBlockState(x, y, z);
                        // Hash stable entre deux lancements : base sur le texte de l'etat, pas sur un identifiant interne.
                        Long v = cache.get(st);
                        if (v == null) {
                            v = fnv(st.toString());
                            cache.put(st, v);
                        }
                        h = mix(h, v);
                    }
                }
            }
        }
        return h;
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
