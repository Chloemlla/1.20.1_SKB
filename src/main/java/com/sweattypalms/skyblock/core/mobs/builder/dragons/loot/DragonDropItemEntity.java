package com.sweattypalms.skyblock.core.mobs.builder.dragons.loot;

import com.sweattypalms.skyblock.core.helpers.PlaceholderFormatter;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import net.minecraft.world.entity.item.EntityItem;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

public class DragonDropItemEntity extends EntityItem implements IDragonLoot {

    private final SkyblockItem dropItem;
    private final SkyblockPlayer dropOwner;

    private ArmorStand nameTag;
    private int pickupCheckTicks = 0;

    public DragonDropItemEntity(Player owner, Location location, SkyblockItem dropItem) {
        super(
                ((CraftWorld) location.getWorld()).getHandle(),
                location.getX(),
                location.getY(),
                location.getZ(),
                CraftItemStack.asNMSCopy(dropItem.toItemStack())
        );

        this.dropItem = dropItem;
        this.dropOwner = SkyblockPlayer.getSkyblockPlayer(owner);

        ((CraftWorld) location.getWorld()).getHandle().addFreshEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        spawnNametag(location);
    }

    private void spawnNametag(Location location) {
        ItemStack itemStack = dropItem.toItemStack();
        String displayName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName() : itemStack.getType().name();
        if (itemStack.getAmount() > 1) {
            displayName += " §8" + itemStack.getAmount() + "x";
        }

        String finalDisplayName = displayName;
        this.nameTag = location.getWorld().spawn(location.clone().add(0, -1.1, 0), ArmorStand.class, armorStand -> {
            armorStand.setCustomName(finalDisplayName);
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setMarker(true);
            armorStand.setInvulnerable(true);
            armorStand.setCollidable(false);
        });
    }

    private void cleanupNameTag() {
        if (this.nameTag != null && !this.nameTag.isDead()) {
            this.nameTag.remove();
        }
        this.nameTag = null;
    }

    @Override
    public SkyblockItem getDropItem() {
        return dropItem;
    }

    @Override
    public SkyblockPlayer getDropOwner() {
        return dropOwner;
    }

    @Override
    public void l() {
        super.l();

        if (this.nameTag != null && !this.nameTag.isDead()) {
            Location tagLocation = this.getBukkitEntity().getLocation().clone().add(0, -1.1, 0);
            this.nameTag.teleport(tagLocation);
        }

        pickupCheckTicks++;
        if (pickupCheckTicks % 20 != 0) {
            return;
        }

        Player owner = this.getDropOwner().getPlayer();
        if (owner == null || !owner.isOnline()) {
            this.b(RemovalReason.b);
            return;
        }

        Location currentLocation = this.getBukkitEntity().getLocation();
        if (currentLocation.getWorld() == null || !owner.getWorld().equals(currentLocation.getWorld())) {
            return;
        }

        if (currentLocation.distanceSquared(owner.getLocation()) > 4) {
            return;
        }

        this.getDropOwner().getInventoryManager().addItem(this.dropItem);

        String ownerDisplayName = owner.getDisplayName();
        String message = PlaceholderFormatter.format(ownerDisplayName + "$e has obtained " + this.dropItem.f_getDisplayName() + ChatColor.RESET + " $e!");

        currentLocation.getWorld().getPlayers().forEach(_player -> _player.sendMessage(message));

        this.b(RemovalReason.b); // DISCARDED
    }
}

