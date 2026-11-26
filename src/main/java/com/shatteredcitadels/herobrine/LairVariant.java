package com.shatteredcitadels.herobrine;

import java.util.Locale;

/** The three looks of the lair. */
public enum LairVariant {
    /** Ruined stone keep, altar in the yard. */
    KEEP(8, 8, 14),
    /** Obsidian tower, altar on the ground floor. */
    TOWER(5, 8, 15),
    /** Underground chamber under a small surface marker. */
    SHRINE(6, 15, 5);

    /** Half of the side of the square footprint. */
    public final int half;
    /** Blocks the footprint reaches below the ground level. */
    public final int below;
    /** Blocks the footprint reaches above the ground level. */
    public final int above;

    LairVariant(int half, int below, int above) {
        this.half = half;
        this.below = below;
        this.above = above;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the variant of that id, or null */
    public static LairVariant byId(String id) {
        for (LairVariant variant : values()) {
            if (variant.id().equalsIgnoreCase(id)) {
                return variant;
            }
        }
        return null;
    }
}
