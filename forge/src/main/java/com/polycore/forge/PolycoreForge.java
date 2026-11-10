package com.polycore.forge;

import com.polycore.Polycore;
import com.polycore.forge.test.PolycoreGameTests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.gametest.ForgeGameTestHooks;

/**
 * Forge entry point: forwards loader events to the loader-independent {@link Polycore} class.
 */
@Mod(Polycore.MODID)
public final class PolycoreForge {
    public PolycoreForge(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        Polycore.init();
        ForgeNetwork.register();
        // Client classes do not exist on a dedicated server: only touched behind this check.
        if (FMLEnvironment.dist.isClient()) com.polycore.forge.client.PolycoreForgeClient.init();
        // Dev only (never in production): the test functions behind the test instances of the dev resources.
        if (ForgeGameTestHooks.isGametestEnabled()) PolycoreGameTests.register(modBus);

        // EventBus 7 (Forge 1.21.6+): one bus per event type.
        ServerStartingEvent.BUS.addListener(e -> Polycore.onServerStarting(e.getServer()));
        TickEvent.ServerTickEvent.Pre.BUS.addListener(e -> Polycore.onServerTickStart(e.server()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> Polycore.onServerTickEnd(e.server()));
        ServerStoppingEvent.BUS.addListener(e -> Polycore.onServerStopping(e.getServer()));
        // Forge 59 (1.21.9): once a file of config/ changes, the debounce pool of FML's config watchers keeps a
        // non-daemon thread and the dedicated server never exits after /stop. The JVM is ending: unload the configs,
        // which stops those watchers.
        if (FMLEnvironment.dist.isDedicatedServer()) ServerStoppedEvent.BUS.addListener(
                e -> net.minecraftforge.fml.config.ConfigTracker.INSTANCE.forceUnload());
        RegisterCommandsEvent.BUS.addListener(e -> Polycore.registerCommands(e.getDispatcher()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer player) Polycore.onPlayerJoin(player);
        });
    }
}
