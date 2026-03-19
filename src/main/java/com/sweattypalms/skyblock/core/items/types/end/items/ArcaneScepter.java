package com.sweattypalms.skyblock.core.items.types.end.items;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ICooldown;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.IUsageCost;
import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArcaneScepter extends SkyblockItem implements IHasAbility {
    public static final String ID = "arcane_scepter";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 45d,
            Stats.INTELLIGENCE, 320d
    );

    public ArcaneScepter() {
        super(
                ID,
                "Arcane Scepter",
                Material.BLAZE_ROD,
                List.of(
                        "$7Mage weapon shell for",
                        "$7upcoming skill migration."
                ),
                STATS,
                Rarity.LEGENDARY,
                SkyblockItemType.WAND
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new RotatingArmorStandsAbility()
        );
    }

    public static class RotatingArmorStandsAbility implements ITriggerableAbility, IUsageCost, ICooldown {
        private static final int ARMOR_STAND_COUNT = 5;
        private static final double ORBIT_RADIUS = 1.0;
        private static final double ROTATION_SPEED = 5.0;
        private static final double LAUNCH_SPEED = 2.0;
        private static final double MAX_DISTANCE = 10.0;
        private static final double STEP_DISTANCE = 0.5;
        private static final long STEP_INTERVAL = 1L;
        private static final long RETURN_DELAY_TICKS = 20L;
        private static final long COOLDOWN_MS = 5_000L;

        private static final Map<UUID, OrbitRuntime> runtimes = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "Arcane Orbit";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Right click to summon rotating orbs,",
                    "$7then right click again to launch one.",
                    "$7Launched orbs return to your orbit."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            SkyblockPlayer skyblockPlayer = interactEvent.getSkyblockPlayer();
            if (isOnCooldown(skyblockPlayer)) return;
            ICooldown.super.apply(event);

            Player player = skyblockPlayer.getPlayer();
            UUID playerId = player.getUniqueId();
            OrbitRuntime runtime = runtimes.computeIfAbsent(playerId, ignored -> new OrbitRuntime());
            compactRuntime(runtime);

            if (runtime.armorStands.isEmpty()) {
                resetRuntime(runtime);
                createArmorStands(player, runtime);
                startRotation(playerId, player, runtime);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                return;
            }

            if (runtime.hasAnyLaunched()) {
                player.sendMessage(ChatColor.YELLOW + "Arcane shards are still returning...");
                return;
            }

            boolean launched = launchOneArmorStand(playerId, player, runtime);
            if (!launched) {
                resetRuntime(runtime);
                createArmorStands(player, runtime);
                startRotation(playerId, player, runtime);
            }
        }

        private void createArmorStands(Player player, OrbitRuntime runtime) {
            World world = player.getWorld();
            for (int i = 0; i < ARMOR_STAND_COUNT; i++) {
                final int index = i;
                ArmorStand armorStand = world.spawn(player.getLocation(), ArmorStand.class, stand -> {
                    stand.setVisible(true);
                    stand.setSmall(true);
                    stand.setGravity(true);
                    stand.setCollidable(false);
                    stand.setInvulnerable(true);
                    stand.setCustomName("ArcaneShard_" + index);
                    stand.setCustomNameVisible(false);
                });

                runtime.armorStands.add(armorStand);
                runtime.launchedByStand.put(armorStand.getUniqueId(), false);
            }
        }

        private void startRotation(UUID playerId, Player player, OrbitRuntime runtime) {
            cancelTask(runtime.rotationTaskId);

            BukkitTask task = SkyBlock.getInstance().getServer().getScheduler().runTaskTimer(
                    SkyBlock.getInstance(),
                    () -> {
                        if (!player.isOnline() || player.isDead()) {
                            cleanupRuntime(playerId);
                            return;
                        }

                        compactRuntime(runtime);
                        if (runtime.armorStands.isEmpty()) {
                            cleanupRuntime(playerId);
                            return;
                        }

                        Location baseLocation = getBehindPlayerLocation(player);
                        double angleIncrement = 360.0 / runtime.armorStands.size();

                        for (int i = 0; i < runtime.armorStands.size(); i++) {
                            ArmorStand armorStand = runtime.armorStands.get(i);
                            if (armorStand.isDead()) continue;
                            if (runtime.launchedByStand.getOrDefault(armorStand.getUniqueId(), false)) continue;

                            double currentAngle = runtime.angle + (i * angleIncrement);
                            Location orbitLocation = calculateVerticalOrbitLocation(baseLocation, currentAngle);
                            armorStand.teleport(orbitLocation);
                        }

                        runtime.angle += ROTATION_SPEED;
                        if (runtime.angle >= 360.0) {
                            runtime.angle -= 360.0;
                        }
                    },
                    0L,
                    1L
            );

            runtime.rotationTaskId = task.getTaskId();
        }

        private boolean launchOneArmorStand(UUID playerId, Player player, OrbitRuntime runtime) {
            for (ArmorStand armorStand : runtime.armorStands) {
                if (armorStand.isDead()) continue;

                UUID standId = armorStand.getUniqueId();
                if (runtime.launchedByStand.getOrDefault(standId, false)) continue;

                Vector direction = player.getEyeLocation().getDirection().clone();
                direction.setY(0);
                if (direction.lengthSquared() < 0.0001) {
                    direction = player.getLocation().getDirection().clone().setY(0);
                }
                if (direction.lengthSquared() < 0.0001) {
                    direction = new Vector(1, 0, 0);
                }
                direction.normalize();

                runtime.launchedByStand.put(standId, true);
                startStepMovement(playerId, runtime, armorStand, player, direction);

                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

                SkyBlock.getInstance().getServer().getScheduler().runTaskLater(SkyBlock.getInstance(), () -> {
                    if (!armorStand.isDead() && armorStand.isValid()) {
                        startReturnTask(playerId, runtime, armorStand, player);
                    }
                }, RETURN_DELAY_TICKS);

                return true;
            }
            return false;
        }

        private void startStepMovement(UUID playerId, OrbitRuntime runtime, ArmorStand armorStand, Player player, Vector direction) {
            if (armorStand.isDead() || !armorStand.isValid()) return;
            if (!player.isOnline() || player.isDead()) return;

            UUID standId = armorStand.getUniqueId();
            cancelTask(runtime.movementTaskIds.remove(standId));

            BukkitTask task = SkyBlock.getInstance().getServer().getScheduler().runTaskLater(
                    SkyBlock.getInstance(),
                    () -> {
                        if (!runtimes.containsKey(playerId)) return;
                        if (armorStand.isDead() || !armorStand.isValid() || !player.isOnline() || player.isDead()) {
                            runtime.movementTaskIds.remove(standId);
                            return;
                        }

                        Location next = armorStand.getLocation().clone().add(direction.clone().multiply(STEP_DISTANCE));
                        if (!isLocationSafe(next)) {
                            runtime.movementTaskIds.remove(standId);
                            startReturnTask(playerId, runtime, armorStand, player);
                            return;
                        }

                        armorStand.teleport(next);
                        runtime.movementTaskIds.remove(standId);
                        startStepMovement(playerId, runtime, armorStand, player, direction);
                    },
                    STEP_INTERVAL
            );

            runtime.movementTaskIds.put(standId, task.getTaskId());
        }

        private void startReturnTask(UUID playerId, OrbitRuntime runtime, ArmorStand armorStand, Player player) {
            if (armorStand.isDead() || !armorStand.isValid()) return;
            if (!player.isOnline() || player.isDead()) return;

            UUID standId = armorStand.getUniqueId();
            cancelTask(runtime.returnTaskIds.remove(standId));

            BukkitTask task = SkyBlock.getInstance().getServer().getScheduler().runTaskLater(
                    SkyBlock.getInstance(),
                    () -> {
                        if (!runtimes.containsKey(playerId)) return;
                        if (armorStand.isDead() || !armorStand.isValid() || !player.isOnline() || player.isDead()) {
                            runtime.returnTaskIds.remove(standId);
                            return;
                        }

                        Location playerLocation = player.getLocation();
                        Location standLocation = armorStand.getLocation();
                        double distance = standLocation.distance(playerLocation);

                        if (distance > MAX_DISTANCE) {
                            Vector toPlayer = playerLocation.toVector().subtract(standLocation.toVector()).normalize();
                            armorStand.setVelocity(toPlayer.multiply(LAUNCH_SPEED * 0.5));
                            runtime.returnTaskIds.remove(standId);
                            startReturnTask(playerId, runtime, armorStand, player);
                            return;
                        }

                        armorStand.setVelocity(new Vector(0, 0, 0));
                        returnToRotation(armorStand, player, runtime);
                        runtime.launchedByStand.put(standId, false);
                        runtime.returnTaskIds.remove(standId);
                    },
                    STEP_INTERVAL
            );

            runtime.returnTaskIds.put(standId, task.getTaskId());
        }

        private void returnToRotation(ArmorStand armorStand, Player player, OrbitRuntime runtime) {
            int index = runtime.armorStands.indexOf(armorStand);
            if (index < 0) return;

            double angleIncrement = 360.0 / runtime.armorStands.size();
            double currentAngle = index * angleIncrement;

            Location baseLocation = getBehindPlayerLocation(player);
            Location orbitLocation = calculateVerticalOrbitLocation(baseLocation, currentAngle);
            armorStand.teleport(orbitLocation);

            if ((runtime.rotationTaskId == -1 || !isTaskRunning(runtime.rotationTaskId)) && player.isOnline() && !player.isDead()) {
                startRotation(player.getUniqueId(), player, runtime);
            }
        }

        private Location getBehindPlayerLocation(Player player) {
            Location playerLocation = player.getLocation();
            Vector behindDirection = playerLocation.getDirection().clone().multiply(-1).normalize().multiply(1.5);
            return playerLocation.clone().add(behindDirection).add(0, 1.0, 0);
        }

        private Location calculateVerticalOrbitLocation(Location baseLocation, double angle) {
            double radians = Math.toRadians(angle);
            double yOffset = ORBIT_RADIUS * Math.cos(radians);
            double zOffset = ORBIT_RADIUS * Math.sin(radians);

            Vector offsetVector = new Vector(0, yOffset, zOffset);
            double playerYawRadians = Math.toRadians(baseLocation.getYaw());
            Vector rotatedOffset = rotateAroundAxisY(offsetVector, playerYawRadians);

            return baseLocation.clone().add(rotatedOffset);
        }

        private Vector rotateAroundAxisY(Vector vector, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = vector.getX() * cos + vector.getZ() * sin;
            double z = vector.getX() * -sin + vector.getZ() * cos;
            return vector.setX(x).setZ(z);
        }

        private boolean isLocationSafe(Location location) {
            Block block = location.getBlock();
            return !block.getType().isSolid();
        }

        private void compactRuntime(OrbitRuntime runtime) {
            runtime.armorStands.removeIf(armorStand -> armorStand == null || armorStand.isDead() || !armorStand.isValid());

            runtime.launchedByStand.entrySet().removeIf(entry -> runtime.armorStands.stream()
                    .noneMatch(stand -> stand.getUniqueId().equals(entry.getKey())));

            runtime.movementTaskIds.entrySet().removeIf(entry -> runtime.armorStands.stream()
                    .noneMatch(stand -> stand.getUniqueId().equals(entry.getKey())));

            runtime.returnTaskIds.entrySet().removeIf(entry -> runtime.armorStands.stream()
                    .noneMatch(stand -> stand.getUniqueId().equals(entry.getKey())));
        }

        private void cleanupRuntime(UUID playerId) {
            OrbitRuntime runtime = runtimes.remove(playerId);
            if (runtime == null) return;
            resetRuntime(runtime);
        }

        private void resetRuntime(OrbitRuntime runtime) {
            cancelTask(runtime.rotationTaskId);
            runtime.rotationTaskId = -1;
            runtime.angle = 0.0;

            for (Integer taskId : runtime.movementTaskIds.values()) {
                cancelTask(taskId);
            }
            runtime.movementTaskIds.clear();

            for (Integer taskId : runtime.returnTaskIds.values()) {
                cancelTask(taskId);
            }
            runtime.returnTaskIds.clear();

            Iterator<ArmorStand> iterator = runtime.armorStands.iterator();
            while (iterator.hasNext()) {
                ArmorStand armorStand = iterator.next();
                if (armorStand != null && armorStand.isValid() && !armorStand.isDead()) {
                    armorStand.remove();
                }
                iterator.remove();
            }

            runtime.launchedByStand.clear();
        }

        private void cancelTask(Integer taskId) {
            if (taskId == null || taskId <= 0) return;
            SkyBlock.getInstance().getServer().getScheduler().cancelTask(taskId);
        }

        private boolean isTaskRunning(int taskId) {
            if (taskId <= 0) return false;
            return SkyBlock.getInstance().getServer().getScheduler().isQueued(taskId)
                    || SkyBlock.getInstance().getServer().getScheduler().isCurrentlyRunning(taskId);
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
        public long getCooldown(SkyblockPlayer skyblockPlayer) {
            return COOLDOWN_MS;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(
                    Stats.INTELLIGENCE, 80d
            );
        }

        private static final class OrbitRuntime {
            private final List<ArmorStand> armorStands = new ArrayList<>();
            private final Map<UUID, Boolean> launchedByStand = new ConcurrentHashMap<>();
            private final Map<UUID, Integer> movementTaskIds = new ConcurrentHashMap<>();
            private final Map<UUID, Integer> returnTaskIds = new ConcurrentHashMap<>();
            private int rotationTaskId = -1;
            private double angle = 0.0;

            private boolean hasAnyLaunched() {
                return launchedByStand.values().stream().anyMatch(Boolean::booleanValue);
            }
        }
    }
}
