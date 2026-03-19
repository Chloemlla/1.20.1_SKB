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
import fr.skytasul.guardianbeam.Laser;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ListenersEdge extends SkyblockItem implements IHasAbility {
    public static final String ID = "listeners_edge";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 165d,
            Stats.STRENGTH, 110d,
            Stats.INTELLIGENCE, 120d
    );

    public ListenersEdge() {
        super(
                ID,
                "Listeners Edge",
                Material.DIAMOND_SWORD,
                List.of(
                        "$7Migrated skill weapon for",
                        "$7the original Listeners kit."
                ),
                STATS,
                Rarity.LEGENDARY,
                SkyblockItemType.SWORD
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new GuardianBeamAbility(),
                new ArcBoltAbility()
        );
    }

    public static class GuardianBeamAbility implements ITriggerableAbility {
        private static final int RANGE = 15;
        private static final long DURATION_TICKS = 20L * 15L;
        private static final Map<UUID, BeamRuntime> activeBeams = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "Guardian Trace";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Lock onto a target and maintain",
                    "$7a damaging beam for $a15s$7."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player caster = interactEvent.getSkyblockPlayer().getPlayer();
            Player target = findTargetPlayer(caster, RANGE);
            if (target == null) return;

            UUID casterId = caster.getUniqueId();
            cancelAll(casterId);

            Laser.GuardianLaser laser;
            try {
                laser = new Laser.GuardianLaser(caster.getEyeLocation(), target, -1, RANGE);
                laser.start(SkyBlock.getInstance());
            } catch (ReflectiveOperationException e) {
                caster.sendMessage("Could not create guardian beam.");
                return;
            }

            BukkitRunnable beamFollowTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!caster.isOnline() || !target.isOnline() || target.isDead()) {
                        cancelAll(casterId);
                        return;
                    }

                    laser.moveStart(caster.getEyeLocation(), 1, null);
                }
            };

            BukkitRunnable damageTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!caster.isOnline() || !target.isOnline() || target.isDead()) {
                        cancelAll(casterId);
                        return;
                    }
                    target.damage(1.0);
                    caster.playSound(caster.getLocation(), Sound.ENTITY_GUARDIAN_HURT, 1f, 1f);
                }
            };

            int beamId = beamFollowTask.runTaskTimer(SkyBlock.getInstance(), 0L, 1L).getTaskId();
            int damageId = damageTask.runTaskTimer(SkyBlock.getInstance(), 0L, 20L).getTaskId();

            int stopId = SkyBlock.getInstance().getServer().getScheduler().runTaskLater(
                    SkyBlock.getInstance(),
                    () -> cancelAll(casterId),
                    DURATION_TICKS
            ).getTaskId();

            activeBeams.put(casterId, new BeamRuntime(laser, new ArrayList<>(List.of(beamId, damageId, stopId))));
        }

        private void cancelAll(UUID casterId) {
            BeamRuntime runtime = activeBeams.remove(casterId);
            if (runtime == null) return;

            runtime.taskIds.forEach(taskId -> SkyBlock.getInstance().getServer().getScheduler().cancelTask(taskId));
            runtime.laser.stop();
        }

        private record BeamRuntime(Laser.GuardianLaser laser, List<Integer> taskIds) {
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

    public static class ArcBoltAbility implements ITriggerableAbility {
        private static final int RANGE = 15;
        private static final Random RANDOM = new Random();

        @Override
        public String getName() {
            return "Arc Bolt";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Fire a curved bolt that ignites",
                    "$7the target on hit."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player caster = interactEvent.getSkyblockPlayer().getPlayer();
            Player target = findTargetPlayer(caster, RANGE);
            if (target == null) return;

            Vector start = spawnPoint(caster);
            Vector end = target.getLocation().toVector();
            Vector control = makeControlPoint(start, end);

            double speed = 0.5 + RANDOM.nextDouble() * 2.0;
            double maxT = Math.max(0.1, start.distance(end) / speed);
            final double[] t = {0.0};
            final int[] frame = {0};

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!caster.isOnline() || !target.isOnline() || target.isDead()) {
                        cancel();
                        return;
                    }

                    if (t[0] >= maxT) {
                        target.setFireTicks(5);
                        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.7f, 1.3f);
                        cancel();
                        return;
                    }

                    double progress = t[0] / maxT;
                    Vector p1 = lerp(start, control, progress);
                    Vector p2 = lerp(control, end, progress);
                    Vector p = lerp(p1, p2, progress);

                    Location loc = new Location(caster.getWorld(), p.getX(), p.getY(), p.getZ());
                    // Lighter trail: main point every 2 ticks, soft accent every 6 ticks.
                    if ((frame[0] & 1) == 0) {
                        caster.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 1, 0, 0, 0, 0);
                    }
                    if (frame[0] % 6 == 0) {
                        caster.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 1, 0.015, 0.015, 0.015, 0);
                    }

                    double deltaTime = speed * 0.1;
                    t[0] += deltaTime;
                    frame[0]++;
                }
            }.runTaskTimer(SkyBlock.getInstance(), 0L, 1L);
        }

        private Vector spawnPoint(Player player) {
            Location spawnPos = player.getLocation().clone().add(0, 1.5, 0);
            Vector forward = player.getEyeLocation().getDirection().setY(0);
            if (forward.lengthSquared() < 0.0001) {
                forward = new Vector(1, 0, 0);
            } else {
                forward.normalize();
            }
            Vector right = rotateAroundAxisY(forward.clone(), Math.toRadians(90));

            spawnPos.setX(spawnPos.getX() + forward.getX() * 2 + right.getX() * (Math.random() - 0.5) * 5);
            spawnPos.setZ(spawnPos.getZ() + forward.getZ() * 2 + right.getZ() * (Math.random() - 0.5) * 5);

            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, spawnPos, 1);
            return spawnPos.toVector();
        }

        private Vector rotateAroundAxisY(Vector vector, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = vector.getX() * cos + vector.getZ() * sin;
            double z = vector.getX() * -sin + vector.getZ() * cos;
            return vector.setX(x).setZ(z);
        }

        private Vector makeControlPoint(Vector start, Vector end) {
            Vector center = lerp(start, end, 0.5);
            center.setX(center.getX() + 5 * (Math.random() - 0.5));
            center.setY(center.getY() + 1 + 4 * Math.random());
            center.setZ(center.getZ() + 5 * (Math.random() - 0.5));
            return center;
        }

        private Vector lerp(Vector a, Vector b, double t) {
            return new Vector(
                    a.getX() + (b.getX() - a.getX()) * t,
                    a.getY() + (b.getY() - a.getY()) * t,
                    a.getZ() + (b.getZ() - a.getZ()) * t
            );
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.LEFT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent interactEvent
                    && interactEvent.getInteractType() == getTriggerType();
        }
    }

    private static Player findTargetPlayer(Player player, int range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                0.75,
                entity -> entity instanceof Player target
                        && !target.getUniqueId().equals(player.getUniqueId())
                        && !target.isDead()
        );
        if (result == null || !(result.getHitEntity() instanceof Player target)) return null;
        return target;
    }
}
