package com.polycore.util;

import com.polycore.platform.Services;

/**
 * Detection of mods that replace the chunk system. Usable very early (mixin plugin).
 */
public final class Compat {
    private static Boolean moonrise;
    private static Boolean c2me;

    private Compat() {}

    private static boolean isLoaded(String modId) {
        try {
            return Services.PLATFORM.isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Moonrise replaces the whole chunk scheduler (and already redirects the same calls as Polycore). */
    public static boolean moonrise() {
        if (moonrise == null) moonrise = isLoaded("moonrise");
        return moonrise;
    }

    /** C2ME already parallelizes generation its own way. */
    public static boolean c2me() {
        if (c2me == null) c2me = isLoaded("c2me");
        return c2me;
    }

    /** Why the generation optimizations are off, or null if they can run. */
    public static String worldgenPoolBlockReason() {
        if (moonrise()) return "Moonrise detected (it already manages its own threads)";
        if (c2me()) return "C2ME detected (it already manages its own threads)";
        return null;
    }
}
