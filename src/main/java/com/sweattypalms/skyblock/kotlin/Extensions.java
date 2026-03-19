package com.sweattypalms.skyblock.kotlin;

import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class Extensions {
    private Extensions() {
    }

    public static SkyblockPlayer getSkyblockPlayer(Player player) {
        return SkyblockPlayer.getSkyblockPlayer(player);
    }

    public static void something(UUID uuid) {
        Player bukkitPlayer = Bukkit.getPlayer(uuid);
        if (bukkitPlayer != null) {
            getSkyblockPlayer(bukkitPlayer);
        }
    }
}
