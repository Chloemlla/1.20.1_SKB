package com.sweattypalms.skyblock.commands.handlers;

import com.sweattypalms.skyblock.commands.Command;
import com.sweattypalms.skyblock.commands.TabCompleter;
import com.sweattypalms.skyblock.core.dungeons.DungeonFileManager;
import com.sweattypalms.skyblock.core.dungeons.DungeonGenerationService;
import com.sweattypalms.skyblock.core.dungeons.generation.DungeonPreset;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class DungeonCommands {
    @Command(name = "dungeon", description = "Dungeon command", op = true)
    public void dungeonCommand(Player player, String[] args) {
        if (args.length == 0) {
            sendHelp(player);
            return;
        }
        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "import" -> handleImport(player, args);
                case "generate", "gen" -> handleGenerate(player, args);
                case "path" -> player.sendMessage(ChatColor.YELLOW + "Samples path: " + DungeonFileManager.getSamplesRoot().toAbsolutePath());
                default -> sendHelp(player);
            }
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Dungeon operation failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @TabCompleter(command = "dungeon")
    public List<String> dungeonCompleter(Player player, String[] args) {
        if (args.length == 1) {
            String q = args[0].toLowerCase();
            return List.of("generate", "import", "path").stream().filter(s -> s.startsWith(q)).toList();
        }
        if (args.length == 2 && ("generate".equalsIgnoreCase(args[0]) || "gen".equalsIgnoreCase(args[0]))) {
            String q = args[1].toLowerCase();
            return List.of("1", "2", "3", "4", "5", "6", "7").stream().filter(s -> s.startsWith(q)).toList();
        }
        return List.of();
    }

    private void handleImport(Player player, String[] args) throws Exception {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /dungeon import <samples_folder_path>");
            return;
        }
        String inputPath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Path source = Paths.get(inputPath);
        DungeonFileManager.importSamples(source);
        player.sendMessage(ChatColor.GREEN + "Imported dungeon samples to: " + DungeonFileManager.getSamplesRoot().toAbsolutePath());
    }

    private void handleGenerate(Player player, String[] args) throws Exception {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /dungeon generate <1-7> [world_name]");
            return;
        }

        DungeonPreset preset = parsePreset(args[1]);
        if (preset == null) {
            player.sendMessage(ChatColor.RED + "Invalid floor. Use 1-7 or FLOOR1-FLOOR7.");
            return;
        }

        DungeonFileManager.ensureStructure();
        Path samplesRoot = DungeonFileManager.getSamplesRoot();
        String worldName = args.length >= 3 ? args[2] : null;
        DungeonGenerationService.generate(player, preset, samplesRoot, worldName);
    }

    private DungeonPreset parsePreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.matches("[1-7]")) {
            return DungeonPreset.valueOf("FLOOR" + normalized);
        }
        if (!normalized.startsWith("FLOOR")) {
            normalized = "FLOOR" + normalized;
        }
        try {
            return DungeonPreset.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "Dungeon Commands:");
        player.sendMessage(ChatColor.YELLOW + "/dungeon import <samples_folder_path>");
        player.sendMessage(ChatColor.YELLOW + "/dungeon generate <1-7> [world_name]");
        player.sendMessage(ChatColor.YELLOW + "/dungeon path");
    }
}
