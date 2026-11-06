package com.polycore.util;

import net.neoforged.fml.loading.LoadingModList;

/**
 * Detection des mods qui remplacent le systeme de chunks.
 * Utilisable tres tot (plugin mixin) : aucune classe Minecraft ici.
 */
public final class Compat {
    private static Boolean moonrise;
    private static Boolean c2me;

    private Compat() {}

    private static boolean isLoaded(String modId) {
        try {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Moonrise remplace tout le planificateur de chunks (et redirige deja les memes appels que nous). */
    public static boolean moonrise() {
        if (moonrise == null) moonrise = isLoaded("moonrise");
        return moonrise;
    }

    /** C2ME parallelise deja la generation a sa facon. */
    public static boolean c2me() {
        if (c2me == null) c2me = isLoaded("c2me");
        return c2me;
    }

    /** Raison pour laquelle le pool de generation est desactive, ou null s'il peut tourner. */
    public static String worldgenPoolBlockReason() {
        if (moonrise()) return "Moonrise detected (it already manages its own threads)";
        if (c2me()) return "C2ME detected (it already manages its own threads)";
        return null;
    }
}
