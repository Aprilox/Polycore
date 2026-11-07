package com.polycore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.polycore.command.PolyCommands;
import com.polycore.config.PolyConfig;
import com.polycore.guard.TpsGuard;
import com.polycore.lag.LagCapture;
import com.polycore.lag.LagMonitor;
import com.polycore.menu.VanillaMenu;
import com.polycore.pregen.PregenManager;
import com.polycore.util.Conflicts;
import com.polycore.util.Notifier;
import com.polycore.util.Permissions;
import com.polycore.util.History;
import com.polycore.util.ServerMetrics;
import com.polycore.util.StatsSnapshot;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Loader-independent entry point. Each loader forwards its events here
 * (see com.polycore.neoforge.PolycoreNeoForge).
 */
public final class Polycore {
    public static final String MODID = "polycore";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
    private static int tickCounter = 0;

    private Polycore() {}

    /** Called once when the mod is constructed. */
    public static void init() {
        PolyConfig.load();
        LOGGER.info("[Polycore] Loaded: {} logical cores", Runtime.getRuntime().availableProcessors());
    }

    /** Runs an action at the start of the next server tick (server thread). */
    public static void runNextTick(Runnable r) {
        NEXT_TICK.add(r);
    }

    public static void onServerStarting(MinecraftServer server) {
        PolyConfig.load();
        Conflicts.reasons(); // checked once, logged to the console
    }

    public static void onServerTickStart(MinecraftServer server) {
        ServerMetrics.onTickStart();
        LagMonitor.tickStart();
        long start = System.nanoTime();
        Runnable r;
        while ((r = NEXT_TICK.poll()) != null) {
            try {
                r.run();
            } catch (Throwable t) {
                LOGGER.error("[Polycore] Error in a deferred task", t);
            }
        }
        LagMonitor.add(LagMonitor.Section.POLYCORE, System.nanoTime() - start);
    }

    public static void onServerTickEnd(MinecraftServer server) {
        long start = System.nanoTime();
        TpsGuard.tick();
        try {
            PregenManager.tick(server);
        } catch (Throwable t) {
            LOGGER.error("[Polycore] Error during pre-generation, stopping", t);
            PregenManager.shutdown();
        }
        if (++tickCounter % 20 == 0) {
            ServerMetrics.sampleCpu();
            StatsSnapshot.sample();
            History.sample();
            VanillaMenu.refreshOpenMenus(server);
        }
        LagMonitor.add(LagMonitor.Section.POLYCORE, System.nanoTime() - start);
        ServerMetrics.onTickEnd();
        LagMonitor.tickEnd(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        PregenManager.shutdown();
        LagCapture.reset();
        LagMonitor.reset();
        com.polycore.pregen.ParityHash.clear();
        TpsGuard.reset(server);
        NEXT_TICK.clear();
    }

    /** Operators learn at login when another mod keeps an optimization off (toast, or action bar). */
    public static void onPlayerJoin(ServerPlayer player) {
        if (!Permissions.canEdit(player)) return;
        int off = Conflicts.reasons().size() + (TpsGuard.Measure.SIM_DISTANCE.blockReason() != null
                && !Conflicts.blocked(Conflicts.Feature.SIM_DISTANCE) ? 1 : 0);
        if (off > 0) {
            Notifier.send(player, Notifier.Level.WARN, off + (off == 1 ? " optimization is off" : " optimizations are off"),
                    "Another mod changes the same game code. Details: Polycore menu, Optimizations and TPS Guard.");
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        PolyCommands.register(dispatcher);
    }
}
