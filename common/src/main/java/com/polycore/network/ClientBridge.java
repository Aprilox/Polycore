package com.polycore.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/**
 * Data received from the server, plus hooks the client installs at startup.
 * Contains no client-only classes, so it is safe to load on a dedicated server.
 */
public final class ClientBridge {
    public record Notification(String level, String title, String message, long time) {}

    private static volatile CompoundTag state = null;
    /** Latest values for the HUD: the last full state or the last light one, whichever is newer. */
    private static volatile CompoundTag hud = null;
    private static volatile long stateReceivedAt = 0;
    private static volatile CompoundTag report = null;
    private static final Deque<Notification> ACTIVITY = new ArrayDeque<>();

    /** Installed by the client: opens the menu on a page. */
    public static volatile Consumer<String> openMenuHook = page -> {};
    /** Installed by the client: shows a toast. */
    public static volatile Consumer<Notification> notifyHook = n -> {};

    private ClientBridge() {}

    static void onState(CompoundTag tag) {
        hud = tag;
        if (tag.getBooleanOr("hudOnly", false)) return; // a light state must not hide the menu's data
        state = tag;
        stateReceivedAt = System.currentTimeMillis();
    }

    public static CompoundTag hudState() {
        return hud;
    }

    static void onReport(CompoundTag tag) {
        report = tag;
    }

    static void onNotify(String level, String title, String message) {
        Notification n = new Notification(level, title, message, System.currentTimeMillis());
        synchronized (ACTIVITY) {
            ACTIVITY.addFirst(n);
            while (ACTIVITY.size() > 30) ACTIVITY.removeLast();
        }
        notifyHook.accept(n);
    }

    static void onOpenMenu(String page) {
        openMenuHook.accept(page);
    }

    public static CompoundTag state() {
        return state;
    }

    public static long stateAgeMillis() {
        return stateReceivedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - stateReceivedAt;
    }

    public static CompoundTag report() {
        return report;
    }

    public static List<Notification> activity() {
        synchronized (ACTIVITY) {
            return new ArrayList<>(ACTIVITY);
        }
    }

    /** Local notification (e.g. "this server does not have Polycore"). */
    public static void localNotify(String level, String title, String message) {
        onNotify(level, title, message);
    }

    public static void clear() {
        state = null;
        hud = null;
        stateReceivedAt = 0;
        report = null;
    }
}
