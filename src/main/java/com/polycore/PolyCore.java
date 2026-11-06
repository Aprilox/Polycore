package com.polycore;

import com.mojang.logging.LogUtils;
import com.polycore.command.PolyCommands;
import com.polycore.gen.WorldgenPool;
import com.polycore.network.PolyNetwork;
import com.polycore.optim.Optimization;
import com.polycore.pregen.PregenManager;
import com.polycore.util.Compat;
import com.polycore.util.ServerMetrics;
import com.polycore.util.StatsSnapshot;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(PolyCore.MODID)
public final class PolyCore {
    public static final String MODID = "polycore";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
    private static int tickCounter = 0;

    public PolyCore(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, PolyConfig.SPEC);
        modBus.addListener(PolyCore::onConfigLoad);
        modBus.addListener(PolyCore::onConfigReload);
        modBus.addListener(PolyNetwork::register);

        NeoForge.EVENT_BUS.addListener(PolyCore::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(PolyCore::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(PolyCore::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(PolyCore::onServerStopping);

        LOGGER.info("[Polycore] Loaded: {} logical cores. Moonrise={}, C2ME={}",
                Runtime.getRuntime().availableProcessors(), Compat.moonrise(), Compat.c2me());
        String block = Compat.worldgenPoolBlockReason();
        if (block != null) LOGGER.warn("[Polycore] Worldgen pool disabled: {}", block);
    }

    /** Execute une action au debut du prochain tick serveur (thread serveur). */
    public static void runNextTick(Runnable r) {
        NEXT_TICK.add(r);
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == PolyConfig.SPEC) {
            Optimization.loadFromConfig();
        }
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == PolyConfig.SPEC) {
            Optimization.loadFromConfig();
            if (WorldgenPool.isCreated()) WorldgenPool.resize(PolyConfig.poolThreads());
        }
    }

    private static void onServerTickPre(ServerTickEvent.Pre event) {
        ServerMetrics.onTickStart();
        Runnable r;
        while ((r = NEXT_TICK.poll()) != null) {
            try {
                r.run();
            } catch (Throwable t) {
                LOGGER.error("[Polycore] Error in a deferred task", t);
            }
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        try {
            PregenManager.tick();
        } catch (Throwable t) {
            LOGGER.error("[Polycore] Error during pre-generation, stopping", t);
            PregenManager.stop();
        }
        if (++tickCounter % 20 == 0) StatsSnapshot.sample();
        ServerMetrics.onTickEnd();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        PolyCommands.register(event.getDispatcher());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        PregenManager.stop();
        NEXT_TICK.clear();
    }
}
