package com.sweattypalms.skyblock.core.mobs.regions.end;

import com.sweattypalms.skyblock.core.helpers.NmsEntityTypes;
import com.sweattypalms.skyblock.core.mobs.builder.ISkyblockMob;
import com.sweattypalms.skyblock.core.mobs.builder.MobAttributes;
import com.sweattypalms.skyblock.core.mobs.builder.SkyblockMob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.IEntityAngerable;
import net.minecraft.world.entity.monster.EntityEnderman;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;

public class VoidlingFanatic extends EntityEnderman implements IEntityAngerable, ISkyblockMob {
    public static final String ID = "voidling_fanatic";
    private final SkyblockMob skyblockMob;


    public VoidlingFanatic(Location location, SkyblockMob skyblockMob) {
        super(NmsEntityTypes.byKey("minecraft", "enderman"), ((CraftWorld) location.getWorld()).getHandle());
        this.skyblockMob = skyblockMob;
        this.skyblockMob
                .setMaxHealth(750_000)
                .setDamage(3_500)
                .setCustomName("$cVoidling Fanatic")
                .setAttribute(MobAttributes.KNOCKBACK_RESISTANT, true)
                .setLevel(85);
    }

    @Override
    public boolean a(DamageSource damagesource, float f) {
        if (this.b(damagesource)) {
            return false;
        } else {
            boolean flag = super.a(damagesource, f);

            // Force an update to clients
            this.cH().a(this, (byte) 53);

            return flag;
        }
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
