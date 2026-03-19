package com.sweattypalms.skyblock.core.dungeons;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;

public final class WorldEditSchematicPaster {

    private WorldEditSchematicPaster() {
    }

    public static void paste(File schematicFile, Location origin, int rotation) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            throw new IOException("Unsupported schematic format: " + schematicFile.getAbsolutePath());
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            clipboard = reader.read();
        }

        com.sk89q.worldedit.world.World adaptedWorld = BukkitAdapter.adapt(origin.getWorld());
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
            BlockVector3 min = clipboard.getMinimumPoint();
            BlockVector3 max = clipboard.getMaximumPoint();
            BlockVector3 relativeOrigin = clipboard.getOrigin().subtract(min);

            int width = max.getX() - min.getX() + 1;
            int height = max.getY() - min.getY() + 1;
            int length = max.getZ() - min.getZ() + 1;

            LegacyOffset offset = readLegacyOffset(schematicFile);
            int offX = offset != null ? offset.x() : relativeOrigin.getX();
            int offY = offset != null ? offset.y() : relativeOrigin.getY();
            int offZ = offset != null ? offset.z() : relativeOrigin.getZ();

            int rot = normalizeRotation(rotation);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < length; z++) {
                        BlockState block = clipboard.getBlock(min.add(x, y, z));
                        if (isAir(block)) {
                            continue;
                        }

                        int dx = x + offX;
                        int dz = z + offZ;

                        int[] rotated = rotate(dx, dz, rot);
                        BlockVector3 target = BlockVector3.at(
                                origin.getBlockX() + rotated[0],
                                origin.getBlockY() + y + offY,
                                origin.getBlockZ() + rotated[1]
                        );
                        editSession.setBlock(target, block);
                    }
                }
            }
            editSession.flushSession();
        } catch (Exception ex) {
            throw new IOException("Failed to paste schematic with WorldEdit: " + schematicFile.getAbsolutePath(), ex);
        }
    }

    private static boolean isAir(BlockState state) {
        return state.getBlockType() == BlockTypes.AIR
                || state.getBlockType() == BlockTypes.CAVE_AIR
                || state.getBlockType() == BlockTypes.VOID_AIR;
    }

    private static int normalizeRotation(int rotation) {
        int rot = rotation % 360;
        if (rot < 0) {
            rot += 360;
        }
        return switch (rot) {
            case 90, 180, 270 -> rot;
            default -> 0;
        };
    }

    private static int[] rotate(int x, int z, int rotation) {
        return switch (rotation) {
            case 90 -> new int[]{-z, x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private static LegacyOffset readLegacyOffset(File schematicFile) {
        try {
            byte[] raw = Files.readAllBytes(schematicFile.toPath());
            LegacyOffset offset = parseOffset(raw, true);
            if (offset != null) {
                return offset;
            }
            return parseOffset(raw, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LegacyOffset parseOffset(byte[] raw, boolean gzipped) throws IOException {
        try (DataInputStream in = new DataInputStream(
                gzipped ? new GZIPInputStream(new ByteArrayInputStream(raw)) : new ByteArrayInputStream(raw))) {
            int rootType = in.readUnsignedByte();
            if (rootType != 10) {
                return null;
            }
            in.readUTF();
            int x = 0;
            int y = 0;
            int z = 0;
            boolean hasAny = false;
            while (true) {
                int type = in.readUnsignedByte();
                if (type == 0) {
                    break;
                }
                String name = in.readUTF();
                if (type == 3 && "WEOffsetX".equals(name)) {
                    x = in.readInt();
                    hasAny = true;
                    continue;
                }
                if (type == 3 && "WEOffsetY".equals(name)) {
                    y = in.readInt();
                    hasAny = true;
                    continue;
                }
                if (type == 3 && "WEOffsetZ".equals(name)) {
                    z = in.readInt();
                    hasAny = true;
                    continue;
                }
                skipPayload(in, type);
            }
            return hasAny ? new LegacyOffset(x, y, z) : null;
        }
    }

    private static void skipPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1 -> in.skipBytes(1);
            case 2 -> in.skipBytes(2);
            case 3 -> in.skipBytes(4);
            case 4 -> in.skipBytes(8);
            case 5 -> in.skipBytes(4);
            case 6 -> in.skipBytes(8);
            case 7 -> in.skipBytes(in.readInt());
            case 8 -> in.skipBytes(in.readUnsignedShort());
            case 9 -> {
                int elementType = in.readUnsignedByte();
                int len = in.readInt();
                for (int i = 0; i < len; i++) {
                    skipPayload(in, elementType);
                }
            }
            case 10 -> {
                while (true) {
                    int nestedType = in.readUnsignedByte();
                    if (nestedType == 0) {
                        break;
                    }
                    in.readUTF();
                    skipPayload(in, nestedType);
                }
            }
            case 11 -> in.skipBytes(in.readInt() * 4);
            case 12 -> in.skipBytes(in.readInt() * 8);
            default -> throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    private record LegacyOffset(int x, int y, int z) {
    }
}
