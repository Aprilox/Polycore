package com.polycore.util;

import com.polycore.config.PolyConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who can see and change what.
 * <ul>
 *   <li>Singleplayer host (your own world, even without cheats): full access.</li>
 *   <li>Operators (level 2+): full access.</li>
 *   <li>Everyone else: read-only, if the server allows it (permissions.allowNonOpView).</li>
 * </ul>
 */
public final class Permissions {
    private Permissions() {}

    public static boolean isOwner(ServerPlayer p) {
        return p.level().getServer().isSingleplayerOwner(p.nameAndId());
    }

    public static boolean canEdit(ServerPlayer p) {
        return isOwner(p) || p.hasPermissions(2);
    }

    public static boolean canView(ServerPlayer p) {
        return canEdit(p) || PolyConfig.get().allowNonOpView;
    }

    public static boolean canEdit(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        return p != null ? canEdit(p) : s.hasPermission(2);
    }

    public static boolean canView(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        return p != null ? canView(p) : true;
    }
}
