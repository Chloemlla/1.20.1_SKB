package com.sweattypalms.skyblock.core.dungeons;

import com.sweattypalms.skyblock.core.dungeons.generation.DungeonDirections;
import com.sweattypalms.skyblock.core.dungeons.generation.DungeonPreset;
import com.sweattypalms.skyblock.core.dungeons.generation.DungeonRoomType;
import com.sweattypalms.skyblock.core.dungeons.generation.DungeonSpecialType;
import com.sweattypalms.skyblock.core.dungeons.generation.LegacyGenerator;
import com.sweattypalms.skyblock.core.dungeons.map.DungeonMap;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonGenerationService {

    private static final int ROOM_SIZE = 31;
    private static final int ROOM_SPACING = ROOM_SIZE + 1;
    private static final int DEFAULT_PASTE_Y = 63;
    private static final int DEFAULT_SPAWN_Y = 69;

    private DungeonGenerationService() {
    }

    public static World generate(Player player, DungeonPreset preset, Path samplesRoot, String preferredWorldName) throws IOException {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null.");
        }
        if (preset == null) {
            throw new IllegalArgumentException("Preset cannot be null.");
        }
        if (samplesRoot == null || !Files.exists(samplesRoot)) {
            throw new IOException("Samples directory not found: " + samplesRoot);
        }

        LegacyGenerator generator = buildValidGenerator(preset);
        String worldName = preferredWorldName == null || preferredWorldName.isBlank()
                ? "dungeon_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                : preferredWorldName;

        World world = new WorldCreator(worldName)
                .generator(new EmptyChunkGenerator())
                .createWorld();
        if (world == null) {
            throw new IOException("Failed to create dungeon world.");
        }

        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);

        Set<String> doneRooms = new HashSet<>();

        for (int x = 0; x < generator.sizeX; x++) {
            for (int y = 0; y < generator.sizeY; y++) {
                if (doneRooms.contains(generator.co(x, y))) {
                    continue;
                }

                DungeonRoomType roomType = DungeonRoomType.getRoomType(generator.types[y][x]);
                RoomSelection selected = chooseRoomFile(samplesRoot, roomType, x, y, generator);

                String name = selected.name();
                int inputX = x;
                int inputY = y;
                int[] pasteOffset;

                ArrayList<int[]> extendedRooms = new ArrayList<>(generator.getExtendedRooms(x, y));

                if (roomType == DungeonRoomType._1x1) {
                    int randomRotation = ThreadLocalRandom.current().nextInt(4);
                    pasteOffset = getPasteCornerOffset(randomRotation, roomType);
                } else if (roomType == DungeonRoomType.L) {
                    int[] middle = generator.getMiddlePieceOfL(extendedRooms);
                    inputX = middle[0];
                    inputY = middle[1];
                    DungeonDirections[] dirs = getDirections(generator, inputX, inputY);
                    int rotation = correspondingRotation(dirs);
                    int[] pasteCoord = getLPaste(extendedRooms, rotation);
                    inputX = pasteCoord[0];
                    inputY = pasteCoord[1];
                    pasteOffset = getPasteCornerOffset(rotation, roomType);
                } else {
                    int[] lowest = getLowest(roomType, getDirections(generator, x, y), extendedRooms, x, y);
                    inputX = lowest[0];
                    inputY = lowest[1];
                    DungeonDirections[] dirs = getDirections(generator, inputX, inputY);
                    DungeonDirections pasteDir = getPasteDirection(dirs);
                    pasteOffset = getPasteCornerOffset(pasteDir, ROOM_SIZE, ROOM_SIZE);
                }

                Location paste = new Location(
                        world,
                        (inputX * ROOM_SPACING) + pasteOffset[0],
                        getPasteYLevel(name),
                        (inputY * ROOM_SPACING) + pasteOffset[1]
                );
                WorldEditSchematicPaster.paste(selected.file(), paste, pasteOffset[2]);

                for (int[] roomCell : extendedRooms) {
                    doneRooms.add(generator.co(roomCell[0], roomCell[1]));
                }
            }
        }

        placeDoors(generator, world);

        int[] spawnRoom = generator.getSpawnRoom();
        Location spawnLoc = new Location(
                world,
                spawnRoom[0] * ROOM_SPACING + 15.5,
                DEFAULT_SPAWN_Y,
                spawnRoom[1] * ROOM_SPACING + 15.5
        );
        player.teleport(spawnLoc);
        giveMapToPlayer(player, world, generator, preset);
        player.sendMessage(ChatColor.GREEN + "Dungeon generated with preset " + ChatColor.YELLOW + preset.name()
                + ChatColor.GREEN + " in world " + ChatColor.YELLOW + world.getName() + ChatColor.GREEN + ".");
        return world;
    }

    private static void giveMapToPlayer(Player player, World world, LegacyGenerator generator, DungeonPreset preset) {
        DungeonMap dungeonMap = new DungeonMap(generator, preset);
        ItemStack mapItem = dungeonMap.getItem(world);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(mapItem);

        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                world.dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage(ChatColor.YELLOW + "Your inventory is full, the dungeon map was dropped on the ground.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "You received a dungeon map.");
    }

    private static LegacyGenerator buildValidGenerator(DungeonPreset preset) {
        LegacyGenerator generator = new LegacyGenerator(preset);
        generator.setup();
        boolean valid = !generator.generatedSuccessfully
                || generator.maxPuzzleRooms < generator.amountOfPuzzleRooms()
                || !generator.doNotRestart;

        while (valid) {
            generator = new LegacyGenerator(preset);
            generator.setup();
            valid = !generator.generatedSuccessfully
                    || generator.maxPuzzleRooms < generator.amountOfPuzzleRooms()
                    || !generator.doNotRestart;
        }
        return generator;
    }

    private static RoomSelection chooseRoomFile(Path samplesRoot, DungeonRoomType roomType, int x, int y, LegacyGenerator generator) throws IOException {
        DungeonSpecialType specialType = getSpecialType(generator, x, y);

        if (specialType != null) {
            File specialFile = samplesRoot.resolve("specialTypes").resolve(specialType.getFileName() + ".schematic").toFile();
            if (!specialFile.exists()) {
                throw new IOException("Missing special room schematic: " + specialFile.getAbsolutePath());
            }
            return new RoomSelection(specialFile, stripExtension(specialFile.getName()), roomType);
        }

        String typeFolderName = roomTypeName(roomType);
        Path folder = samplesRoot.resolve(typeFolderName);
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            try (var stream = Files.list(folder)) {
                List<File> files = stream
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".schematic"))
                        .map(Path::toFile)
                        .toList();
                if (!files.isEmpty()) {
                    File chosen = files.get(ThreadLocalRandom.current().nextInt(files.size()));
                    return new RoomSelection(chosen, stripExtension(chosen.getName()), roomType);
                }
            }
        }

        File fallback = samplesRoot.resolve(typeFolderName + ".schematic").toFile();
        if (!fallback.exists()) {
            throw new IOException("Missing room schematic: " + fallback.getAbsolutePath());
        }
        return new RoomSelection(fallback, stripExtension(fallback.getName()), roomType);
    }

    private static String roomTypeName(DungeonRoomType roomType) {
        return switch (roomType) {
            case _1x1 -> "1x1";
            case _2x1 -> "2x1";
            case _2x2 -> "2x2";
            case _3x1 -> "3x1";
            case _4x1 -> "4x1";
            case L -> "L";
        };
    }

    private static DungeonSpecialType getSpecialType(LegacyGenerator generator, int x, int y) {
        if (generator.specialTypes[y][x] != null) {
            return generator.specialTypes[y][x];
        }
        if (generator.rgbMatches(x, y, 255, 64, 255)) {
            return DungeonSpecialType.FAIRY;
        }
        if (generator.rgbMatches(x, y, 255, 0, 0)) {
            return DungeonSpecialType.BOSS;
        }
        if (generator.rgbMatches(x, y, 0, 255, 0)) {
            return DungeonSpecialType.SPAWN;
        }
        return null;
    }

    private static int getPasteYLevel(String roomName) {
        return DEFAULT_PASTE_Y;
    }

    private static int[] getPasteCornerOffset(int rotationIndex, DungeonRoomType roomType) {
        int[] offset = new int[3];
        // Keep legacy behavior: this branch is used by 1x1 and L rooms and
        // historically both used a 31x31 corner offset basis.
        int sizeX = ROOM_SIZE;
        int sizeZ = ROOM_SIZE;
        offset[2] = getRotation(rotationIndex);
        switch (rotationIndex) {
            case 1:
                offset[0] = sizeX - 1;
                break;
            case 2:
                offset[0] = sizeX - 1;
                offset[1] = sizeZ - 1;
                break;
            case 3:
                offset[1] = sizeZ - 1;
                break;
            default:
                break;
        }
        return offset;
    }

    private static int[] getPasteCornerOffset(DungeonDirections dir, int sizeX, int sizeZ) {
        int[] offset = new int[3];
        switch (dir) {
            case UP:
                offset[1] = sizeZ - 1;
                offset[2] = 270;
                break;
            case LEFT:
                offset[0] = sizeX - 1;
                offset[1] = sizeZ - 1;
                offset[2] = 180;
                break;
            case DOWN:
                offset[0] = sizeX - 1;
                offset[2] = 90;
                break;
            case RIGHT:
            default:
                break;
        }
        return offset;
    }

    private static int getRoomSizeX(DungeonRoomType roomType) {
        return switch (roomType) {
            case _1x1 -> 31;
            case _2x1, _2x2, L -> 63;
            case _3x1 -> 95;
            case _4x1 -> 127;
        };
    }

    private static int getRoomSizeZ(DungeonRoomType roomType) {
        return switch (roomType) {
            case _1x1, _2x1, _3x1, _4x1 -> 31;
            case _2x2, L -> 63;
        };
    }

    private static int getRotation(int rotationIndex) {
        return switch (rotationIndex) {
            case 1 -> 90;
            case 2 -> 180;
            case 3 -> 270;
            default -> 0;
        };
    }

    private static int correspondingRotation(DungeonDirections[] dirs) {
        if (dirs[0] == DungeonDirections.RIGHT && dirs[1] == DungeonDirections.UP) {
            return 2;
        }
        if (dirs[0] == DungeonDirections.RIGHT && dirs[1] == DungeonDirections.DOWN) {
            return 3;
        }
        if (dirs[0] == DungeonDirections.LEFT && dirs[1] == DungeonDirections.DOWN) {
            return 0;
        }
        return 1;
    }

    private static DungeonDirections getPasteDirection(DungeonDirections[] dirs) {
        if (dirs[0] == null || dirs[1] == null) {
            return dirs[0] == null ? dirs[1] : dirs[0];
        }
        if (dirs[0] == DungeonDirections.RIGHT && dirs[1] == DungeonDirections.UP) {
            return DungeonDirections.UP;
        }
        if (dirs[0] == DungeonDirections.RIGHT && dirs[1] == DungeonDirections.DOWN) {
            return DungeonDirections.RIGHT;
        }
        if (dirs[0] == DungeonDirections.LEFT && dirs[1] == DungeonDirections.DOWN) {
            return DungeonDirections.DOWN;
        }
        return DungeonDirections.LEFT;
    }

    private static int[] getLPaste(ArrayList<int[]> extended, int rotation) {
        int[] result = new int[2];
        switch (rotation) {
            case 2 -> {
                result[0] = getHighestX(extended);
                result[1] = getHighestY(extended);
            }
            case 3 -> {
                result[0] = getLowestX(extended);
                result[1] = getHighestY(extended);
            }
            case 0 -> {
                result[0] = getLowestX(extended);
                result[1] = getLowestY(extended);
            }
            default -> {
                result[0] = getHighestX(extended);
                result[1] = getLowestY(extended);
            }
        }
        return result;
    }

    private static int[] getLowest(DungeonRoomType type, DungeonDirections[] dirs, ArrayList<int[]> list, int baseX, int baseY) {
        if (type == DungeonRoomType.L || type == DungeonRoomType._2x2) {
            int[] result = new int[2];
            DungeonDirections upDown = dirs[1];
            DungeonDirections leftRight = dirs[0];
            if (upDown == DungeonDirections.UP) {
                if (leftRight == DungeonDirections.LEFT) {
                    result[0] = getLowestX(list);
                    result[1] = getLowestY(list);
                } else {
                    result[0] = getLowestX(list);
                    result[1] = getHighestY(list);
                }
            } else {
                if (leftRight == DungeonDirections.LEFT) {
                    result[0] = getHighestX(list);
                    result[1] = getLowestY(list);
                } else {
                    result[0] = getHighestX(list);
                    result[1] = getHighestY(list);
                }
            }
            return result;
        }

        DungeonDirections dir = getPasteDirection(dirs);
        return switch (dir) {
            case UP -> new int[]{baseX, getHighestY(list)};
            case DOWN -> new int[]{baseX, getLowestY(list)};
            case LEFT -> new int[]{getLowestX(list), baseY};
            case RIGHT -> new int[]{getHighestX(list), baseY};
        };
    }

    private static int getLowestX(ArrayList<int[]> list) {
        int lowest = Integer.MAX_VALUE;
        for (int[] room : list) {
            if (room[0] < lowest) {
                lowest = room[0];
            }
        }
        return lowest;
    }

    private static int getHighestX(ArrayList<int[]> list) {
        int highest = Integer.MIN_VALUE;
        for (int[] room : list) {
            if (room[0] > highest) {
                highest = room[0];
            }
        }
        return highest;
    }

    private static int getLowestY(ArrayList<int[]> list) {
        int lowest = Integer.MAX_VALUE;
        for (int[] room : list) {
            if (room[1] < lowest) {
                lowest = room[1];
            }
        }
        return lowest;
    }

    private static int getHighestY(ArrayList<int[]> list) {
        int highest = Integer.MIN_VALUE;
        for (int[] room : list) {
            if (room[1] > highest) {
                highest = room[1];
            }
        }
        return highest;
    }

    private static DungeonDirections[] getDirections(LegacyGenerator generator, int x, int y) {
        DungeonDirections[] directions = new DungeonDirections[2];
        ArrayList<int[]> list = generator.getExtendedRooms(x, y);
        int[] base = !list.isEmpty() ? list.get(0) : new int[]{x, y};
        list.remove(base);
        for (int[] cell : list) {
            if (cell[0] < x) {
                directions[0] = DungeonDirections.LEFT;
            } else if (cell[0] > x) {
                directions[0] = DungeonDirections.RIGHT;
            }
            if (cell[1] < y) {
                directions[1] = DungeonDirections.UP;
            } else if (cell[1] > y) {
                directions[1] = DungeonDirections.DOWN;
            }
        }
        return directions;
    }

    private static void placeDoors(LegacyGenerator generator, World world) {
        for (String name : generator.doors.keySet()) {
            String[] split = name.split(" ");
            int x1 = Integer.parseInt(split[0]);
            int y1 = Integer.parseInt(split[1]);

            for (String connected : generator.doors.get(name)) {
                String[] connectedSplit = connected.split(" ");
                int x2 = Integer.parseInt(connectedSplit[0]);
                int y2 = Integer.parseInt(connectedSplit[1]);

                int mcX1 = x1 * ROOM_SPACING;
                int mcZ1 = y1 * ROOM_SPACING;
                int mcX2 = x2 * ROOM_SPACING;
                int mcZ2 = y2 * ROOM_SPACING;

                int gradient1X1 = x1 * ROOM_SPACING;
                int gradient1Z1 = y1 * ROOM_SPACING;
                int gradient1X2 = x1 * ROOM_SPACING;
                int gradient1Z2 = y1 * ROOM_SPACING;

                int gradient2X1 = x2 * ROOM_SPACING;
                int gradient2Z1 = y2 * ROOM_SPACING;
                int gradient2X2 = x2 * ROOM_SPACING;
                int gradient2Z2 = y2 * ROOM_SPACING;

                DungeonDirections dir = generator.directionGoingIn(x1, y1, x2, y2);
                switch (dir) {
                    case RIGHT -> {
                        mcX1 += 29;
                        mcZ1 += 13;
                        mcX2 += 1;
                        mcZ2 += 17;
                        gradient1X1 += 28;
                        gradient1Z1 += 13;
                        gradient1X2 = gradient1X1;
                        gradient1Z2 = gradient1Z1 + 4;
                        gradient2X1 += 2;
                        gradient2Z1 += 17;
                        gradient2X2 = gradient2X1;
                        gradient2Z2 = gradient2Z1 - 4;
                    }
                    case LEFT -> {
                        mcX1 += 1;
                        mcZ1 += 13;
                        mcX2 += 29;
                        mcZ2 += 17;
                        gradient1X1 += 2;
                        gradient1Z1 += 13;
                        gradient1X2 = gradient1X1;
                        gradient1Z2 = gradient1Z1 + 4;
                        gradient2X1 += 28;
                        gradient2Z1 += 17;
                        gradient2X2 = gradient2X1;
                        gradient2Z2 = gradient2Z1 - 4;
                    }
                    case DOWN -> {
                        mcX1 += 13;
                        mcZ1 += 1;
                        mcX2 += 17;
                        mcZ2 += 29;
                        gradient1X1 += 13;
                        gradient1Z1 += 2;
                        gradient1X2 = gradient1X1 + 4;
                        gradient1Z2 = gradient1Z1;
                        gradient2X1 += 17;
                        gradient2Z1 += 28;
                        gradient2X2 = gradient2X1 - 4;
                        gradient2Z2 = gradient2Z1;
                    }
                    case UP -> {
                        mcX1 += 13;
                        mcZ1 += 29;
                        mcX2 += 17;
                        mcZ2 += 1;
                        gradient1X1 += 13;
                        gradient1Z1 += 28;
                        gradient1X2 = gradient1X1 + 4;
                        gradient1Z2 = gradient1Z1;
                        gradient2X1 += 17;
                        gradient2Z1 += 2;
                        gradient2X2 = gradient2X1 - 4;
                        gradient2Z2 = gradient2Z1;
                    }
                }

                Location loc1 = new Location(world, mcX1, 67, mcZ1);
                Location loc2 = new Location(world, mcX2, 66, mcZ2);
                blocksFromTwoPoints(loc1, loc2).forEach(block -> block.setType(Material.BEDROCK, false));

                Location gradient1Loc1 = new Location(world, gradient1X1, 68, gradient1Z1);
                Location gradient1Loc2 = new Location(world, gradient1X2, 68, gradient1Z2);
                Location gradient2Loc1 = new Location(world, gradient2X1, 68, gradient2Z1);
                Location gradient2Loc2 = new Location(world, gradient2X2, 68, gradient2Z2);

                List<BlockData> materials = new ArrayList<>();
                collectBlockData(materials, blocksFromTwoPoints(gradient1Loc1, gradient1Loc2));
                collectBlockData(materials, blocksFromTwoPoints(gradient2Loc1, gradient2Loc2));

                if (materials.isEmpty()) {
                    continue;
                }

                loc1.setY(68);
                loc2.setY(68);
                for (Block block : blocksFromTwoPoints(loc1, loc2)) {
                    BlockData randomData = materials.get(ThreadLocalRandom.current().nextInt(materials.size()));
                    block.setBlockData(randomData.clone(), false);
                }
            }
        }
    }

    private static void collectBlockData(List<BlockData> out, List<Block> blocks) {
        for (Block block : blocks) {
            BlockData data = block.getBlockData();
            boolean exists = false;
            for (BlockData current : out) {
                if (current.matches(data)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                out.add(data.clone());
            }
        }
    }

    private static List<Block> blocksFromTwoPoints(Location loc1, Location loc2) {
        List<Block> blocks = new ArrayList<>();
        int topX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int bottomX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int topY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int bottomY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int topZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
        int bottomZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());

        for (int x = bottomX; x <= topX; x++) {
            for (int z = bottomZ; z <= topZ; z++) {
                for (int y = bottomY; y <= topY; y++) {
                    blocks.add(loc1.getWorld().getBlockAt(x, y, z));
                }
            }
        }
        return blocks;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private record RoomSelection(File file, String name, DungeonRoomType roomType) {
    }
}
