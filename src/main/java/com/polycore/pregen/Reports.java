package com.polycore.pregen;

import com.polycore.Polycore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import com.polycore.platform.Services;

/**
 * Benchmark and hash reports saved in the polycore-reports folder of the game/server directory.
 */
public final class Reports {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,120}\\.txt");
    private static final int MAX_LINES = 400;

    // The list is requested every second by open menus: cache it for a few seconds.
    private static ListTag cachedList = null;
    private static long cachedAt = 0;
    private static int cachedMax = 0;

    private Reports() {}

    public static Path dir() {
        return Services.PLATFORM.gameDir().resolve("polycore-reports");
    }

    public static Path write(String fileName, List<String> lines) {
        try {
            Path d = dir();
            Files.createDirectories(d);
            Path file = d.resolve(fileName);
            Files.write(file, lines, StandardCharsets.UTF_8);
            cachedList = null;
            return file;
        } catch (IOException e) {
            Polycore.LOGGER.error("[Polycore] Could not write report {}", fileName, e);
            return null;
        }
    }

    /** Most recent reports first: name + one-line summary. */
    public static synchronized ListTag list(int max) {
        long now = System.currentTimeMillis();
        if (cachedList != null && cachedMax == max && now - cachedAt < 5000) return cachedList.copy();
        cachedList = scan(max);
        cachedAt = now;
        cachedMax = max;
        return cachedList.copy();
    }

    private static ListTag scan(int max) {
        ListTag out = new ListTag();
        Path d = dir();
        if (!Files.isDirectory(d)) return out;
        try (Stream<Path> files = Files.list(d)) {
            List<Path> sorted = files
                    .filter(p -> SAFE_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .limit(max)
                    .toList();
            for (Path p : sorted) {
                CompoundTag t = new CompoundTag();
                String name = p.getFileName().toString();
                t.putString("name", name);
                t.putString("kind", name.startsWith("bench-") ? "Benchmark" : name.startsWith("hash-") ? "Parity hash"
                        : name.startsWith("lag-") ? "Lag analysis" : "Report");
                t.putString("summary", summary(p));
                out.add(t);
            }
        } catch (IOException e) {
            Polycore.LOGGER.warn("[Polycore] Could not list reports", e);
        }
        return out;
    }

    private static String summary(Path p) {
        try (Stream<String> lines = Files.lines(p, StandardCharsets.UTF_8)) {
            List<String> all = lines.limit(200).toList();
            for (String l : all) if (l.startsWith("RESULT")) return l;
            String full = null, terrain = null;
            for (String l : all) {
                if (l.startsWith("# global=")) full = l.substring(9);
                if (l.startsWith("# terrain=")) terrain = l.substring(10);
            }
            if (terrain != null) return "Terrain " + terrain + ", full " + full;
            if (full != null) return "Fingerprint " + full;
            return all.isEmpty() ? "" : all.get(0);
        } catch (Exception e) {
            return "";
        }
    }

    /** Reads a report by file name. Only plain names from the reports folder are accepted. */
    public static CompoundTag read(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        ListTag lines = new ListTag();
        if (!SAFE_NAME.matcher(name).matches()) {
            lines.add(StringTag.valueOf("Invalid report name."));
        } else {
            Path p = dir().resolve(name);
            try {
                List<String> all = new ArrayList<>(Files.readAllLines(p, StandardCharsets.UTF_8));
                if (all.size() > MAX_LINES) {
                    int more = all.size() - MAX_LINES;
                    all = new ArrayList<>(all.subList(0, MAX_LINES));
                    all.add("... (" + more + " more lines in the file)");
                }
                for (String l : all) lines.add(StringTag.valueOf(l));
            } catch (IOException e) {
                lines.add(StringTag.valueOf("Could not read the report: " + e.getMessage()));
            }
        }
        t.put("lines", lines);
        return t;
    }
}
