package com.polycore.neoforge;

import com.polycore.Polycore;
import com.polycore.platform.Platform;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.PacketDistributor;

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
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // NeoForge 21.6 has no ClientPacketDistributor: send through the vanilla connection (what it does anyway).
        com.polycore.neoforge.client.NeoForgeClientAccess.sendToServer(payload);
    }

    @Override
    public boolean playerHasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        try {
            return player.connection != null && player.connection.hasChannel(type);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean serverHasChannel(CustomPacketPayload.Type<?> type) {
        // Client classes do not exist on a dedicated server: keep them in a separate class.
        return FMLEnvironment.dist.isClient() && com.polycore.neoforge.client.NeoForgeClientAccess.serverHasChannel(type);
    }
}
