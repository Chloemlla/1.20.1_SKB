package com.sweattypalms.skyblock.core.items.types.end.items;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.events.def.SkyblockPlayerDamageEntityEvent;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.IHasAbilityDamage;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShamanTotemBlade extends SkyblockItem implements IHasAbility {
    public static final String ID = "shaman_totem_blade";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 110d,
            Stats.STRENGTH, 75d,
            Stats.INTELLIGENCE, 90d
    );

    public ShamanTotemBlade() {
        super(
                ID,
                "Shaman Totem Blade",
                Material.GOLDEN_SWORD,
                List.of(
                        "$7Weapon shell for Shaman skill",
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
                new ShamanTornado(),
                new SpiderLeap()
        );
    }

    public static class ShamanTornado implements ITriggerableAbility, IHasAbilityDamage {
        private static final int TORNADO_DURATION_TICKS = 120;
        private static final long PARTICLE_UPDATE_PERIOD = 2L;
        private static final int VERTICAL_SEGMENTS = 5;
        private static final double TORNADO_HEIGHT = 4.4;
        private static final double BASE_RADIUS = 0.55;
        private static final double TOP_RADIUS = 1.75;
        private static final double SPIN_PER_FRAME_DEGREES = 24.0;
        private static final double VERTICAL_TWIST_DEGREES = 140.0;
        private static final Particle.DustOptions CORE_DUST =
                new Particle.DustOptions(Color.fromRGB(86, 228, 255), 1.0f);
        private static final Particle.DustOptions EDGE_DUST =
                new Particle.DustOptions(Color.fromRGB(201, 255, 247), 0.85f);

        private static final Map<UUID, Location> windLocation = new HashMap<>();
        private static final Map<UUID, Integer> activeMoveTasks = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "Spiritual Tornado";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Summon a moving tornado that",
                    "$7damages nearby targets over time."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player caster = interactEvent.getSkyblockPlayer().getPlayer();
            UUID casterId = caster.getUniqueId();

            Integer previousTask = activeMoveTasks.remove(casterId);
            if (previousTask != null) {
                Bukkit.getScheduler().cancelTask(previousTask);
            }

            Vector direction = caster.getLocation().getDirection().clone();
            direction.setY(0);
            if (direction.lengthSquared() < 0.0001) {
                direction = new Vector(1, 0, 0);
            } else {
                direction.normalize();
            }

            createMovingTornado(caster, direction);
            caster.playSound(caster.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 0.7f, 1.2f);
        }

        private void createMovingTornado(Player caster, Vector direction) {
            UUID casterId = caster.getUniqueId();
            windLocation.put(casterId, caster.getLocation().clone());

            BukkitRunnable moveTask = new BukkitRunnable() {
                int moveCounter = 0;

                @Override
                public void run() {
                    if (!caster.isOnline() || caster.isDead()) {
                        cleanup(casterId);
                        cancel();
                        return;
                    }

                    if (moveCounter >= 3) {
                        cleanup(casterId);
                        cancel();
                        return;
                    }

                    Location current = windLocation.getOrDefault(casterId, caster.getLocation());
                    Location newLocation = current.clone().add(direction.getX() * 2, 0, direction.getZ() * 2);

                    startDamageField(caster, newLocation);
                    createTornado(newLocation);

                    windLocation.put(casterId, newLocation);
                    moveCounter++;
                }

                private void cleanup(UUID id) {
                    activeMoveTasks.remove(id);
                    windLocation.remove(id);
                }
            };

            int taskId = moveTask.runTaskTimer(SkyBlock.getInstance(), 0L, 20L * 5L).getTaskId();
            activeMoveTasks.put(casterId, taskId);
        }

        private void startDamageField(Player caster, Location center) {
            BukkitRunnable fieldTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!caster.isOnline() || caster.isDead()) {
                        cancel();
                        return;
                    }

                    World world = center.getWorld();
                    if (world == null) {
                        cancel();
                        return;
                    }

                    for (Entity entity : world.getNearbyEntities(
                            center,
                            3,
                            3,
                            3,
                            nearby -> nearby instanceof LivingEntity livingEntity
                                    && !nearby.getUniqueId().equals(caster.getUniqueId())
                                    && !livingEntity.isDead()
                    )) {
                        LivingEntity livingEntity = (LivingEntity) entity;

                        if (livingEntity instanceof Player victimPlayer) {
                            victimPlayer.damage(2.0, caster);
                            continue;
                        }

                        SkyblockPlayerDamageEntityEvent damageEvent = new SkyblockPlayerDamageEntityEvent(
                                livingEntity,
                                caster,
                                SkyblockPlayerDamageEntityEvent.DamageType.ABILITY
                        );

                        if (damageEvent.getSkyblockMob() == null) {
                            livingEntity.damage(2.0, caster);
                            continue;
                        }

                        damageEvent.setAbility(thisAbility());
                        damageEvent.setAbilityItem(caster.getInventory().getItemInMainHand());
                        Bukkit.getPluginManager().callEvent(damageEvent);
                    }
                }

                private ShamanTornado thisAbility() {
                    return ShamanTornado.this;
                }
            };

            fieldTask.runTaskTimer(SkyBlock.getInstance(), 0L, 1L);
            Bukkit.getScheduler().runTaskLater(SkyBlock.getInstance(), fieldTask::cancel, 20L * 4L);
        }

        private void createTornado(Location location) {
            BukkitRunnable tornadoTask = new BukkitRunnable() {
                int frame = 0;

                @Override
                public void run() {
                    if (frame >= (TORNADO_DURATION_TICKS / PARTICLE_UPDATE_PERIOD)) {
                        cancel();
                        return;
                    }

                    World world = location.getWorld();
                    if (world == null) {
                        cancel();
                        return;
                    }

                    double cx = location.getX();
                    double cy = location.getY();
                    double cz = location.getZ();
                    double spin = frame * SPIN_PER_FRAME_DEGREES;

                    for (int segment = 0; segment < VERTICAL_SEGMENTS; segment++) {
                        double progress = segment / (double) (VERTICAL_SEGMENTS - 1);
                        double y = cy + progress * TORNADO_HEIGHT;
                        double radius = BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * progress;

                        for (int strand = 0; strand < 2; strand++) {
                            double angle = spin + (strand * 180.0) + (progress * VERTICAL_TWIST_DEGREES);
                            double rad = Math.toRadians(angle);
                            double x = cx + (Math.cos(rad) * radius);
                            double z = cz + (Math.sin(rad) * radius);

                            world.spawnParticle(
                                    Particle.REDSTONE,
                                    x,
                                    y,
                                    z,
                                    1,
                                    0,
                                    0,
                                    0,
                                    0,
                                    strand == 0 ? CORE_DUST : EDGE_DUST
                            );
                        }
                    }

                    if ((frame & 1) == 0) {
                        world.spawnParticle(
                                Particle.CLOUD,
                                cx,
                                cy + 0.2,
                                cz,
                                1,
                                0.18,
                                0.04,
                                0.18,
                                0
                        );
                    }

                    if (frame % 3 == 0) {
                        world.spawnParticle(
                                Particle.ENCHANTMENT_TABLE,
                                cx,
                                cy + TORNADO_HEIGHT + 0.25,
                                cz,
                                1,
                                0.1,
                                0.06,
                                0.1,
                                0
                        );
                    }

                    frame++;
                }
            };

            tornadoTask.runTaskTimer(SkyBlock.getInstance(), 0L, PARTICLE_UPDATE_PERIOD);
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

        @Override
        public double getBaseAbilityDamage() {
            return 2.0;
        }

        @Override
        public double getAbilityScaling() {
            return 0.0;
        }
    }

    public static class SpiderLeap implements ITriggerableAbility {
        private static final Map<UUID, Integer> landingWatchTasks = new ConcurrentHashMap<>();
        private static final Set<UUID> skillLocked = ConcurrentHashMap.newKeySet();
        private static final Set<UUID> leapingCache = ConcurrentHashMap.newKeySet();
        private static final Map<UUID, SkitterData> skitterData = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "Skitter Leap";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Leap forward and explode on landing."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player player = interactEvent.getSkyblockPlayer().getPlayer();
            UUID playerId = player.getUniqueId();

            if (skillLocked.contains(playerId)) return;
            skillLocked.add(playerId);
            leapingCache.add(playerId);

            World world = player.getWorld();
            Location loc = player.getLocation();
            world.playSound(loc, Sound.ENTITY_PARROT_IMITATE_SPIDER, 1f, 1f);

            Vector dir = loc.getDirection().clone();
            double y = (0.2 * Math.pow(dir.getY(), 2)) + 0.7;
            dir.setY(y);
            dir.multiply(1.8);
            player.setVelocity(dir);

            int landingWatchTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(SkyBlock.getInstance(), () -> {
                if (!player.isOnline()) {
                    stopLeap(playerId);
                    return;
                }

                if (player.isOnGround()) {
                    stopLandingWatcher(playerId);
                    skillLocked.remove(playerId);
                    explodeAtLocation(player.getLocation());
                }
            }, 5L, 1L);

            landingWatchTasks.put(playerId, landingWatchTask);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }

                    Location below = player.getLocation().clone().add(0, -1, 0);
                    boolean reachedGround = player.isOnGround() || !below.getBlock().isPassable();
                    if (!reachedGround) return;

                    Bukkit.getScheduler().runTaskLater(SkyBlock.getInstance(), () -> {
                        if (leapingCache.remove(playerId)) {
                            startSkitter(playerId);
                        }
                    }, 5L);

                    cancel();
                }
            }.runTaskTimer(SkyBlock.getInstance(), 5L, 2L);
        }

        private void stopLeap(UUID playerId) {
            stopLandingWatcher(playerId);
            skillLocked.remove(playerId);
            leapingCache.remove(playerId);
        }

        private void stopLandingWatcher(UUID playerId) {
            Integer task = landingWatchTasks.remove(playerId);
            if (task != null) {
                Bukkit.getScheduler().cancelTask(task);
            }
        }

        private void startSkitter(UUID playerId) {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    SkitterData data = skitterData.get(playerId);
                    if (data == null || data.time <= 0 || data.count >= 4) {
                        skitterData.remove(playerId);
                        cancel();
                        return;
                    }

                    data.time--;
                }
            };

            SkitterData data = new SkitterData(task, 30, 0);
            skitterData.put(playerId, data);
            task.runTaskTimer(SkyBlock.getInstance(), 0L, 2L);
        }

        private void explodeAtLocation(Location location) {
            World world = location.getWorld();
            if (world == null) return;
            world.createExplosion(location.getX(), location.getY(), location.getZ(), 2.0f, false, false);
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.LEFT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return false;
            Player player = interactEvent.getSkyblockPlayer().getPlayer();
            return interactEvent.getInteractType() == getTriggerType()
                    && !skillLocked.contains(player.getUniqueId());
        }

        static class SkitterData {
            private final BukkitRunnable task;
            private int time;
            private int count;

            private SkitterData(BukkitRunnable task, int time, int count) {
                this.task = task;
                this.time = time;
                this.count = count;
            }

            private void add() {
                if (time != 30) {
                    count++;
                }
            }
        }
    }
}
