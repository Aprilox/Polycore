package com.polycore.platform;

import java.nio.file.Path;
import com.polycore.network.PolyNetwork.Payload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything Polycore needs from the mod loader.
 *
 * <p>The rest of the mod only uses vanilla Minecraft classes (Mojang mappings), so supporting another loader
 * (Fabric, Forge...) means writing one implementation of this interface plus the small entry-point classes
 * that forward events to {@link com.polycore.Polycore}. See docs/architecture.md.</p>
 */
public interface Platform {
    /** "NeoForge", "Fabric", "Forge"... */
    String loaderName();

    boolean isModLoaded(String modId);

    /** Game directory (client) or server directory. */
    Path gameDir();

    Path configDir();

    /** Version of Polycore itself. */
    String modVersion();

    /** Version of the Minecraft game. */
    String minecraftVersion();

    void sendToPlayer(ServerPlayer player, Payload payload);

    /** Client side only. */
    void sendToServer(Payload payload);

    /** Whether this player's client has Polycore (has registered the given channel). */
    boolean playerHasChannel(ServerPlayer player, ResourceLocation channel);

    /** Client side only: whether the server we are connected to has Polycore. */
    boolean serverHasChannel(ResourceLocation channel);

    /** Whether the game runs on Mojang names (a development game): util/Conflicts then needs no name table. */
    default boolean mojangNames() {
        // A resource lookup, not Class.forName: nothing is loaded. Production Fabric has intermediary class names.
        return Platform.class.getClassLoader().getResource("net/minecraft/server/level/ChunkMap.class") != null;
    }
}
