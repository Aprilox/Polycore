package com.polycore.neoforge;

import com.polycore.Polycore;
import com.polycore.neoforge.client.PolycoreNeoForgeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point: forwards loader events to the loader-independent {@link Polycore} class. The only @Mod class:
 * NeoForge 20.5 (FML 3.0) builds one per mod id and ignores {@code dist}.
 */
@Mod(Polycore.MODID)
public final class PolycoreNeoForge {
    public PolycoreNeoForge(IEventBus modBus, ModContainer container) {
        Polycore.init();
        modBus.addListener(NeoForgeNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) PolycoreNeoForgeClient.init(modBus);

        NeoForge.EVENT_BUS.addListener(PolycoreNeoForge::onServerStarting);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForge::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForge::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForge::onServerStopping);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForge::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(PolycoreNeoForge::onPlayerJoin);
    }

    private static void onServerStarting(ServerStartingEvent e) {
        Polycore.onServerStarting(e.getServer());
    }

    private static void onServerTickPre(ServerTickEvent.Pre e) {
        Polycore.onServerTickStart(e.getServer());
    }

    private static void onServerTickPost(ServerTickEvent.Post e) {
        Polycore.onServerTickEnd(e.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent e) {
        Polycore.onServerStopping(e.getServer());
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) Polycore.onPlayerJoin(player);
    }

    private static void onRegisterCommands(RegisterCommandsEvent e) {
        Polycore.registerCommands(e.getDispatcher());
    }
}
