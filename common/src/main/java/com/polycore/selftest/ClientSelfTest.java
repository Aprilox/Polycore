package com.polycore.selftest;

import com.polycore.Polycore;
import com.polycore.client.ClientConfig;
import com.polycore.client.HudOverlay;
import com.polycore.client.PolycoreClient;
import com.polycore.client.ui.PolyScreen;
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

/**
 * Production client self-test, inactive unless the game runs with {@code -Dpolycore.selftest.client=true}.
 * At the title screen: checks the menu key, opens the Polycore menu and screenshots it to
 * {@code polycore.selftest.shot}. Then joins each server of {@code polycore.selftest.servers}
 * ({@code polycore@host:port;vanilla@host:port}), checks it stays connected, the HUD layer renders and the menu
 * opens with the right server section, and quits. Every result is a log line starting with {@code [Polycore selftest]}.
 */
public final class ClientSelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("polycore.selftest.client");
    private static final String SHOT_NAME = "polycore-selftest.png";

    private record Target(boolean polycore, String address) {}

    private static int step = 0;
    private static int wait = 0;
    private static int server = 0;
    private static int hudBefore = 0;
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
            log(false, "disconnected from " + TARGETS.get(server).address + ": " + d.getTitle().getString());
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
                new File(mc.gameDirectory, "screenshots/" + SHOT_NAME).delete();
                Screenshot.grab(mc.gameDirectory, SHOT_NAME, mc.getMainRenderTarget(), m -> {});
                next(2);
            }
            case 2 -> { // Screenshot writes on an IO thread
                Path shot = mc.gameDirectory.toPath().resolve("screenshots").resolve(SHOT_NAME);
                if (!Files.isRegularFile(shot) && wait < 200) return;
                String dest = System.getProperty("polycore.selftest.shot");
                try {
                    if (dest != null) Files.copy(shot, Path.of(dest), StandardCopyOption.REPLACE_EXISTING);
                    log(true, "menu screenshot written to " + (dest != null ? dest : shot));
                } catch (Exception e) {
                    log(false, "menu screenshot: " + e);
                }
                mc.setScreen(new TitleScreen());
                next(TARGETS.isEmpty() ? 99 : 10);
            }
            case 10 -> { // join the next server
                if (wait < 10) return;
                Target t = TARGETS.get(server);
                Polycore.LOGGER.info("[Polycore selftest] joining {} server {}", t.polycore ? "Polycore" : "vanilla", t.address);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(t.address),
                        new ServerData("selftest", t.address, ServerData.Type.OTHER), false, null);
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
                PolycoreClient.requestOpen(ClientConfig.get().lastPage);
                next(13);
            }
            case 13 -> {
                if (wait < 40) return;
                log(mc.screen instanceof PolyScreen, "Polycore menu open in the world");
                mc.setScreen(null);
                if (mc.level != null) mc.level.disconnect();
                mc.disconnect(new TitleScreen());
                server++;
                next(server < TARGETS.size() ? 10 : 99);
            }
            default -> finish(mc);
        }
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
