package com.polycore.neoforge;

import com.polycore.network.PolyNetwork;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

/**
 * Registers the Polycore payloads with NeoForge 20.4 and forwards them to the loader-independent handlers, on the
 * game thread. Optional channels: a vanilla peer (or one without Polycore) can still connect.
 */
public final class NeoForgeNetwork {
    private NeoForgeNetwork() {}

    /** NeoForge 20.4 sends {@link CustomPacketPayload}s: wraps a loader-independent Polycore message. */
    record Packet(PolyNetwork.Payload payload) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buf) {
            payload.write(buf);
        }

        @Override
        public ResourceLocation id() {
            return payload.id();
        }
    }

    static void register(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar r = event.registrar(com.polycore.Polycore.MODID).versioned(PolyNetwork.PROTOCOL).optional();
        toServer(r, PolyNetwork.RequestState.ID, PolyNetwork.RequestState::read, PolyNetwork::onRequestState);
        toServer(r, PolyNetwork.Action.ID, PolyNetwork.Action::read, PolyNetwork::onAction);
        toClient(r, PolyNetwork.State.ID, PolyNetwork.State::read, PolyNetwork::onState);
        toClient(r, PolyNetwork.ReportContent.ID, PolyNetwork.ReportContent::read, PolyNetwork::onReportContent);
        toClient(r, PolyNetwork.OpenMenu.ID, PolyNetwork.OpenMenu::read, PolyNetwork::onOpenMenu);
        toClient(r, PolyNetwork.Notify.ID, PolyNetwork.Notify::read, PolyNetwork::onNotify);
    }

    @SuppressWarnings("unchecked")
    private static <T extends PolyNetwork.Payload> void toServer(IPayloadRegistrar r, ResourceLocation id,
                                                                 FriendlyByteBuf.Reader<T> read, BiConsumer<ServerPlayer, T> handler) {
        r.play(id, buf -> new Packet(read.apply(buf)), h -> h.server((p, ctx) -> ctx.workHandler().execute(() -> {
            if (ctx.player().orElse(null) instanceof ServerPlayer player) handler.accept(player, (T) p.payload());
        })));
    }

    @SuppressWarnings("unchecked")
    private static <T extends PolyNetwork.Payload> void toClient(IPayloadRegistrar r, ResourceLocation id,
                                                                 FriendlyByteBuf.Reader<T> read, Consumer<T> handler) {
        r.play(id, buf -> new Packet(read.apply(buf)), h -> h.client((p, ctx) -> ctx.workHandler().execute(() -> handler.accept((T) p.payload()))));
    }

    static void sendToPlayer(PolyNetwork.Payload payload, ServerPlayer player) {
        PacketDistributor.PLAYER.with(player).send(new Packet(payload));
    }

    public static void sendToServer(PolyNetwork.Payload payload) {
        PacketDistributor.SERVER.noArg().send(new Packet(payload));
    }
}
