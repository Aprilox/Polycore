package com.polycore.platform;

import java.nio.file.Path;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client side only. */
    void sendToServer(CustomPacketPayload payload);

    /** Whether this player's client has Polycore (has registered the given channel). */
    boolean playerHasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type);

    /** Client side only: whether the server we are connected to has Polycore. */
    boolean serverHasChannel(CustomPacketPayload.Type<?> type);
}
