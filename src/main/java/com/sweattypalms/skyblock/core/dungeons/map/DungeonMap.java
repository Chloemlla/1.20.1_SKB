package com.sweattypalms.skyblock.core.dungeons.map;

import com.sweattypalms.skyblock.core.dungeons.generation.DungeonPreset;
import com.sweattypalms.skyblock.core.dungeons.generation.LegacyGenerator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.List;

public class DungeonMap {
    private final LegacyGenerator generator;
    private final DungeonPreset preset;

    public DungeonMap(LegacyGenerator generator, DungeonPreset preset) {
        this.generator = generator;
        this.preset = preset;
    }

    public ItemStack getItem(World world) {
        MapView view = Bukkit.createMap(world);
        view.getRenderers().clear();
        view.addRenderer(new DungeonMapRenderer(generator));
        view.setScale(MapView.Scale.FARTHEST);
        view.setTrackingPosition(false);
        view.setCenterX(0);
        view.setCenterZ(0);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        if (mapMeta == null) {
            return mapItem;
        }

        mapMeta.setMapView(view);
        mapMeta.setDisplayName(ChatColor.AQUA + "Dungeon Map " + ChatColor.GRAY + "(" + preset.name() + ")");
        mapMeta.setLore(List.of(
                ChatColor.GRAY + "Generated layout preview",
                ChatColor.DARK_GRAY + "Spawn: Green  Boss: Red"
        ));
        mapItem.setItemMeta(mapMeta);
        return mapItem;
    }
}
