package com.polycore.client;

import com.mojang.brigadier.CommandDispatcher;
import com.polycore.client.ui.PolyScreen;
import com.polycore.network.ClientBridge;
import com.polycore.network.PolyNetwork;
import com.polycore.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Loader-independent client logic. The loader glue (see neoforge/client) registers the key, the HUD layer and
 * the client command, then forwards ticks here.
 */
public final class PolycoreClient {
    private static String pendingPage = null;
    private static int ticks = 0;

    private PolycoreClient() {}

    public static void init() {
        ClientConfig.load();
        ClientBridge.openMenuHook = page -> pendingPage = page;
        ClientBridge.notifyHook = n -> {
            if (ClientConfig.get().toastsEnabled) {
                Minecraft.getInstance().getToasts().addToast(new PolyToast(n.level(), n.title(), n.message()));
            }
        };
    }

    /** Opens the menu on the next tick (safe to call from a command or a packet). */
    public static void requestOpen(String page) {
        pendingPage = page;
    }

    public static boolean serverHasPolycore() {
        try {
            return Minecraft.getInstance().getConnection() != null
                    && Services.PLATFORM.serverHasChannel(PolyNetwork.RequestState.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The "open menu" key, set by the loader glue; the menu closes on it too. */
    public static volatile net.minecraft.client.KeyMapping openKey = null;

    /** The menu needs everything; the HUD alone only asks for its few values. */
    public static void requestState() {
        boolean menu = Minecraft.getInstance().screen instanceof PolyScreen;
        if (serverHasPolycore()) Services.PLATFORM.sendToServer(new PolyNetwork.RequestState(menu));
    }

    public static void sendAction(String action, String arg, String value) {
        if (serverHasPolycore()) Services.PLATFORM.sendToServer(new PolyNetwork.Action(action, arg, value));
    }

    /**
     * Called at the end of every client tick.
     * @param openKeyPressed whether the "open menu" key was pressed this tick
     */
    public static void onClientTick(boolean openKeyPressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (openKeyPressed && mc.screen == null) {
            pendingPage = ClientConfig.get().lastPage;
        }
        if (pendingPage != null && (mc.screen == null || mc.screen instanceof PolyScreen)) {
            String page = pendingPage;
            pendingPage = null;
            mc.setScreen(new PolyScreen(page));
            requestState();
        }

        // Refresh the data while the menu or the HUD needs it.
        boolean needed = mc.screen instanceof PolyScreen || ClientConfig.get().hudEnabled;
        if (needed && ++ticks >= ClientConfig.get().refreshTicks) {
            ticks = 0;
            requestState();
        }
    }

    public static void onLogout() {
        ClientBridge.clear();
        pendingPage = null;
    }

    /**
     * Client-side "/polycore" and "/pc": open the menu like the K key. Commands with arguments
     * (e.g. "/polycore pregen start 32") are not handled here and go to the server.
     */
    public static void registerClientCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("polycore").executes(c -> {
            requestOpen(ClientConfig.get().lastPage);
            return 1;
        }));
        dispatcher.register(Commands.literal("pc").executes(c -> {
            requestOpen(ClientConfig.get().lastPage);
            return 1;
        }));
    }
}
