package com.sweattypalms.skyblock.core.dungeons.generation;

import lombok.Getter;

public enum DungeonPreset {
    ENTRANCE(4, 4, "Entrance"),
    FLOOR1(4, 5, "I"),
    FLOOR2(5, 5, "II"),
    FLOOR3(5, 5, "III"),
    FLOOR4(5, 6, "IV"),
    FLOOR5(5, 6, "V"),
    FLOOR6(5, 6, "VI"),
    FLOOR7(6, 6, "VII");

    public int sizeX;
    public int sizeY;
    @Getter String romanNumeral;

    DungeonPreset(int sizeX, int sizeY, String romanNumeral) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.romanNumeral = romanNumeral;
    }
}
