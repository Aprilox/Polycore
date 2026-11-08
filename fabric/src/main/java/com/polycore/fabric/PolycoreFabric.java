package com.polycore.fabric;

import com.polycore.Polycore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric entry point: forwards Fabric API events to the loader-independent {@link Polycore} class.
 */
public final class PolycoreFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Polycore.init();
        FabricNetwork.register();

        ServerLifecycleEvents.SERVER_STARTING.register(Polycore::onServerStarting);
        ServerTickEvents.START_SERVER_TICK.register(Polycore::onServerTickStart);
        ServerTickEvents.END_SERVER_TICK.register(Polycore::onServerTickEnd);
        ServerLifecycleEvents.SERVER_STOPPING.register(Polycore::onServerStopping);
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> Polycore.registerCommands(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Polycore.onPlayerJoin(handler.player));
    }
}
