package com.polycore.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polycore.Polycore;
import com.polycore.platform.Services;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client preferences, stored in config/polycore-client.json. Always editable by the player (no operator needed).
 */
public final class ClientConfig {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public static final class Data {
        public boolean hudEnabled = false;
        public Corner hudCorner = Corner.TOP_LEFT;
        public boolean toastsEnabled = true;
        /** How often the menu and HUD refresh their data, in ticks (20 = once per second). */
        public int refreshTicks = 10;
        public String lastPage = "server.dashboard";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();

    private ClientConfig() {}

    public static Data get() {
        return data;
    }

    private static Path file() {
        return Services.PLATFORM.configDir().resolve("polycore-client.json");
    }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) data = d;
                }
            }
        } catch (Exception e) {
            Polycore.LOGGER.warn("[Polycore] Could not read {}, using defaults", f, e);
        }
        if (data.hudCorner == null) data.hudCorner = Corner.TOP_LEFT;
        data.refreshTicks = Math.max(5, Math.min(100, data.refreshTicks));
        if (data.lastPage == null) data.lastPage = "server.dashboard";
        save();
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Polycore.LOGGER.warn("[Polycore] Could not save the client config", e);
        }
    }
}
