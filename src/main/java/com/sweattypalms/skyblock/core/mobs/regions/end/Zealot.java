package com.sweattypalms.skyblock.core.mobs.regions.end;

import com.sweattypalms.skyblock.core.mobs.builder.ISkyblockMob;
import com.sweattypalms.skyblock.core.mobs.builder.SkyblockMob;
import com.sweattypalms.skyblock.core.helpers.NmsEntityTypes;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.monster.EntityEnderman;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;

public class Zealot extends EntityEnderman implements ISkyblockMob {

    public static final String ID = "zealot";
    private final SkyblockMob skyblockMob;

    public Zealot(Location location, SkyblockMob skyblockMob) {
        super(NmsEntityTypes.byKey("minecraft", "enderman"), ((CraftWorld) location.getWorld()).getHandle());
        this.skyblockMob = skyblockMob;
        this.skyblockMob
                .setMaxHealth(13_000)
                .setDamage(1_250)
                .setCustomName("$cZealot")
                .setLevel(55);
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
