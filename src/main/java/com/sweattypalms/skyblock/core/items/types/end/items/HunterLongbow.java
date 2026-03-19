package com.sweattypalms.skyblock.core.items.types.end.items;

import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.events.def.SkyblockProjectileLaunchEvent;
import com.sweattypalms.skyblock.core.events.def.SkyblockProjectilePrelaunchEvent;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.items.builder.item.IShortBow;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class HunterLongbow extends SkyblockItem implements IHasAbility, IShortBow {
    public static final String ID = "hunter_longbow";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 95d,
            Stats.CRIT_DAMAGE, 45d
    );

    public HunterLongbow() {
        super(
                ID,
                "Hunter Longbow",
                Material.BOW,
                List.of(
                        "$7Weapon shell for Hunter skill",
                        "$7migration."
                ),
                STATS,
                Rarity.EPIC,
                SkyblockItemType.BOW
        );
    }

    @Override
    public List<String> getShortBowDescription() {
        return List.of(
                "$7Shoots instantly.",
                "$7Fully powered arrows will home",
                "$7toward nearby players."
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new HunterHomingArrow(),
                new SkeletonExplosiveArrow()
        );
    }

    public static class HunterHomingArrow implements ITriggerableAbility {
        @Override
        public String getName() {
            return "Hunter's Focus";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Fire a quick shot. If its launch",
                    "$7speed is high enough, it homes",
                    "$7in on nearby players."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player shooter = interactEvent.getSkyblockPlayer().getPlayer();

            SkyblockProjectilePrelaunchEvent prelaunchEvent = new SkyblockProjectilePrelaunchEvent(interactEvent.getSkyblockPlayer());
            Bukkit.getPluginManager().callEvent(prelaunchEvent);
            if (prelaunchEvent.isCancelled()) return;

            shooter.getWorld().spawn(
                    shooter.getEyeLocation().add(shooter.getEyeLocation().getDirection().multiply(1.5)),
                    ArmorStand.class,
                    armorStand -> {
                        armorStand.setInvisible(true);
                        armorStand.setInvulnerable(true);
                        armorStand.setMarker(true);
                        armorStand.setSmall(true);
                        armorStand.setCollidable(false);

                        Arrow arrow = armorStand.launchProjectile(Arrow.class);
                        arrow.setShooter(shooter);

                        SkyblockProjectileLaunchEvent<Arrow> projectileLaunchEvent =
                                new SkyblockProjectileLaunchEvent<>(interactEvent.getSkyblockPlayer(), List.of(arrow));
                        Bukkit.getPluginManager().callEvent(projectileLaunchEvent);

                        if (projectileLaunchEvent.isCancelled()) {
                            arrow.remove();
                            armorStand.remove();
                            return;
                        }

                        shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
                        armorStand.remove();

                        Vector velocity = arrow.getVelocity();
                        boolean max =
                                Math.abs(velocity.getX()) > 2.0 ||
                                        Math.abs(velocity.getY()) > 2.0 ||
                                        Math.abs(velocity.getZ()) > 2.0;

                        if (!max) return;

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!shooter.isOnline() || arrow.isDead() || arrow.isOnGround()) {
                                    cancel();
                                    return;
                                }

                                var arrowLocation = arrow.getLocation();
                                Player target = arrow.getWorld().getNearbyEntities(
                                                arrowLocation,
                                                5,
                                                5,
                                                5,
                                                entity -> entity instanceof Player candidate
                                                        && !candidate.getUniqueId().equals(shooter.getUniqueId())
                                                        && !candidate.isDead()
                                        ).stream()
                                        .filter(Player.class::isInstance)
                                        .map(Player.class::cast)
                                        .min(Comparator.comparingDouble(candidate -> candidate.getLocation().distanceSquared(arrowLocation)))
                                        .orElse(null);

                                if (target == null) return;

                                Vector homing = target.getLocation().toVector()
                                        .subtract(arrow.getLocation().toVector())
                                        .multiply(0.8);
                                arrow.setVelocity(homing);
                            }
                        }.runTaskTimer(com.sweattypalms.skyblock.SkyBlock.getInstance(), 0L, 1L);
                    }
            );
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.RIGHT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return false;
            if (interactEvent.getInteractType() != getTriggerType()) return false;
            SkyblockItem inHand = interactEvent.getSkyblockPlayer().getInventoryManager().getSkyblockItemInHand();
            return inHand instanceof HunterLongbow;
        }
    }

    public static class SkeletonExplosiveArrow implements ITriggerableAbility {
        @Override
        public String getName() {
            return "Bone Blast";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Fire an explosive arrow that",
                    "$7detonates on impact."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player shooter = interactEvent.getSkyblockPlayer().getPlayer();

            SkyblockProjectilePrelaunchEvent prelaunchEvent = new SkyblockProjectilePrelaunchEvent(interactEvent.getSkyblockPlayer());
            Bukkit.getPluginManager().callEvent(prelaunchEvent);
            if (prelaunchEvent.isCancelled()) return;

            shooter.getWorld().spawn(
                    shooter.getEyeLocation().add(shooter.getEyeLocation().getDirection().multiply(1.5)),
                    ArmorStand.class,
                    armorStand -> {
                        armorStand.setInvisible(true);
                        armorStand.setInvulnerable(true);
                        armorStand.setMarker(true);
                        armorStand.setSmall(true);
                        armorStand.setCollidable(false);

                        Arrow arrow = armorStand.launchProjectile(Arrow.class);
                        arrow.setShooter(shooter);
                        arrow.setVelocity(shooter.getLocation().getDirection().multiply(2.35));

                        SkyblockProjectileLaunchEvent<Arrow> projectileLaunchEvent =
                                new SkyblockProjectileLaunchEvent<>(interactEvent.getSkyblockPlayer(), List.of(arrow));
                        Bukkit.getPluginManager().callEvent(projectileLaunchEvent);

                        if (projectileLaunchEvent.isCancelled()) {
                            arrow.remove();
                            armorStand.remove();
                            return;
                        }

                        armorStand.remove();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!shooter.isOnline() || arrow.isDead() || arrow.isOnGround()) {
                                    var location = arrow.getLocation();
                                    if (location.getWorld() != null) {
                                        location.getWorld().createExplosion(location.getX(), location.getY(), location.getZ(), 2.0F, false, false);
                                        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.0f);
                                    }

                                    if (!arrow.isDead()) {
                                        arrow.remove();
                                    }
                                    cancel();
                                }
                            }
                        }.runTaskTimer(com.sweattypalms.skyblock.SkyBlock.getInstance(), 0L, 1L);
                    }
            );
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.LEFT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return false;
            if (interactEvent.getInteractType() != getTriggerType()) return false;
            SkyblockItem inHand = interactEvent.getSkyblockPlayer().getInventoryManager().getSkyblockItemInHand();
            return inHand instanceof HunterLongbow;
        }
    }
}
