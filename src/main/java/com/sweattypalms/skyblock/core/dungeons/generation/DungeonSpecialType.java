package com.sweattypalms.skyblock.core.dungeons.generation;

import lombok.Getter;

public enum DungeonSpecialType {
    MINIBOSS("MiniBoss"),
    PUZZLE("Puzzle"),
    TRAP("Trap"),
    FAIRY("Fairy"),
    BOSS("Boss"),
    SPAWN("Spawn");

    @Getter
    private final String fileName;

    DungeonSpecialType(String fileName) {
        this.fileName = fileName;
    }
}
