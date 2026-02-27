package com.sweattypalms.skyblock.core.mobs.regions.test;

import com.sweattypalms.skyblock.core.mobs.builder.ISkyblockMob;
import com.sweattypalms.skyblock.core.mobs.builder.MobAttributes;
import com.sweattypalms.skyblock.core.mobs.builder.SkyblockMob;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.monster.EntityZombie;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;


public class TestEntity extends EntityZombie implements ISkyblockMob {
    public static final String ID = "test_entity";

    private final SkyblockMob skyblockMob;

    public TestEntity(Location location, SkyblockMob skyblockMob) {
        super(((CraftWorld) location.getWorld()).getHandle());
        this.skyblockMob = skyblockMob;
        this.skyblockMob
                .setMaxHealth(100_000_000)
                .setCustomName("$cFlorida man")
                .setLevel(999)
//                .setLoot(new MobLoot().addConfirmedDrop(SkyblockItem.get()))
                 .setAttribute(MobAttributes.COMBAT_XP, 50d);
                ;
        skyblockMob.setAttribute(MobAttributes.AI_ENABLED, false);
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
