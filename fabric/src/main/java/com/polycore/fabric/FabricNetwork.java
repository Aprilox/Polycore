package com.polycore.fabric;

import com.polycore.network.PolyNetwork;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the server's receivers of the Polycore channels with Fabric API and forwards them to the
 * loader-independent handlers, on the game thread. A vanilla peer is fine: nothing is sent unless it has the channel.
 */
public final class FabricNetwork {
    private FabricNetwork() {}

    public static void register() {
        toServer(PolyNetwork.RequestState.ID, PolyNetwork.RequestState::read, PolyNetwork::onRequestState);
        toServer(PolyNetwork.Action.ID, PolyNetwork.Action::read, PolyNetwork::onAction);
    }

    private static <T> void toServer(ResourceLocation id, Function<FriendlyByteBuf, T> read, BiConsumer<ServerPlayer, T> handler) {
        // Fabric calls this on the network thread: decode here, handle on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, listener, buf, sender) -> {
            T payload = read.apply(buf);
            server.execute(() -> handler.accept(player, payload));
        });
    }

    public static FriendlyByteBuf encode(PolyNetwork.Payload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        return buf;
    }
}
