package com.polycore.selftest;

import com.polycore.Polycore;
import com.polycore.client.ClientConfig;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import com.polycore.client.ui.PolyScreen;
import com.polycore.network.ClientBridge;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.CompoundTag;

/**
 * Production client self-test, inactive unless the game runs with {@code -Dpolycore.selftest.client=true}.
 * At the title screen: checks the menu key, opens the Polycore menu and screenshots it to
 * {@code polycore.selftest.shot}. Then joins each server of {@code polycore.selftest.servers}
 * ({@code polycore@host:port;vanilla@host:port}), checks it stays connected, the HUD layer renders and the menu
 * opens with the right server section, and quits. Every result is a log line starting with {@code [Polycore selftest]}.
 * With {@code -Dpolycore.selftest.gallery=<dir>}, on the Polycore server it also runs a small benchmark (operator
 * only), then captures every menu page and the in-game HUD into that directory ({@code menu-<page>.png},
 * {@code hud-in-game.png}).
 */
public final class ClientSelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("polycore.selftest.client");
    private static final String SHOT_NAME = "polycore-selftest.png";
    private static final String GALLERY = System.getProperty("polycore.selftest.gallery");

    private record Target(boolean polycore, String address) {}

    private static int step = 0;
    private static int wait = 0;
    private static int server = 0;
    private static int hudBefore = 0;
    private static int shot = 0;
    private static volatile boolean written = false;
    private static boolean ok = true;
    private static final List<Target> TARGETS = new ArrayList<>();

    private ClientSelfTest() {}

    private static void log(boolean pass, String what) {
        if (!pass) ok = false;
        Polycore.LOGGER.info("[Polycore selftest] {} {}", pass ? "PASS" : "FAIL", what);
    }

    /** Called every client tick, in and out of a world. */
    public static void tick() {
        if (!ENABLED || step < 0) return;
        Minecraft mc = Minecraft.getInstance();
        wait++;
        if (mc.screen instanceof DisconnectedScreen d && step >= 10) {
            // The reason is a child widget of the screen (title, reason, button): log them all.
            StringBuilder why = new StringBuilder(d.getTitle().getString());
            for (var c : d.children())
                if (c instanceof net.minecraft.client.gui.components.AbstractWidget w) why.append(" | ").append(w.getMessage().getString());
            log(false, "disconnected from " + TARGETS.get(server).address + ": " + why);
            finish(mc);
            return;
        }
        switch (step) {
            case 0 -> { // the title screen, after the resource reload
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || wait < 40) return;
                boolean key = PolycoreClient.openKey != null
                        && Arrays.asList(mc.options.keyMappings).contains(PolycoreClient.openKey);
                log(key, "menu key registered: "
                        + (key ? PolycoreClient.openKey.getTranslatedKeyMessage().getString() : "missing"));
                for (String t : System.getProperty("polycore.selftest.servers", "").split(";")) {
                    if (t.contains("@")) TARGETS.add(new Target(t.startsWith("polycore@"), t.substring(t.indexOf('@') + 1)));
                }
                mc.setScreen(new PolyScreen(null));
                next(1);
            }
            case 1 -> { // let the menu render, then capture it
                if (wait < 40 || mc.getOverlay() != null) return;
                log(mc.screen instanceof PolyScreen, "Polycore menu open at the title screen");
                grab(mc);
                next(2);
            }
            case 2 -> {
                if (!saved(mc, System.getProperty("polycore.selftest.shot"), "menu screenshot")) return;
                mc.setScreen(new TitleScreen());
                next(TARGETS.isEmpty() ? 99 : 10);
            }
            case 10 -> { // join the next server, 3 s after leaving the previous one (Forge 1.20.1 restores its registries meanwhile)
                if (wait < 60) return;
                Target t = TARGETS.get(server);
                Polycore.LOGGER.info("[Polycore selftest] joining {} server {}", t.polycore ? "Polycore" : "vanilla", t.address);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(t.address),
                        new ServerData("selftest", t.address, false));
                next(11);
            }
            case 11 -> { // in the world
                if (mc.player == null || mc.getConnection() == null) {
                    if (wait > 1800) { log(false, "could not join " + TARGETS.get(server).address); finish(mc); }
                    return;
                }
                hudBefore = HudOverlay.renderCalls;
                next(12);
            }
            case 12 -> { // stay 5 s, then check the server section and open the menu
                if (wait < 100) return;
                Target t = TARGETS.get(server);
                log(true, "still connected to " + t.address + " after 5 s");
                log(HudOverlay.renderCalls > hudBefore, "HUD layer rendered in the world");
                boolean has = PolycoreClient.serverHasPolycore();
                log(has == t.polycore, "server section: " + (has ? "server has Polycore" : "server does not have Polycore"));
                if (t.polycore && GALLERY != null) {
                    boolean op = mc.player.hasPermissions(2);
                    log(op, "gallery: player is an operator");
                    if (op) mc.player.connection.sendCommand("polycore bench 3");
                    mc.setScreen(new PolyScreen("server.tools")); // the server only streams its state to an open menu
                    next(20);
                    return;
                }
                PolycoreClient.requestOpen(ClientConfig.get().lastPage);
                next(13);
            }
            case 20 -> { // gallery: wait for the benchmark (at most 3 min) on the Tools page, it fills the dashboard and the reports
                CompoundTag st = ClientBridge.state();
                boolean running = st == null || st.getBoolean("benchRunning");
                if ((running || wait < 100) && wait < 3600) return;
                log(!running, "gallery: benchmark finished");
                shot = 0;
                next(21);
            }
            case 21 -> { // gallery: open the next page, or show the HUD alone after the last one
                List<String> pages = PolyScreen.pageIds();
                if (shot < pages.size()) {
                    mc.setScreen(new PolyScreen(pages.get(shot)));
                } else {
                    ClientConfig.get().hudEnabled = true;
                    mc.setScreen(null);
                }
                next(22);
            }
            case 22 -> {
                if (wait < 60) return;
                grab(mc);
                next(23);
            }
            case 23 -> {
                List<String> pages = PolyScreen.pageIds();
                String name = shot < pages.size() ? "menu-" + pages.get(shot).replace('.', '-') + ".png" : "hud-in-game.png";
                if (!saved(mc, Path.of(GALLERY, name).toString(), "gallery " + name)) return;
                if (++shot <= pages.size()) {
                    next(21);
                } else {
                    ClientConfig.get().hudEnabled = false;
                    PolycoreClient.requestOpen(ClientConfig.get().lastPage);
                    next(13);
                }
            }
            case 13 -> {
                if (wait < 40) return;
                log(mc.screen instanceof PolyScreen, "Polycore menu open in the world");
                mc.setScreen(null);
                if (mc.level != null) mc.level.disconnect();
                mc.clearLevel(new TitleScreen());
                server++;
                next(server < TARGETS.size() ? 10 : 99);
            }
            default -> finish(mc);
        }
    }

    private static void grab(Minecraft mc) {
        new File(mc.gameDirectory, "screenshots/" + SHOT_NAME).delete();
        written = false;
        Screenshot.grab(mc.gameDirectory, SHOT_NAME, mc.getMainRenderTarget(), m -> written = true);
    }

    /** False while the screenshot is still being written (IO thread), then copies it to {@code dest} if set. */
    private static boolean saved(Minecraft mc, String dest, String what) {
        Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve(SHOT_NAME);
        if (!written && wait < 200) return false;
        try {
            if (dest != null) Files.copy(file, Path.of(dest), StandardCopyOption.REPLACE_EXISTING);
            log(true, what + " written to " + (dest != null ? dest : file));
        } catch (Exception e) {
            log(false, what + ": " + e);
        }
        return true;
    }

    private static void next(int s) {
        step = s;
        wait = 0;
    }

    private static void finish(Minecraft mc) {
        step = -1;
        Polycore.LOGGER.info("[Polycore selftest] CLIENT SELFTEST {}", ok ? "PASSED" : "FAILED");
        mc.stop();
    }
}
