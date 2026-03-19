package com.sweattypalms.skyblock.core.events.listeners;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.items.types.end.items.SnowmanScepter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.UUID;

public class BeastChargeRideListener implements Listener {

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!SnowmanScepter.BeastCharge.isRideProxy(event.getDismounted())
                && !SnowmanScepter.BeastCharge.isBeastRavager(event.getDismounted())) {
            return;
        }

        UUID playerId = player.getUniqueId();
        SnowmanScepter.BeastCharge.cleanupRideForPlayer(playerId);
        player.setNoDamageTicks(40);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SnowmanScepter.BeastCharge.cleanupRideForPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        SnowmanScepter.BeastCharge.cleanupRideForPlayer(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onBeastRavagerDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Ravager ravager)) {
            return;
        }
        if (!SnowmanScepter.BeastCharge.isBeastRavager(ravager)) {
            return;
        }

        SnowmanScepter.BeastCharge.cleanupRideForRavager(ravager);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != SkyBlock.getInstance()) {
            return;
        }

        SnowmanScepter.BeastCharge.cleanupAllRides();
    }
}
