package com.polycore.fabric;

import com.polycore.Polycore;
import com.polycore.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import com.polycore.network.PolyNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of {@link Platform}. Client-only classes are only touched from client-only methods.
 */
public final class FabricPlatform implements Platform {
    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(Polycore.MODID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, PolyNetwork.Payload payload) {
        // A vanilla client (or one without Polycore) never gets a Polycore packet.
        if (ServerPlayNetworking.canSend(player, payload.id())) ServerPlayNetworking.send(player, payload.id(), FabricNetwork.encode(payload));
    }

    @Override
    public void sendToServer(PolyNetwork.Payload payload) {
        com.polycore.fabric.client.FabricClientAccess.sendToServer(payload);
    }

    @Override
    public boolean playerHasChannel(ServerPlayer player, ResourceLocation channel) {
        return ServerPlayNetworking.canSend(player, channel);
    }

    @Override
    public boolean serverHasChannel(ResourceLocation channel) {
        // Client classes do not exist on a dedicated server: keep them in a separate class.
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                && com.polycore.fabric.client.FabricClientAccess.serverHasChannel(channel);
    }
}
