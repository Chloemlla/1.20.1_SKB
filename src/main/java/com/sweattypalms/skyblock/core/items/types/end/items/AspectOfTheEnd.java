package com.sweattypalms.skyblock.core.items.types.end.items;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.AbilityManager;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ICooldown;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.IUsageCost;
import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import com.sweattypalms.skyblock.core.player.sub.bonus.Bonus;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

public class AspectOfTheEnd extends SkyblockItem implements IHasAbility {
    public static final String ID = "aspect_of_the_end";
    private static final Map<Stats, Double> stats = Map.of(
            Stats.DAMAGE, 100d,
            Stats.STRENGTH, 100d
    );
    public AspectOfTheEnd() {
        super(
                "aspect_of_the_end",
                "Aspect of the End",
                Material.DIAMOND_SWORD,
                null,
                stats,
                Rarity.RARE,
                SkyblockItemType.SWORD
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new AspectOfTheEndTeleportAbility(),
                new OutlawsVaultAbility()
        );
    }
    public static class AspectOfTheEndTeleportAbility extends AbilityManager.TELEPORT_ABILITY {
        public AspectOfTheEndTeleportAbility() {
            super(8);
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(
                    Stats.INTELLIGENCE, 50.0
            );
        }

        @Override
        public String getName() {
            return "Instant Transmission";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Teleport $a8 $7blocks ahead of",
                    "$7you and gain $a+50 " + Stats.SPEED.getSymbol() + "Speed",
                    "$7for $a3 seconds$7."
            );
        }

        @Override
        public void apply(Event event) {
            super.apply(event);
            SkyblockPlayer skyblockPlayer = ((SkyblockInteractEvent) event).getSkyblockPlayer();
            Bonus speedBonus = new Bonus(Stats.SPEED, 50, 3000);
            skyblockPlayer.getBonusManager().setBonus("aote.speed", speedBonus);
        }
    }

    public static class OutlawsVaultAbility implements ITriggerableAbility, IUsageCost, ICooldown {
        private static final long COOLDOWN_MS = 4_000L;
        private static final int MAX_TRACK_TICKS = 60;

        @Override
        public String getName() {
            return "Outlaws Vault";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Launch an anvil anchor and vault",
                    "$7towards it. Looking down launches",
                    "$7you up, looking forward grapples."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            SkyblockPlayer skyblockPlayer = interactEvent.getSkyblockPlayer();
            if (isOnCooldown(skyblockPlayer)) return;

            ICooldown.super.apply(event);

            Player player = skyblockPlayer.getPlayer();
            FallingBlock anchor = player.getWorld().spawnFallingBlock(
                    player.getEyeLocation(),
                    Material.ANVIL.createBlockData()
            );
            anchor.setDropItem(false);
            anchor.setHurtEntities(false);
            anchor.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.5));

            Bat tether = player.getWorld().spawn(anchor.getLocation(), Bat.class, bat -> {
                bat.setInvisible(true);
                bat.setAI(false);
                bat.setSilent(true);
            });
            tether.setLeashHolder(player);

            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.3f);

            new BukkitRunnable() {
                private int lived = 0;
                private org.bukkit.Location lastAnchorLocation = anchor.getLocation().clone();

                @Override
                public void run() {
                    lived++;

                    if (!player.isOnline() || player.isDead()) {
                        cleanup();
                        return;
                    }

                    if (anchor.isValid()) {
                        lastAnchorLocation = anchor.getLocation().clone();
                    }

                    if (tether.isValid()) {
                        tether.teleport(lastAnchorLocation.clone().add(0, -1, 0));
                    }

                    boolean anchorExpired = lived >= MAX_TRACK_TICKS;
                    boolean anchorLanded = lived > 4 && (
                            !anchor.isValid() ||
                                    anchor.isOnGround() ||
                                    !lastAnchorLocation.getBlock().isPassable()
                    );

                    if (!anchorLanded && !anchorExpired) return;

                    float pitch = player.getLocation().getPitch();
                    if (pitch < 15f) {
                        pullEntityToLocation(player, lastAnchorLocation.getBlock().getLocation().add(0.5, 0.8, 0.5), 0.92d);
                    } else if (pitch >= 85f) {
                        pullEntityToLocation(player, player.getLocation().add(0, 5.5, 0), 0.88d);
                    } else {
                        pullEntityToLocation(player, lastAnchorLocation.clone().add(0, 1.2, 0), 0.95d);
                    }
                    player.setFallDistance(0);

                    cleanup();
                }

                private void cleanup() {
                    if (anchor.isValid()) {
                        anchor.remove();
                    }
                    if (tether.isValid()) {
                        tether.remove();
                    }
                    cancel();
                }
            }.runTaskTimer(SkyBlock.getInstance(), 0L, 1L);
        }

        private void pullEntityToLocation(Entity entity, org.bukkit.Location target, double multiply) {
            org.bukkit.Location entityLocation = entity.getLocation();
            Vector boost = entity.getVelocity();
            boost.setY(0.3);
            entity.setVelocity(boost);

            Bukkit.getScheduler().runTaskLater(SkyBlock.getInstance(), () -> {
                double distance = target.distance(entityLocation);
                if (distance < 0.001) return;

                double g = -0.08;
                double t = distance;
                double vx = (1.0 + 0.07 * t) * (target.getX() - entityLocation.getX()) / t;
                double vy = (1.0 + 0.03 * t) * (target.getY() - entityLocation.getY()) / t - 0.5 * g * t;
                double vz = (1.0 + 0.07 * t) * (target.getZ() - entityLocation.getZ()) / t;

                Vector velocity = entity.getVelocity();
                velocity.setX(vx);
                velocity.setY(vy);
                velocity.setZ(vz);
                velocity.multiply(multiply);
                entity.setVelocity(velocity);
            }, 1L);
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.LEFT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent interactEvent
                    && interactEvent.getInteractType() == getTriggerType()
                    && !isOnCooldown(interactEvent.getSkyblockPlayer());
        }

        @Override
        public long getCooldown(SkyblockPlayer skyblockPlayer) {
            return COOLDOWN_MS;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(
                    Stats.INTELLIGENCE, 60d
            );
        }
    }

}
