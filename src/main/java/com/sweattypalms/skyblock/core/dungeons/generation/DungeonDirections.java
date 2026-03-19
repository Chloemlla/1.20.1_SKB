package com.sweattypalms.skyblock.core.dungeons.generation;

public enum DungeonDirections {
    LEFT(-1, 0),
    RIGHT(1, 0),
    UP(0, 1),
    DOWN(0, -1);

    int x, y;

    DungeonDirections(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
