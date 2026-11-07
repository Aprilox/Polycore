package com.polycore.forge;

import com.polycore.Polycore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entry point: forwards loader events to the loader-independent {@link Polycore} class.
 */
@Mod(Polycore.MODID)
public final class PolycoreForge {
    public PolycoreForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        Polycore.init();
        ForgeNetwork.register();
        // Client classes do not exist on a dedicated server: only touched behind this check.
        if (FMLEnvironment.dist.isClient()) com.polycore.forge.client.PolycoreForgeClient.init(modBus);

        MinecraftForge.EVENT_BUS.addListener(PolycoreForge::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForge::onServerTickPre);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForge::onServerTickPost);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForge::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForge::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(PolycoreForge::onPlayerJoin);
    }

    private static void onServerStarting(ServerStartingEvent e) {
        Polycore.onServerStarting(e.getServer());
    }

    private static void onServerTickPre(TickEvent.ServerTickEvent.Pre e) {
        Polycore.onServerTickStart(e.getServer());
    }

    private static void onServerTickPost(TickEvent.ServerTickEvent.Post e) {
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
