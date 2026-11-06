package com.polycore.network;

import net.minecraft.nbt.CompoundTag;

/**
 * Derniere copie de l'etat recue du serveur. Aucune classe client ici : peut etre chargee sur un serveur dedie.
 */
public final class ClientStatsCache {
    private static volatile CompoundTag latest = null;
    private static volatile long receivedAt = 0;

    private ClientStatsCache() {}

    public static void set(CompoundTag tag) {
        latest = tag;
        receivedAt = System.currentTimeMillis();
    }

    public static CompoundTag get() {
        return latest;
    }

    public static long ageMillis() {
        return receivedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - receivedAt;
    }

    public static void clear() {
        latest = null;
        receivedAt = 0;
    }
}
