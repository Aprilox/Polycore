package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.lag.LagMonitor;
import com.polycore.lag.LagMonitor.Section;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lag analyzer: times the server-wide parts of a tick (network, chunk sending, datapack functions, autosave).
 * Observation only: every call runs exactly as before.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerFunctionManager;tick()V"))
    private void polycore$functions(ServerFunctionManager functions, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(functions);
        LagMonitor.add(Section.FUNCTIONS, System.nanoTime() - t);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V"))
    private void polycore$network(ServerConnectionListener connections, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(connections);
        LagMonitor.add(Section.NETWORK, System.nanoTime() - t);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/PlayerChunkSender;sendNextChunks(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void polycore$sendChunks(PlayerChunkSender sender, ServerPlayer player, Operation<Void> original) {
        long t = System.nanoTime();
        original.call(sender, player);
        LagMonitor.add(Section.NETWORK, System.nanoTime() - t);
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveEverything(ZZZ)Z"))
    private boolean polycore$autosave(MinecraftServer server, boolean suppressLog, boolean flush, boolean forced, Operation<Boolean> original) {
        long t = System.nanoTime();
        boolean result = original.call(server, suppressLog, flush, forced);
        LagMonitor.add(Section.AUTOSAVE, System.nanoTime() - t);
        return result;
    }
}
