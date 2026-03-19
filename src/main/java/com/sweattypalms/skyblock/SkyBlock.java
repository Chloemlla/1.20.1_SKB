package com.sweattypalms.skyblock;

import com.sweattypalms.skyblock.commands.CommandRegistry;
import com.sweattypalms.skyblock.core.enchants.EnchantManager;
import com.sweattypalms.skyblock.core.items.ItemManager;
import com.sweattypalms.skyblock.core.items.builder.reforges.ReforgeManager;
import com.sweattypalms.skyblock.core.mobs.builder.MobManager;
import com.sweattypalms.skyblock.core.mobs.builder.dragons.DragonManager;
import com.sweattypalms.skyblock.core.player.SkyblockPlayer;
import com.sweattypalms.skyblock.core.world.WorldManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;

import static com.sweattypalms.skyblock.core.items.builder.reforges.ReforgeManager.REFORGES_LIST;


// TODO: Work on skills + loot.
public final class SkyBlock extends JavaPlugin {

    private static SkyBlock instance;

    public static SkyBlock getInstance() {
        return instance;
    }

    public boolean debug = true;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        this.registerServer();

        // Init the plugin asynchronously to speed up
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            registerCommands();
            registerListeners();
            registerCraft();
            configs();

            long end = System.currentTimeMillis() - start;
            logSuccess("Skyblock has been enabled! This took " + ChatColor.YELLOW + end + ChatColor.GREEN + "ms");
        });

        drawAscii();

        Bukkit.getOnlinePlayers().forEach(SkyblockPlayer::newPlayer);
    }

    private void registerServer() {
        WorldManager.init();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, WorldManager::tick, 0L, 1L);

        new DragonManager();
    }

    private void registerCraft() {
        logInfo("Registering items...");
        ItemManager.init();
        logSuccess("Successfully loaded " + ItemManager.ITEMS_LIST.size() + " items.");

        logInfo("Registering mobs...");
        MobManager.init();
        logSuccess("Successfully loaded " + MobManager.MOBS_LIST.size() + " mobs.");

        logInfo("Registering reforges...");
        ReforgeManager.init();
        logSuccess("Successfully loaded " + ReforgeManager.REFORGES_LIST.size() + " reforges.");

        logInfo("Registering enchantments...");
        EnchantManager.init();
        logSuccess("Successfully loaded " + EnchantManager.ENCHANTMENTS.size() + " enchantments.");
    }

    public void registerListeners() {
        logInfo("Registering listeners...");
        long start = System.currentTimeMillis();
        Reflections reflections = new Reflections("com.sweattypalms.skyblock");
        Set<Class<? extends Listener>> listenerClasses = reflections.getSubTypesOf(org.bukkit.event.Listener.class);

        for (Class<? extends org.bukkit.event.Listener> clazz : listenerClasses) {
            try {
                org.bukkit.event.Listener listenerInstance = clazz.getDeclaredConstructor().newInstance();
                Bukkit.getPluginManager().registerEvents(listenerInstance, this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        logInfo("Registered listeners in " + (System.currentTimeMillis() - start) + "ms");
        logSuccess("Successfully registered " + listenerClasses.size() + " listeners.");
    }

    public void registerCommands() {
        logInfo("Registering commands...");
        CommandRegistry commandRegistry = new CommandRegistry();
        commandRegistry.registerAll();
        logSuccess("Successfully registered " + commandRegistry.getCommandsAmt() + " commands.");
    }

    private void configs() {
        File configuration = new File(this.getDataFolder(), "skyblock_config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configuration);
        config.set("ratio", true);
        try {
            config.save(configuration);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawAscii() {
        int width = 220;
        int height = 30;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        Graphics2D graphics2D = (Graphics2D) graphics;
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 24));
        graphics.drawString("SWEATTY", 10, 20);


        getLogger().info("");
        getLogger().info("");
        getLogger().info("");
        for (int y = 0; y < height; y++) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int x = 0; x < width; x++) {
                stringBuilder.append(image.getRGB(x, y) == -16777216 ? " " : "*");
            }
            if (stringBuilder.toString().trim().isEmpty()) {
                continue;
            }
            getLogger().info(stringBuilder.toString());
        }
    }

    @Override
    public void onDisable() {
        logError("Skyblock has been disabled!");
    }

    private void logInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + message);
    }

    private void logSuccess(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + message);
    }

    private void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + message);
    }
}
