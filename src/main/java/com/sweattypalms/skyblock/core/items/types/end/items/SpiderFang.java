package com.sweattypalms.skyblock.core.items.types.end.items;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

public class SpiderFang extends SkyblockItem implements IHasAbility {
    public static final String ID = "spider_fang";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 125d,
            Stats.STRENGTH, 95d
    );

    public SpiderFang() {
        super(
                ID,
                "Spider Fang",
                Material.IRON_SWORD,
                List.of(
                        "$7Weapon shell for Spider skill",
                        "$7migration."
                ),
                STATS,
                Rarity.EPIC,
                SkyblockItemType.SWORD
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new SquidPull()
        );
    }

    public static class SquidPull implements ITriggerableAbility {
        @Override
        public String getName() {
            return "Squid Pull";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Launch nearby players upward and",
                    "$7pull them back toward you."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player player = interactEvent.getSkyblockPlayer().getPlayer();

            Player target = findLineOfSightTarget(player);
            if (target == null) {
                player.sendMessage("No players nearby.");
            }

            for (Entity nearby : player.getWorld().getNearbyEntities(
                    player.getLocation(),
                    5,
                    5,
                    5,
                    entity -> entity instanceof Player other
                            && !other.getUniqueId().equals(player.getUniqueId())
            )) {
                if (!(nearby instanceof Player other)) continue;

                other.damage(4.0, player);
                other.setVelocity(new Vector(0, 0.6, 0));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!other.isOnline() || other.isDead()) return;
                        Vector pull = player.getLocation().toVector()
                                .subtract(other.getLocation().toVector())
                                .normalize()
                                .multiply(0.8);
                        other.setVelocity(pull);
                    }
                }.runTaskLater(SkyBlock.getInstance(), 5L);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 1f, 1.1f);
        }

        private Player findLineOfSightTarget(Player player) {
            RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    4.0,
                    0.9,
                    entity -> entity instanceof Player nearbyPlayer
                            && !nearbyPlayer.getUniqueId().equals(player.getUniqueId())
                            && !nearbyPlayer.isDead()
            );
            if (result == null || !(result.getHitEntity() instanceof Player target)) return null;
            return target;
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.RIGHT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent interactEvent
                    && interactEvent.getInteractType() == getTriggerType();
        }
    }
}
