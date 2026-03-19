package com.sweattypalms.skyblock.core.items.types.end.items;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.events.def.SkyblockPlayerDamageEntityEvent;
import com.sweattypalms.skyblock.core.helpers.MathHelper;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ICooldown;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.IHasAbilityDamage;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.IUsageCost;
import com.sweattypalms.skyblock.core.mobs.builder.ISkyblockMob;
import com.sweattypalms.skyblock.core.mobs.builder.MobAttributes;
import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import net.minecraft.world.entity.EntityLiving;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AspectOfTheDragons extends SkyblockItem implements IHasAbility {
    public static final String ID = "aspect_of_the_dragons";
    private static final Map<Stats, Double> stats = new HashMap<>(Map.of(
            Stats.DAMAGE, 225d,
            Stats.STRENGTH, 100d
    ));

    public AspectOfTheDragons() {
        super(
                ID,
                "Aspect of the Dragons",
                Material.DIAMOND_SWORD,
                null,
                stats,
                Rarity.LEGENDARY,
                SkyblockItemType.SWORD
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new DragonRage(),
                new IceSerpent()
        );
    }

    public static class DragonRage implements ITriggerableAbility, IUsageCost, IHasAbilityDamage {

        @Override
        public String getName() {
            return "Dragon Rage";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7All Monsters in front of you",
                    "$7take $a12,000 $7damage. Hit",
                    "$7monsters take large knockback."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent skyblockInteractEvent)) return;

            SkyblockPlayer skyblockPlayer = skyblockInteractEvent.getSkyblockPlayer();
            Player player = skyblockPlayer.getPlayer();
            MathHelper.spiralParticles(player, 0.1, 1.5, Particle.FLAME);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 10, 1);

            double maxDistance = 4.0;
            double maxKnockback = 4.0;
            double baseKnockback = 1;
            Location playerLoc = player.getLocation();
            Vector viewDirection = playerLoc.getDirection();

            for (Entity nearby : player.getWorld().getNearbyEntities(
                    playerLoc,
                    maxDistance,
                    maxDistance,
                    maxDistance,
                    entity -> entity instanceof LivingEntity livingEntity && !livingEntity.isDead()
            )) {
                LivingEntity entity = (LivingEntity) nearby;
                EntityLiving entityLiving = ((CraftLivingEntity) entity).getHandle();

                if (!(entityLiving instanceof ISkyblockMob skyblockMob)) continue;

                Location mobLoc = entity.getLocation();

                Vector toEntity = mobLoc.toVector().subtract(playerLoc.toVector());
                double angle = Math.toDegrees(viewDirection.angle(toEntity));

                if (angle > 60) continue;


                if (!(boolean) skyblockMob.getSkyblockMob().getAttribute(MobAttributes.KNOCKBACK_RESISTANT)) {
                    // Check if the entity is within 30 degrees of the player's view direction
                    double distance = playerLoc.distance(mobLoc);
                    double knockbackFactor = baseKnockback + maxKnockback * Math.pow((maxDistance - distance) / maxDistance, 2);

                    Vector knockback = toEntity.normalize().multiply(knockbackFactor);
                    knockback.setY(knockbackFactor * 0.2);  // Make the vertical component also dependent on distance

                    entity.setVelocity(knockback);
                }

//                damage
                SkyblockPlayerDamageEntityEvent skyblockPlayerDamageEntityEvent = new SkyblockPlayerDamageEntityEvent(entity, player, SkyblockPlayerDamageEntityEvent.DamageType.ABILITY);
                skyblockPlayerDamageEntityEvent.setAbilityItem(skyblockInteractEvent.getSkyblockPlayer().getInventoryManager().getItemInHand());
                skyblockPlayerDamageEntityEvent.setAbility(this);

                Bukkit.getPluginManager().callEvent(skyblockPlayerDamageEntityEvent);

//                skyblockPlayer.getPlayer().sendMessage(
//                        ChatColor.RED + "You dealt " + ChatColor.GOLD + skyblockPlayerDamageEntityEvent.getDamage() + ChatColor.RED + " damage to " + ChatColor.GOLD + skyblockPlayerDamageEntityEvent.getEntity().getName()
//                );
            }
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.RIGHT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent skyblockInteractEvent && skyblockInteractEvent.getInteractType() == TriggerType.RIGHT_CLICK;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(
                    Stats.INTELLIGENCE, 100d
            );
        }

        @Override
        public double getBaseAbilityDamage() {
            return 12_000;
        }

        @Override
        public double getAbilityScaling() {
            return 0.1;
        }
    }

    public static class IceSerpent implements ITriggerableAbility, IUsageCost, ICooldown {
        private static final int BODY_SEGMENTS = 10;
        private static final long DURATION_TICKS = 20L * 8L;
        private static final long COOLDOWN_MS = 8_000L;
        private static final double MOVE_SPEED = 0.8;

        private static final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
        private static final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();
        private static final Map<UUID, List<Entity>> activeSerpents = new HashMap<>();
        private static final Map<UUID, Map<Bat, Player>> markedPassengers = new HashMap<>();

        @Override
        public String getName() {
            return "Frozen Serpent";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Summon a frozen serpent in front",
                    "$7of you for $a8s$7, dragging nearby",
                    "$7players along its body."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent skyblockInteractEvent)) return;
            if (isOnCooldown(skyblockInteractEvent.getSkyblockPlayer())) return;

            ICooldown.super.apply(event);

            Player caster = skyblockInteractEvent.getSkyblockPlayer().getPlayer();
            UUID casterId = caster.getUniqueId();

            cleanup(casterId);
            summonSerpent(caster);
        }

        private void summonSerpent(Player caster) {
            World world = caster.getWorld();
            Location headLocation = caster.getLocation().clone();
            Vector direction = headLocation.getDirection().normalize();
            headLocation.add(direction.clone().multiply(2));

            List<Entity> serpent = new ArrayList<>();
            Map<Bat, Player> markedBats = new HashMap<>();

            Bat head = world.spawn(headLocation, Bat.class, bat -> {
                bat.setInvisible(true);
                bat.setSilent(true);
                bat.setVelocity(new Vector(0, 0, 0));
            });

            if (!head.addPassenger(caster)) {
                head.remove();
                return;
            }

            serpent.add(head);

            Location bodyLocation = headLocation.clone();
            Vector backward = direction.clone().multiply(-1);
            ItemStack tailItem = new ItemStack(Material.BLUE_ICE);

            for (int i = 1; i <= BODY_SEGMENTS; i++) {
                bodyLocation.add(backward);

                ArmorStand part = world.spawn(bodyLocation, ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setGravity(false);
                    as.setSmall(true);
                    as.setMarker(true);
                    as.getEquipment().setHelmet(tailItem);
                });
                serpent.add(part);

                Bat bat = world.spawn(bodyLocation, Bat.class, spawnedBat -> {
                    spawnedBat.setInvisible(true);
                    spawnedBat.setSilent(true);
                    spawnedBat.setVelocity(new Vector(0, 0, 0));
                });
                serpent.add(bat);
            }

            // Keep original snowman behavior: pull nearby players onto the serpent body.
            for (Entity nearbyEntity : caster.getNearbyEntities(25, 25, 25)) {
                if (!(nearbyEntity instanceof Player nearbyPlayer) || nearbyPlayer.equals(caster)) continue;

                for (Entity entity : serpent) {
                    if (!(entity instanceof Bat bat)) continue;
                    if (bat.equals(head)) continue;
                    if (!bat.getPassengers().isEmpty()) continue;

                    if (bat.addPassenger(nearbyPlayer)) {
                        bat.setAI(false);
                        bat.setVelocity(new Vector(0, 0, 0));
                        markedBats.put(bat, nearbyPlayer);
                    }
                    break;
                }
            }

            UUID casterId = caster.getUniqueId();
            activeSerpents.put(casterId, serpent);
            markedPassengers.put(casterId, markedBats);

            BukkitTask updateTask = Bukkit.getScheduler().runTaskTimer(
                    SkyBlock.getInstance(),
                    () -> updateSerpent(caster, head, serpent, markedBats),
                    0L,
                    1L
            );
            activeTasks.put(casterId, updateTask);

            BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(
                    SkyBlock.getInstance(),
                    () -> cleanup(casterId),
                    DURATION_TICKS
            );
            expiryTasks.put(casterId, expiryTask);

            caster.playSound(caster.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
            caster.playSound(caster.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 1.2f);
        }

        private void updateSerpent(Player caster, Bat head, List<Entity> serpent, Map<Bat, Player> markedBats) {
            if (!caster.isOnline() || !head.isValid() || head.isDead()) {
                cleanup(caster.getUniqueId());
                return;
            }

            Vector playerVector = caster.getLocation().getDirection().normalize().multiply(MOVE_SPEED);
            head.setVelocity(playerVector);

            Location lastLocation = head.getLocation().clone().subtract(0, 0.8, 0);

            for (Entity entity : serpent) {
                if (entity == head || !entity.isValid()) continue;

                if (entity instanceof Bat bat) {
                    bat.teleport(lastLocation);
                    if (markedBats.containsKey(bat)) {
                        Player passenger = markedBats.get(bat);
                        if (passenger != null && passenger.isOnline()) {
                            passenger.leaveVehicle();
                            bat.teleport(lastLocation);
                            passenger.teleport(lastLocation);
                            bat.addPassenger(passenger);
                        }
                    }
                    continue;
                }

                if (entity instanceof ArmorStand armorStand) {
                    Location currentLocation = armorStand.getLocation();
                    Location deltaLocation = currentLocation.clone().subtract(lastLocation);
                    double yaw = -Math.atan2(deltaLocation.getX(), deltaLocation.getZ());
                    double hyp = (deltaLocation.getX() * deltaLocation.getX()) + (deltaLocation.getZ() * deltaLocation.getZ());
                    double pitch = Math.atan2(Math.sqrt(hyp), deltaLocation.getY());

                    armorStand.teleport(lastLocation);
                    armorStand.setHeadPose(new EulerAngle(pitch, 0, 0));
                    armorStand.setRotation((float) Math.toDegrees(yaw), 0);

                    lastLocation = currentLocation;
                }
            }
        }

        private void cleanup(UUID casterId) {
            BukkitTask task = activeTasks.remove(casterId);
            if (task != null) {
                task.cancel();
            }

            BukkitTask expiryTask = expiryTasks.remove(casterId);
            if (expiryTask != null) {
                expiryTask.cancel();
            }

            List<Entity> serpent = activeSerpents.remove(casterId);
            if (serpent != null) {
                serpent.forEach(entity -> {
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                });
            }

            markedPassengers.remove(casterId);
        }

        @Override
        public TriggerType getTriggerType() {
            // Keep RIGHT_CLICK for Dragon Rage; bind serpent to LEFT_CLICK.
            return TriggerType.LEFT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent skyblockInteractEvent
                    && skyblockInteractEvent.getInteractType() == getTriggerType();
        }

        @Override
        public long getCooldown(SkyblockPlayer skyblockPlayer) {
            return COOLDOWN_MS;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(
                    Stats.INTELLIGENCE, 150d
            );
        }
    }

}
