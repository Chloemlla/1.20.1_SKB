package com.sweattypalms.skyblock.core.dungeons.map;

import com.sweattypalms.skyblock.core.dungeons.generation.DungeonSpecialType;
import com.sweattypalms.skyblock.core.dungeons.generation.DungeonType;
import com.sweattypalms.skyblock.core.dungeons.generation.LegacyGenerator;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonMapRenderer extends MapRenderer {
    private static final int CANVAS_SIZE = 128;
    private static final int MARGIN = 8;
    private static final int GAP = 2;
    private static final int MIN_CELL_SIZE = 6;

    private final LegacyGenerator generator;
    private boolean rendered;

    public DungeonMapRenderer(LegacyGenerator generator) {
        this.generator = generator;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }

        int gridWidth = Math.max(1, generator.sizeX);
        int gridHeight = Math.max(1, generator.sizeY);
        int maxGrid = Math.max(gridWidth, gridHeight);

        int available = CANVAS_SIZE - (MARGIN * 2);
        int cellSize = Math.max(MIN_CELL_SIZE, (available - (maxGrid - 1) * GAP) / maxGrid);
        int step = cellSize + GAP;

        int usedWidth = (gridWidth * cellSize) + ((gridWidth - 1) * GAP);
        int usedHeight = (gridHeight * cellSize) + ((gridHeight - 1) * GAP);
        int offsetX = (CANVAS_SIZE - usedWidth) / 2;
        int offsetY = (CANVAS_SIZE - usedHeight) / 2;

        BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        drawRooms(graphics, offsetX, offsetY, cellSize, step);
        drawConnections(graphics, offsetX, offsetY, cellSize, step);
        graphics.dispose();

        image = MapPalette.resizeImage(image);
        for (int x = 0; x < CANVAS_SIZE; x++) {
            for (int y = 0; y < CANVAS_SIZE; y++) {
                Color pixelColor = new Color(image.getRGB(x, y), true);
                if (pixelColor.getAlpha() == 0) {
                    canvas.setPixel(x, y, MapPalette.TRANSPARENT);
                    continue;
                }
                canvas.setPixel(x, y, MapPalette.matchColor(pixelColor));
            }
        }

        rendered = true;
    }

    private void drawRooms(Graphics2D graphics, int offsetX, int offsetY, int cellSize, int step) {
        List<List<int[]>> roomGroups = collectRoomGroups();

        for (List<int[]> group : roomGroups) {
            if (group.isEmpty()) {
                continue;
            }

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (int[] cell : group) {
                minX = Math.min(minX, cell[0]);
                maxX = Math.max(maxX, cell[0]);
                minY = Math.min(minY, cell[1]);
                maxY = Math.max(maxY, cell[1]);
            }

            Color baseColor = resolveGroupColor(group);
            int drawX = offsetX + (minX * step);
            int drawY = offsetY + (minY * step);
            int drawWidth = ((maxX - minX + 1) * cellSize) + ((maxX - minX) * GAP);
            int drawHeight = ((maxY - minY + 1) * cellSize) + ((maxY - minY) * GAP);

            graphics.setColor(baseColor);
            graphics.fillRect(drawX, drawY, drawWidth, drawHeight);

            graphics.setColor(baseColor.darker());
            graphics.drawRect(drawX, drawY, drawWidth - 1, drawHeight - 1);
        }
    }

    private void drawConnections(Graphics2D graphics, int offsetX, int offsetY, int cellSize, int step) {
        int thickness = Math.max(2, cellSize / 3);
        Set<String> drawnPairs = new HashSet<>();

        for (Map.Entry<String, ArrayList<String>> entry : generator.doors.entrySet()) {
            int[] from = parseCoordinates(entry.getKey());
            if (!isValidOccupiedCell(from)) {
                continue;
            }

            for (String target : entry.getValue()) {
                int[] to = parseCoordinates(target);
                if (!isValidOccupiedCell(to)) {
                    continue;
                }

                String pairKey = normalizePair(from, to);
                if (!drawnPairs.add(pairKey)) {
                    continue;
                }

                int x1 = offsetX + from[0] * step;
                int y1 = offsetY + from[1] * step;
                int x2 = offsetX + to[0] * step;
                int y2 = offsetY + to[1] * step;

                graphics.setColor(Color.BLACK);

                if (from[0] == to[0]) {
                    int drawX = x1 + (cellSize - thickness) / 2;
                    int drawY = Math.min(y1, y2) + cellSize;
                    graphics.fillRect(drawX, drawY, thickness, GAP);
                } else if (from[1] == to[1]) {
                    int drawX = Math.min(x1, x2) + cellSize;
                    int drawY = y1 + (cellSize - thickness) / 2;
                    graphics.fillRect(drawX, drawY, GAP, thickness);
                }
            }
        }
    }

    private List<List<int[]>> collectRoomGroups() {
        List<List<int[]>> groups = new ArrayList<>();
        boolean[][] visited = new boolean[generator.sizeY][generator.sizeX];

        for (int y = 0; y < generator.sizeY; y++) {
            for (int x = 0; x < generator.sizeX; x++) {
                if (visited[y][x] || !generator.alreadyOccupied[y][x]) {
                    continue;
                }

                ArrayList<int[]> extended = generator.getExtendedRooms(x, y);
                List<int[]> normalized = new ArrayList<>();

                for (int[] coords : extended) {
                    if (!isWithinBounds(coords)) {
                        continue;
                    }
                    if (!generator.alreadyOccupied[coords[1]][coords[0]]) {
                        continue;
                    }
                    normalized.add(new int[]{coords[0], coords[1]});
                    visited[coords[1]][coords[0]] = true;
                }

                if (normalized.isEmpty()) {
                    normalized.add(new int[]{x, y});
                    visited[y][x] = true;
                }

                groups.add(normalized);
            }
        }

        return groups;
    }

    private Color resolveGroupColor(List<int[]> cells) {
        for (int[] cell : cells) {
            Color specialColor = getSpecialColor(cell[0], cell[1]);
            if (specialColor != null) {
                return specialColor;
            }
        }

        int[] base = cells.get(0);
        return getTypeColor(generator.types[base[1]][base[0]]);
    }

    private Color getSpecialColor(int x, int y) {
        if (!isWithinBounds(new int[]{x, y}) || !generator.alreadyOccupied[y][x]) {
            return null;
        }

        DungeonSpecialType specialType = generator.specialTypes[y][x];
        if (specialType != null) {
            return switch (specialType) {
                case SPAWN -> new Color(46, 204, 113);
                case BOSS -> new Color(231, 76, 60);
                case FAIRY -> new Color(255, 105, 180);
                case PUZZLE -> new Color(186, 85, 211);
                case TRAP -> new Color(255, 140, 0);
                case MINIBOSS -> new Color(241, 196, 15);
            };
        }

        if (generator.rgbMatches(x, y, 0, 255, 0)) {
            return new Color(46, 204, 113);
        }
        if (generator.rgbMatches(x, y, 255, 0, 0)) {
            return new Color(231, 76, 60);
        }
        if (generator.rgbMatches(x, y, 255, 64, 255)) {
            return new Color(255, 105, 180);
        }

        return null;
    }

    private Color getTypeColor(DungeonType type) {
        if (type == null) {
            return new Color(120, 120, 120);
        }
        return switch (type) {
            case _1x1 -> new Color(139, 69, 19);
            case _2x1 -> new Color(46, 204, 113);
            case _2x2 -> new Color(52, 152, 219);
            case _3x1 -> new Color(241, 196, 15);
            case _4x1 -> new Color(155, 89, 182);
            case _L1, _L2 -> new Color(230, 126, 34);
        };
    }

    private int[] parseCoordinates(String input) {
        if (input == null || input.isBlank()) {
            return new int[]{-1, -1};
        }

        String[] split = input.trim().split(" ");
        if (split.length != 2) {
            return new int[]{-1, -1};
        }

        try {
            return new int[]{Integer.parseInt(split[0]), Integer.parseInt(split[1])};
        } catch (NumberFormatException ignored) {
            return new int[]{-1, -1};
        }
    }

    private boolean isWithinBounds(int[] coords) {
        if (coords == null || coords.length != 2) {
            return false;
        }
        int x = coords[0];
        int y = coords[1];
        return x >= 0 && x < generator.sizeX && y >= 0 && y < generator.sizeY;
    }

    private boolean isValidOccupiedCell(int[] coords) {
        return isWithinBounds(coords) && generator.alreadyOccupied[coords[1]][coords[0]];
    }

    private String normalizePair(int[] a, int[] b) {
        String first = a[0] + "," + a[1];
        String second = b[0] + "," + b[1];
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }
}
