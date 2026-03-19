package com.sweattypalms.skyblock.core.mobs.ai.v1_20_R1;

import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.mobs.ai.VolatileAIHandler;
import net.minecraft.world.entity.EntityCreature;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreakDoor;
import net.minecraft.world.entity.ai.goal.PathfinderGoalDoorOpen;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMoveTowardsTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStroll;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSelector;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.pathfinder.PathType;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftLivingEntity;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class VolatileAIHandlerImpl implements VolatileAIHandler {

    private final Map<String, GoalFactory> aiGoals = new HashMap<>();
    private final Map<String, GoalFactory> aiTargets = new HashMap<>();

    private final Field goalSelectorField;
    private final Field targetSelectorField;
    private final Field selectorAvailableGoalsField;
    private final Field selectorLockedFlagsField;

    public VolatileAIHandlerImpl() {
        this.goalSelectorField = resolveInsentientField("bO", 0);
        this.targetSelectorField = resolveInsentientField("bP", 1);
        this.selectorAvailableGoalsField = resolveSelectorField("c", Map.class, 0);
        this.selectorLockedFlagsField = resolveSelectorField("d", Set.class, 0);

        registerDefaultGoals();
        registerDefaultTargets();
    }

    @Override
    public void setTarget(LivingEntity entity, LivingEntity target) {
        if (entity == null) {
            return;
        }

        if (entity instanceof Creature creature) {
            creature.setTarget(target);
        }

        EntityInsentient mob = toInsentient(entity);
        if (mob == null) {
            return;
        }

        mob.setTarget(target == null ? null : ((CraftLivingEntity) target).getHandle(), EntityTargetEvent.TargetReason.CUSTOM, true);
    }

    @Override
    public void addPathfinderGoals(LivingEntity entity, List<String> aiMods) {
        applySelectors(entity, aiMods, true);
    }

    @Override
    public void addTargetGoals(LivingEntity entity, List<String> aiMods) {
        applySelectors(entity, aiMods, false);
    }

    @Override
    public void navigateToLocation(LivingEntity entity, Location destination, double speedModifier) {
        if (entity == null || destination == null) {
            return;
        }
        EntityInsentient insentient = toInsentient(entity);
        if (insentient == null) {
            return;
        }
        insentient.J().a(destination.getX(), destination.getY(), destination.getZ(), speedModifier);
    }

    @Override
    public void setPathfindingMalus(LivingEntity entity, String malus, float value) {
        if (entity == null || malus == null || malus.isBlank()) {
            return;
        }
        EntityInsentient insentient = toInsentient(entity);
        if (insentient == null) {
            return;
        }

        try {
            PathType pathType = PathType.valueOf(malus.toUpperCase(Locale.ROOT));
            insentient.a(pathType, value);
        } catch (IllegalArgumentException ex) {
            SkyBlock.getInstance().getLogger().warning("Invalid PathType in AIPathfindingMalus: " + malus);
        }
    }

    public void clearPathfinderGoals(LivingEntity entity) {
        clearSelector(entity, true);
    }

    public void clearPathfinderTargets(LivingEntity entity) {
        clearSelector(entity, false);
    }

    public void registerGoal(String name, GoalFactory factory) {
        if (name == null || name.isBlank() || factory == null) {
            return;
        }
        aiGoals.put(name.toLowerCase(Locale.ROOT), factory);
    }

    public void registerTarget(String name, GoalFactory factory) {
        if (name == null || name.isBlank() || factory == null) {
            return;
        }
        aiTargets.put(name.toLowerCase(Locale.ROOT), factory);
    }

    private void registerDefaultGoals() {
        registerGoal("float", (mob, args) -> new PathfinderGoalFloat(mob));
        registerGoal("meleeattack", (mob, args) -> new PathfinderGoalMeleeAttack(
                asCreature(mob),
                parseDouble(args, 0, 1.0),
                parseBoolean(args, 1, true)
        ));
        registerGoal("randomstroll", (mob, args) -> new PathfinderGoalRandomStroll(
                asCreature(mob),
                parseDouble(args, 0, 1.0)
        ));
        registerGoal("randomlookaround", (mob, args) -> new PathfinderGoalRandomLookaround(mob));
        registerGoal("lookatplayer", (mob, args) -> new PathfinderGoalLookAtPlayer(
                mob,
                EntityHuman.class,
                (float) parseDouble(args, 0, 8.0)
        ));
        registerGoal("movetowardstarget", (mob, args) -> new PathfinderGoalMoveTowardsTarget(
                asCreature(mob),
                parseDouble(args, 0, 1.0),
                (float) parseDouble(args, 1, 24.0)
        ));
        registerGoal("panic", (mob, args) -> new PathfinderGoalPanic(
                asCreature(mob),
                parseDouble(args, 0, 1.2)
        ));
        registerGoal("opendoor", (mob, args) -> new PathfinderGoalDoorOpen(mob, parseBoolean(args, 0, true)));
        registerGoal("breakdoor", (mob, args) -> new PathfinderGoalBreakDoor(mob, d -> true));
    }

    private void registerDefaultTargets() {
        registerTarget("hurtbytarget", (mob, args) -> new PathfinderGoalHurtByTarget(asCreature(mob)));
        registerTarget("nearestplayer", (mob, args) -> new PathfinderGoalNearestAttackableTarget<>(mob, EntityHuman.class, parseBoolean(args, 0, true)));
        registerTarget("players", aiTargets.get("nearestplayer"));
        registerTarget("nearestattackableplayer", aiTargets.get("nearestplayer"));
    }

    private void applySelectors(LivingEntity entity, List<String> aiMods, boolean goalSelector) {
        EntityInsentient mob = toInsentient(entity);
        if (mob == null || aiMods == null || aiMods.isEmpty()) {
            return;
        }

        PathfinderGoalSelector selector = getSelector(mob, goalSelector);
        if (selector == null) {
            return;
        }

        for (String line : aiMods) {
            if (line == null || line.isBlank()) {
                continue;
            }
            SelectorLine selectorLine = SelectorLine.parse(line);
            if (selectorLine == null) {
                continue;
            }

            if ("clear".equals(selectorLine.name()) || "reset".equals(selectorLine.name())) {
                clearSelector(selector);
                continue;
            }

            GoalFactory factory = (goalSelector ? aiGoals : aiTargets).get(selectorLine.name());
            if (factory == null) {
                SkyBlock.getInstance().getLogger().warning("Unknown AI selector key: " + selectorLine.name());
                continue;
            }

            try {
                PathfinderGoal pathfinderGoal = factory.create(mob, selectorLine.args());
                if (pathfinderGoal != null) {
                    selector.a(selectorLine.priority(), pathfinderGoal);
                }
            } catch (Exception ex) {
                SkyBlock.getInstance().getLogger().log(Level.WARNING, "Failed applying AI selector: " + line, ex);
            }
        }
    }

    private void clearSelector(LivingEntity entity, boolean goalSelector) {
        EntityInsentient mob = toInsentient(entity);
        if (mob == null) {
            return;
        }
        PathfinderGoalSelector selector = getSelector(mob, goalSelector);
        if (selector != null) {
            clearSelector(selector);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clearSelector(PathfinderGoalSelector selector) {
        try {
            Map availableGoals = (Map) selectorAvailableGoalsField.get(selector);
            Set lockedFlags = (Set) selectorLockedFlagsField.get(selector);
            availableGoals.clear();
            lockedFlags.clear();
        } catch (IllegalAccessException ex) {
            SkyBlock.getInstance().getLogger().log(Level.WARNING, "Failed clearing pathfinder selector", ex);
        }
    }

    private PathfinderGoalSelector getSelector(EntityInsentient insentient, boolean goalSelector) {
        try {
            return (PathfinderGoalSelector) (goalSelector ? goalSelectorField.get(insentient) : targetSelectorField.get(insentient));
        } catch (IllegalAccessException ex) {
            SkyBlock.getInstance().getLogger().log(Level.WARNING, "Failed accessing pathfinder selector", ex);
            return null;
        }
    }

    private EntityInsentient toInsentient(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        EntityLiving handle = ((CraftLivingEntity) entity).getHandle();
        if (handle instanceof EntityInsentient insentient) {
            return insentient;
        }
        return null;
    }

    private Field resolveInsentientField(String obfName, int selectorIndex) {
        Field field = findField(EntityInsentient.class, obfName);
        if (field != null && PathfinderGoalSelector.class.isAssignableFrom(field.getType())) {
            return field;
        }
        List<Field> selectors = fieldsByType(EntityInsentient.class, PathfinderGoalSelector.class);
        if (selectors.size() > selectorIndex) {
            Field resolved = selectors.get(selectorIndex);
            resolved.setAccessible(true);
            return resolved;
        }
        throw new IllegalStateException("Unable to resolve EntityInsentient selector field");
    }

    private Field resolveSelectorField(String obfName, Class<?> expectedType, int index) {
        Field field = findField(PathfinderGoalSelector.class, obfName);
        if (field != null && expectedType.isAssignableFrom(field.getType())) {
            return field;
        }

        List<Field> fields = fieldsByType(PathfinderGoalSelector.class, expectedType);
        if (fields.size() > index) {
            Field resolved = fields.get(index);
            resolved.setAccessible(true);
            return resolved;
        }

        throw new IllegalStateException("Unable to resolve PathfinderGoalSelector internal field for " + expectedType.getSimpleName());
    }

    private Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private List<Field> fieldsByType(Class<?> owner, Class<?> type) {
        return Arrays.stream(owner.getDeclaredFields())
                .filter(field -> type.isAssignableFrom(field.getType()))
                .peek(field -> field.setAccessible(true))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private EntityCreature asCreature(EntityInsentient entity) {
        if (entity instanceof EntityCreature creature) {
            return creature;
        }
        throw new IllegalArgumentException("Entity is not EntityCreature: " + entity.getClass().getSimpleName());
    }

    private static boolean parseBoolean(String[] args, int index, boolean defaultValue) {
        if (args == null || index >= args.length) {
            return defaultValue;
        }
        return Boolean.parseBoolean(args[index]);
    }

    private static double parseDouble(String[] args, int index, double defaultValue) {
        if (args == null || index >= args.length) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @FunctionalInterface
    public interface GoalFactory {
        PathfinderGoal create(EntityInsentient entity, String[] args);
    }

    private record SelectorLine(int priority, String name, String[] args) {
        static SelectorLine parse(String source) {
            String line = source == null ? "" : source.trim();
            if (line.isEmpty()) {
                return null;
            }

            String[] split = line.split("\\s+");
            if (split.length == 0) {
                return null;
            }

            int priority = 0;
            int nameIndex = 0;
            if (isInteger(split[0])) {
                priority = Integer.parseInt(split[0]);
                nameIndex = 1;
            }
            if (split.length <= nameIndex) {
                return null;
            }

            String name = split[nameIndex].toLowerCase(Locale.ROOT);
            int argsFrom = nameIndex + 1;
            String[] args = argsFrom < split.length ? java.util.Arrays.copyOfRange(split, argsFrom, split.length) : new String[0];
            return new SelectorLine(priority, name, args);
        }

        static boolean isInteger(String value) {
            if (value == null || value.isEmpty()) {
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                if (!Character.isDigit(value.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
