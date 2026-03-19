package com.sweattypalms.skyblock.core.dungeons.generation;

public enum DungeonType {
    _1x1(1),
    _L1(3),
    _L2(3),
    _2x2(4),
    _2x1(2, 1),
    _3x1(3, 2),
    _4x1(4, 3);

    int distance;
    int amountOfRooms;

    DungeonType(int amountOfRooms) {
        this.amountOfRooms = amountOfRooms;
    }

    DungeonType(int amountOfRooms, int distance) {
        this.amountOfRooms = amountOfRooms;
        this.distance = distance;
    }
}
