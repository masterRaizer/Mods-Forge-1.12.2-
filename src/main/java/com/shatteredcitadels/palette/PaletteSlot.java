package com.shatteredcitadels.palette;

import java.util.Locale;

/** Architectural role of a block inside a citadel palette. */
public enum PaletteSlot {
    WALL,
    PILLAR,
    FLOOR,
    STAIRS,
    ACCENT,
    LIGHT,
    FURNITURE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PaletteSlot byKey(String key) {
        for (PaletteSlot slot : values()) {
            if (slot.key().equals(key)) {
                return slot;
            }
        }
        return null;
    }
}
