package com.sweattypalms.skyblock.core.mobs.regions.graveyard;

import com.sweattypalms.skyblock.core.mobs.builder.IRegionEntity;
import com.sweattypalms.skyblock.core.mobs.builder.ISkyblockMob;
import com.sweattypalms.skyblock.core.mobs.builder.MobAttributes;
import com.sweattypalms.skyblock.core.regions.Regions;
import com.sweattypalms.skyblock.core.mobs.builder.SkyblockMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.monster.EntityZombie;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SmallFireball;
import org.bukkit.util.Vector;

import java.util.List;

public class GraveyardZombie extends EntityZombie implements ISkyblockMob, IRegionEntity {

    public static final String ID = "graveyard_zombie";

    private final SkyblockMob skyblockMob;
    private boolean fireballNext;

    public GraveyardZombie(Location location, SkyblockMob skyblockMob) {
        super(((CraftWorld) location.getWorld()).getHandle());
        this.skyblockMob = skyblockMob;
        this.fireballNext = false;
        this.skyblockMob
                .setMaxHealth(100)
                .setDamage(20)
                .setCustomName("$cGraveyard Zombie")
                .setLevel(1)
                .setAttribute(MobAttributes.COMBAT_XP, 40d)
                .setAIGoalSelectors(List.of(
                        "clear",
                        "0 float",
                        "2 meleeAttack 1.0 true",
                        "7 randomStroll 1.0",
                        "8 lookAtPlayer 8.0",
                        "8 randomLookAround"
                ))
                .setAITargetSelectors(List.of(
                        "clear",
                        "1 hurtByTarget",
                        "2 nearestPlayer true"
                ))
        ;
    }

    @Override
    public boolean z(Entity entity) {
        if (!(entity instanceof EntityLiving livingTarget)) {
            return super.z(entity);
        }

        if (!fireballNext) {
            fireballNext = true;
            return super.z(entity);
        }

        fireballNext = false;
        launchFireball(livingTarget);
        return true;
    }

    private void launchFireball(EntityLiving target) {
        if (!(this.getBukkitEntity() instanceof LivingEntity shooter)) {
            return;
        }
        if (!(target.getBukkitEntity() instanceof LivingEntity targetEntity)) {
            return;
        }

        Location spawn = shooter.getEyeLocation().add(shooter.getLocation().getDirection().multiply(0.4));
        Vector direction = targetEntity.getEyeLocation().toVector().subtract(spawn.toVector()).normalize();

        SmallFireball fireball = shooter.getWorld().spawn(spawn, SmallFireball.class, projectile -> {
            projectile.setShooter(shooter);
            projectile.setYield(0F);
            projectile.setIsIncendiary(false);
            projectile.setDirection(direction);
        });
        fireball.setVelocity(direction.multiply(0.8));
    }

    @Override
    public Regions getRegion() {
        return Regions.GRAVEYARD;
    }


    @Override
    public SkyblockMob getSkyblockMob() {
        return skyblockMob;
    }

    @Override
    public EntityLiving getEntityInstance() {
        return this;
    }
}
