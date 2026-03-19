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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OutlawsBlade extends SkyblockItem implements IHasAbility {
    public static final String ID = "outlaws_blade";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 140d,
            Stats.STRENGTH, 90d
    );

    public OutlawsBlade() {
        super(
                ID,
                "Outlaws Blade",
                Material.DIAMOND_SWORD,
                List.of(
                        "$7Weapon shell for Outlaws skill",
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
                new MoleDrill(),
                new PuppetStrings()
        );
    }

    public static class MoleDrill implements ITriggerableAbility, IHasAbilityDamage {
        private static final int DURATION_TICKS = 12; // source behavior runs 12 ticks, not 12 seconds
        private static final double BLOCK_RADIUS = 2.0;
        private static final double DAMAGE_RADIUS = 5.0;
        private static final Map<UUID, Integer> runningTasks = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "Mole Drill";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Dash forward while tunneling through",
                    "$7blocks and damaging nearby targets."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player caster = interactEvent.getSkyblockPlayer().getPlayer();
            UUID casterId = caster.getUniqueId();

            Integer activeTask = runningTasks.remove(casterId);
            if (activeTask != null) {
                Bukkit.getScheduler().cancelTask(activeTask);
            }

            BukkitRunnable runnable = new BukkitRunnable() {
                private int ticks = 0;

                @Override
                public void run() {
                    if (!caster.isOnline() || caster.isDead() || ticks >= DURATION_TICKS) {
                        runningTasks.remove(casterId);
                        cancel();
                        return;
                    }

                    caster.setVelocity(caster.getEyeLocation().getDirection().normalize().multiply(0.7));
                    carveSphere(caster.getLocation(), (int) BLOCK_RADIUS);
                    damageNearby(caster);

                    ticks++;
                }
            };

            int taskId = runnable.runTaskTimer(SkyBlock.getInstance(), 0L, 1L).getTaskId();
            runningTasks.put(casterId, taskId);
            caster.playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.5f, 0.7f);
        }

        private void carveSphere(Location center, int radius) {
            for (Block block : getSphere(center, radius)) {
                Material type = block.getType();
                if (type == Material.AIR) continue;
                if (type == Material.BEDROCK || type == Material.BARRIER || type == Material.END_PORTAL_FRAME) continue;
                block.setType(Material.AIR);
            }
        }

        private void damageNearby(Player caster) {
            for (Entity nearby : caster.getWorld().getNearbyEntities(
                    caster.getLocation(),
                    DAMAGE_RADIUS,
                    DAMAGE_RADIUS,
                    DAMAGE_RADIUS,
                    entity -> entity instanceof LivingEntity livingEntity
                            && !entity.getUniqueId().equals(caster.getUniqueId())
                            && !livingEntity.isDead()
            )) {

                if (nearby instanceof Player targetPlayer) {
                    targetPlayer.damage(4.0, caster);
                    continue;
                }

                if (!(nearby instanceof LivingEntity livingEntity)) continue;

                SkyblockPlayerDamageEntityEvent damageEvent = new SkyblockPlayerDamageEntityEvent(
                        livingEntity,
                        caster,
                        SkyblockPlayerDamageEntityEvent.DamageType.ABILITY
                );
                damageEvent.setAbility(this);
                damageEvent.setAbilityItem(caster.getInventory().getItemInMainHand());

                Bukkit.getPluginManager().callEvent(damageEvent);
            }
        }

        private List<Block> getSphere(Location location, int radius) {
            List<Block> blocks = new ArrayList<>();
            int x0 = location.getBlockX();
            int y0 = location.getBlockY();
            int z0 = location.getBlockZ();
            int radiusSquared = radius * radius;

            for (int x = x0 - radius; x <= x0 + radius; x++) {
                for (int y = y0 - radius; y <= y0 + radius; y++) {
                    for (int z = z0 - radius; z <= z0 + radius; z++) {
                        int dx = x0 - x;
                        int dz = z0 - z;
                        if ((dx * dx) + (dz * dz) > radiusSquared) continue;
                        blocks.add(location.getWorld().getBlockAt(x, y, z));
                    }
                }
            }

            return blocks;
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
            return 120;
        }

        @Override
        public double getAbilityScaling() {
            return 0.1;
        }
    }

    public static class PuppetStrings implements ITriggerableAbility, IHasAbilityDamage {
        private static final double AFFECT_RADIUS = 4.5;

        @Override
        public String getName() {
            return "Puppet Strings";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Blind and pull nearby targets, then",
                    "$7drop iron blocks and detonate."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player caster = interactEvent.getSkyblockPlayer().getPlayer();

            Location eyeLocation = caster.getEyeLocation();
            Location ringCenter = caster.getLocation().clone().add(0, 6, 0);

            for (LivingEntity victim : inRange(caster, AFFECT_RADIUS)) {
                applyControl(caster, eyeLocation, victim);
            }

            spawnIronRain(caster, ringCenter);
            caster.getWorld().playSound(eyeLocation, Sound.BLOCK_ANVIL_LAND, 1f, 2f);

            Bukkit.getScheduler().runTaskLater(SkyBlock.getInstance(), () -> {
                for (LivingEntity victim : inRange(caster, AFFECT_RADIUS)) {
                    if (victim instanceof Player playerVictim) {
                        playerVictim.damage(2.0, caster);
                        continue;
                    }

                    SkyblockPlayerDamageEntityEvent damageEvent = new SkyblockPlayerDamageEntityEvent(
                            victim,
                            caster,
                            SkyblockPlayerDamageEntityEvent.DamageType.ABILITY
                    );
                    damageEvent.setAbility(this);
                    damageEvent.setAbilityItem(caster.getInventory().getItemInMainHand());
                    Bukkit.getPluginManager().callEvent(damageEvent);
                }

                World world = caster.getWorld();
                Location location = caster.getLocation();
                world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
            }, 7L);
        }

        private void applyControl(Player caster, Location casterEye, LivingEntity victim) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 1));

            Vector velocity = casterEye.toVector().subtract(victim.getEyeLocation().toVector());
            double y = velocity.getY();

            if (Math.abs(y) > 0.4) {
                velocity.setY(0.4 / y);
            }

            double len = velocity.length();
            if (len > 0.9) {
                velocity.multiply(0.9 / len);
            }

            velocity.add(victim.getVelocity());
            victim.setVelocity(velocity.multiply(1.2).add(new Vector(0, 0.7, 0)));

            if (victim instanceof Player playerVictim) {
                playerVictim.playSound(playerVictim.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.4f, 1.4f);
            }
        }

        private void spawnIronRain(Player caster, Location center) {
            World world = caster.getWorld();
            Vector down = new Vector(0, -1, 0);
            int theta = 30;

            for (int i = 0; i < 12; i++) {
                int deg = theta * i;
                double rad = Math.toRadians(deg);

                double x = 3 * Math.cos(rad);
                double z = 3 * Math.sin(rad);

                Location spawn = center.clone().add(x, 0, z);
                FallingBlock block = world.spawnFallingBlock(spawn, Material.IRON_BLOCK.createBlockData());
                block.setDropItem(false);
                block.setHurtEntities(false);
                block.setVelocity(down);

                Bukkit.getScheduler().runTaskLater(SkyBlock.getInstance(), () -> {
                    if (block.isValid()) {
                        if (block.isOnGround()) {
                            block.getLocation().getBlock().setType(Material.AIR);
                        }
                        block.remove();
                    }
                }, 10L);
            }
        }

        private List<LivingEntity> inRange(Player caster, double radius) {
            return caster.getWorld().getNearbyEntities(
                            caster.getLocation(),
                            radius,
                            radius,
                            radius,
                            entity -> entity instanceof LivingEntity living
                                    && !living.getUniqueId().equals(caster.getUniqueId())
                                    && !living.isDead()
                    ).stream()
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .toList();
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

        @Override
        public double getBaseAbilityDamage() {
            return 90;
        }

        @Override
        public double getAbilityScaling() {
            return 0.1;
        }
    }
}
