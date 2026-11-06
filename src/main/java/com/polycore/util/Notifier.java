package com.polycore.util;

import com.polycore.Polycore;
import com.polycore.network.PolyNetwork;
import com.polycore.platform.Services;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends feedback without using the chat:
 * <ul>
 *   <li>players with Polycore installed: a toast in the Polycore style (and the menu activity log),</li>
 *   <li>players without it: the action bar above the hotbar,</li>
 *   <li>console / command blocks: a normal command reply.</li>
 * </ul>
 */
public final class Notifier {
    public enum Level { INFO, SUCCESS, WARN, ERROR }

    private Notifier() {}

    public static boolean hasClientMod(ServerPlayer p) {
        return Services.PLATFORM.playerHasChannel(p, PolyNetwork.Notify.TYPE);
    }

    public static void send(@Nullable ServerPlayer p, Level level, String title, String message) {
        Polycore.LOGGER.info("[Polycore] {}: {}", title, message);
        if (p == null) return;
        if (hasClientMod(p)) {
            Services.PLATFORM.sendToPlayer(p, new PolyNetwork.Notify(level.name(), title, message));
        } else {
            p.displayClientMessage(Component.literal(title + " - " + message).withStyle(color(level)), true);
        }
    }

    public static void send(CommandSourceStack source, Level level, String title, String message) {
        ServerPlayer p = source.getPlayer();
        if (p != null) {
            send(p, level, title, message);
        } else {
            Component c = Component.literal("[Polycore] " + title + ": " + message).withStyle(color(level));
            if (level == Level.ERROR) source.sendFailure(c);
            else source.sendSuccess(() -> c, false);
        }
    }

    private static ChatFormatting color(Level level) {
        return switch (level) {
            case SUCCESS -> ChatFormatting.GREEN;
            case WARN -> ChatFormatting.GOLD;
            case ERROR -> ChatFormatting.RED;
            default -> ChatFormatting.AQUA;
        };
    }
}
