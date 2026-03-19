package com.sweattypalms.skyblock.core.mobs.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;

public interface VolatileAIHandler {

    void setTarget(LivingEntity entity, LivingEntity target);

    void addPathfinderGoals(LivingEntity entity, List<String> aiMods);

    void addTargetGoals(LivingEntity entity, List<String> aiMods);

    default void navigateToLocation(LivingEntity entity, Location destination, double speedModifier) {
    }

    default void setPathfindingMalus(LivingEntity entity, String malus, float value) {
    }
}

