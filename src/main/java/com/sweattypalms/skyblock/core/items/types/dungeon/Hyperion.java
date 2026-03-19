package com.sweattypalms.skyblock.core.items.types.dungeon;

import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.events.def.SkyblockPlayerDamageEntityEvent;
import com.sweattypalms.skyblock.core.events.def.SkyblockPlayerDamageEntityEvent.DamageType;
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
import com.sweattypalms.skyblock.core.mobs.builder.ISkyblockMob;
import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import com.sweattypalms.skyblock.core.player.sub.bonus.Bonus;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class Hyperion extends SkyblockItem implements IHasAbility {
    public static final String ID = "hyperion";
    public static final long COOLDOWN = 10L;
    public static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 260.0,
            Stats.STRENGTH, 150.0,
            Stats.INTELLIGENCE, 350.0,
            Stats.FEROCITY, 30.0
    );

    public Hyperion() {
        super(
                ID,
                "Hyperion",
                Material.IRON_SWORD,
                null,
                STATS,
                Rarity.LEGENDARY,
                SkyblockItemType.SWORD
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(new ShadowWarp(), new Implosion(), new WitherShield());
    }

    public static class Implosion implements ITriggerableAbility, IUsageCost, ICooldown {
        @Override
        public String getName() {
            return "Implosion";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Deals $c10,000 $7damage to nearby enemies."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            if (ICooldown.super.isOnCooldown(interactEvent.getSkyblockPlayer())) return;
            ICooldown.super.apply(event);
            ability(interactEvent);
        }

        private void ability(SkyblockInteractEvent event) {
            Player player = event.getSkyblockPlayer().getPlayer();
            World world = player.getLocation().getWorld();
            if (world == null) return;

            double range = 5.0;
            for (Entity entity : world.getNearbyEntities(
                    player.getLocation(),
                    range,
                    range,
                    range,
                    nearby -> nearby instanceof CraftLivingEntity craftLivingEntity
                            && craftLivingEntity.getHandle() instanceof ISkyblockMob
            )) {
                CraftLivingEntity craftLivingEntity = (CraftLivingEntity) entity;

                SkyblockPlayerDamageEntityEvent damageEvent = new SkyblockPlayerDamageEntityEvent(craftLivingEntity, player, DamageType.ABILITY);
                damageEvent.setAbilityItem(event.getSkyblockPlayer().getInventoryManager().getItemInHand());
                damageEvent.setAbility(this);

                Bukkit.getPluginManager().callEvent(damageEvent);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 5f, 1f);
            world.spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    player.getLocation().add(0, 0.5, 0),
                    6,
                    0.0,
                    0.0,
                    0.0,
                    6.0,
                    null,
                    true
            );
        }

        @Override
        public long getCooldown(SkyblockPlayer skyblockPlayer) {
            return COOLDOWN;
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.RIGHT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            if (!(event instanceof SkyblockInteractEvent skyblockInteractEvent)) return false;
            return skyblockInteractEvent.getInteractType() == TriggerType.RIGHT_CLICK;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(Stats.INTELLIGENCE, 300.0);
        }
    }

    public static class ShadowWarp extends AbilityManager.TELEPORT_ABILITY implements ICooldown {
        public ShadowWarp() {
            super(10);
        }

        @Override
        public String getName() {
            return "Shadow Warp";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Creates a space distortion $e10",
                    "$7blocks ahead of you that sucks all",
                    "$7enemies around it. Use this ability",
                    "$7again within $e5$7 seconds to detonate",
                    "$7the warp and deal damage to enemies",
                    "$7near it."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            if (ICooldown.super.isOnCooldown(interactEvent.getSkyblockPlayer())) return;
            ICooldown.super.apply(event);
            super.apply(event);
        }

        @Override
        public long getCooldown(SkyblockPlayer skyblockPlayer) {
            return COOLDOWN;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(Stats.INTELLIGENCE, 300.0);
        }
    }

    public static class WitherShield implements ITriggerableAbility, IUsageCost, ICooldown {
        @Override
        public String getName() {
            return "Wither Shield";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Reduces damage taken by $e30%",
                    "$7for $e5$7 seconds. Also grants",
                    "$7an absorption shield that gives",
                    "$b200%$7 of your Critical Damage",
                    "$7as health, after $e5$7 seconds",
                    "$a50%$7 of the shield is",
                    "$7converted into healing."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            if (ICooldown.super.isOnCooldown(interactEvent.getSkyblockPlayer())) return;
            ICooldown.super.apply(event);
            ability(interactEvent);
        }

        private void ability(SkyblockInteractEvent event) {
            SkyblockPlayer skyblockPlayer = event.getSkyblockPlayer();

            double absorptionAmt = skyblockPlayer.getStatsManager().getMaxStat(Stats.CRIT_DAMAGE) * 1.5;
            skyblockPlayer.getBonusManager().setBonus(
                    "hyperion.wither_shield.absorption",
                    new Bonus(Stats.ABSORPTION, absorptionAmt, 5_000),
                    bonus -> {
                        var statsManager = skyblockPlayer.getStatsManager();
                        double absorption = statsManager.getLiveStat(Stats.ABSORPTION) / 2;
                        double newHealth = Math.max(
                                statsManager.getLiveStat(Stats.HEALTH) + absorption,
                                statsManager.getMaxStat(Stats.HEALTH)
                        );

                        Player player = skyblockPlayer.getPlayer();
                        player.setHealth(newHealth);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                    }
            );
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.RIGHT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            if (!(event instanceof SkyblockInteractEvent skyblockInteractEvent)) return false;
            return skyblockInteractEvent.getInteractType() == TriggerType.RIGHT_CLICK;
        }

        @Override
        public Map<Stats, Double> getCost() {
            return Map.of(Stats.INTELLIGENCE, 150.0);
        }

        @Override
        public long getCooldown(SkyblockPlayer skyblockPlayer) {
            return COOLDOWN;
        }
    }
}
