package com.sweattypalms.skyblock.core.items.types.end.items;

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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Map;

public class BlazeWand extends SkyblockItem implements IHasAbility {
    public static final String ID = "blaze_wand";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 70d,
            Stats.INTELLIGENCE, 260d
    );

    public BlazeWand() {
        super(
                ID,
                "Blaze Wand",
                Material.BLAZE_ROD,
                List.of(
                        "$7Weapon shell for Blaze skill",
                        "$7migration."
                ),
                STATS,
                Rarity.LEGENDARY,
                SkyblockItemType.WAND
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new EndermenWarp()
        );
    }

    public static class EndermenWarp implements ITriggerableAbility, IUsageCost, ICooldown {
        private static final double RANGE = 25.0;
        private static final double RAY_SIZE = 0.75;
        private static final long COOLDOWN_MS = 2_500L;

        @Override
        public String getName() {
            return "Shadow Target";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Warp to a target within $a25 $7blocks",
                    "$7using line-of-sight targeting."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            SkyblockPlayer skyblockPlayer = interactEvent.getSkyblockPlayer();
            if (isOnCooldown(skyblockPlayer)) return;

            Player caster = skyblockPlayer.getPlayer();
            Player target = findTarget(caster);
            if (target == null) return;

            ICooldown.super.apply(event);

            caster.teleport(target.getLocation());
            caster.setFallDistance(0);

            caster.playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
            target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
        }

        private Player findTarget(Player caster) {
            RayTraceResult result = caster.getWorld().rayTraceEntities(
                    caster.getEyeLocation(),
                    caster.getEyeLocation().getDirection(),
                    RANGE,
                    RAY_SIZE,
                    entity -> entity instanceof Player target
                            && !target.getUniqueId().equals(caster.getUniqueId())
                            && !target.isDead()
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
                    Stats.INTELLIGENCE, 70d
            );
        }
    }
}
