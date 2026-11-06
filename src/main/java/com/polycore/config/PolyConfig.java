package com.polycore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polycore.Polycore;
import com.polycore.platform.Services;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Server-side configuration, stored in config/polycore.json.
 *
 * <p>Loader-independent on purpose (plain JSON through Gson, which ships with Minecraft), so the same code
 * works on NeoForge, Fabric and Forge. Edited live from the menu or /polycore set; /polycore reload re-reads
 * the file after a manual edit.</p>
 */
public final class PolyConfig {
    /** All values with their defaults. Field names are the JSON keys. */
    public static final class Data {
        public boolean worldgenPoolEnabled = true;
        /** 0 = automatic (cores - 2). Always capped to the machine's logical cores. */
        public int worldgenPoolThreads = 0;
        public int worldgenPoolPriority = 5;

        public boolean parallelStagesEnabled = true;
        public boolean profilerEnabled = true;

        /** 0 = automatic (cores x 8, between 32 and 512). */
        public int pregenConcurrency = 0;
        public boolean pregenKeepTps = true;
        public int pregenTargetMspt = 40;
        public int pregenSaveEvery = 2000;
        public int pregenTimeoutSeconds = 120;

        public int benchDefaultRadius = 10;
        public int benchBaselineConcurrency = 32;

        public boolean allowNonOpView = true;

        /** A tick longer than this is a lag spike; a 5 s average above it triggers alerts and auto analysis. */
        public int lagThresholdMs = 50;
        public boolean lagAutoCapture = true;
        public int lagCaptureSeconds = 30;
        /** UUIDs of the operators who asked for lag alerts. */
        public java.util.List<String> lagAlertPlayers = new java.util.ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Data data = new Data();

    private PolyConfig() {}

    public static Data get() {
        return data;
    }

    public static Path file() {
        return Services.PLATFORM.configDir().resolve("polycore.json");
    }

    /** Loads the file (creating it with defaults if missing). Never throws. */
    public static synchronized void load() {
        Path f = file();
        Data loaded = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, Data.class);
            } catch (Exception e) {
                Polycore.LOGGER.error("[Polycore] Could not read {}, using defaults", f, e);
            }
        }
        data = loaded != null ? loaded : new Data();
        sanitize(data);
        save();
    }

    /** Writes the file atomically. */
    public static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("polycore.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.writeString(f, GSON.toJson(data), StandardCharsets.UTF_8);
            } catch (IOException e2) {
                Polycore.LOGGER.error("[Polycore] Could not save {}", f, e2);
            }
        }
    }

    private static void sanitize(Data d) {
        int cores = cores();
        d.worldgenPoolThreads = clamp(d.worldgenPoolThreads, 0, cores);
        d.worldgenPoolPriority = clamp(d.worldgenPoolPriority, 1, 10);
        d.pregenConcurrency = clamp(d.pregenConcurrency, 0, 4096);
        d.pregenTargetMspt = clamp(d.pregenTargetMspt, 10, 50);
        d.pregenSaveEvery = clamp(d.pregenSaveEvery, 0, 1_000_000);
        d.pregenTimeoutSeconds = clamp(d.pregenTimeoutSeconds, 10, 3600);
        d.benchDefaultRadius = clamp(d.benchDefaultRadius, 2, 64);
        d.benchBaselineConcurrency = clamp(d.benchBaselineConcurrency, 1, 4096);
        d.lagThresholdMs = clamp(d.lagThresholdMs, 20, 1000);
        d.lagCaptureSeconds = clamp(d.lagCaptureSeconds, 5, 300);
        if (d.lagAlertPlayers == null) d.lagAlertPlayers = new java.util.ArrayList<>();
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static int autoPoolThreads() {
        return Math.max(1, cores() - 2);
    }

    /** Pool size actually used: never more threads than this machine has logical cores. */
    public static int poolThreads() {
        int v = data.worldgenPoolThreads;
        return v <= 0 ? autoPoolThreads() : clamp(v, 1, cores());
    }

    public static int autoPregenConcurrency() {
        return clamp(cores() * 8, 32, 512);
    }

    public static int pregenConcurrency() {
        int v = data.pregenConcurrency;
        return v > 0 ? v : autoPregenConcurrency();
    }
}
