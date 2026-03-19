package com.sweattypalms.skyblock.core.items.builder;

import lombok.Getter;

@Getter
public enum Rarity {
    COMMON("\u00A7f"),
    UNCOMMON("\u00A7a"),
    RARE("\u00A79"),
    EPIC("\u00A75"),
    LEGENDARY("\u00A76"),
    MYTHIC("\u00A7d"),
    DIVINE("\u00A7b"),
    SPECIAL("\u00A7c"),
    VERY_SPECIAL("\u00A7c");

    private final String color;

    Rarity(String color) {
        this.color = color;
    }

    public Rarity getUpgraded(){
        int current = this.ordinal();
        if (current == Rarity.values().length - 1)
            return Rarity.values()[current];

        return Rarity.values()[current + 1];
    }
}
