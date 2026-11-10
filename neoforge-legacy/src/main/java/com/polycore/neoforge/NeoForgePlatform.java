package com.polycore.neoforge;

import com.polycore.Polycore;
import com.polycore.platform.Platform;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import com.polycore.network.PolyNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

/**
 * NeoForge implementation of {@link Platform}. Client-only classes are only touched from client-only methods.
 */
public final class NeoForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "NeoForge";
    }


    @Override
    public boolean isModLoaded(String modId) {
        try {
            ModList list = ModList.get();
            if (list != null) return list.isLoaded(modId);
        } catch (Throwable ignored) {
        }
        // Very early (mixin plugin): ModList is not built yet.
        try {
            LoadingModList loading = LoadingModList.get();
            return loading != null && loading.getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion() {
        try {
            return ModList.get().getModContainerById(Polycore.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("?");
        } catch (Throwable t) {
            return "?";
        }
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, PolyNetwork.Payload payload) {
        if (player.connection != null) NeoForgeNetwork.sendToPlayer(payload, player);
    }

    @Override
    public void sendToServer(PolyNetwork.Payload payload) {
        if (FMLEnvironment.dist.isClient()) com.polycore.neoforge.client.NeoForgeClientAccess.sendToServer(payload);
    }

    @Override
    public boolean playerHasChannel(ServerPlayer player, ResourceLocation channel) {
        try {
            return player.connection != null && NeoForgeNetwork.remoteHasChannel(player.connection.connection);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean serverHasChannel(ResourceLocation channel) {
        // Client classes do not exist on a dedicated server: keep them in a separate class.
        return FMLEnvironment.dist.isClient() && com.polycore.neoforge.client.NeoForgeClientAccess.serverHasChannel(channel);
    }
}
