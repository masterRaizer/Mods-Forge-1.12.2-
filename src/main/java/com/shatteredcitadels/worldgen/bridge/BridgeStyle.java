package com.shatteredcitadels.worldgen.bridge;

import java.util.Locale;
import java.util.Random;

/** The looks a river bridge can have: seven that follow the biome of the banks and three rare specials. */
public enum BridgeStyle {
    TIMBER(3, 3, true, false),
    STONE_ARCH(5, 4, true, false),
    SPRUCE(3, 3, true, false),
    SANDSTONE(5, 3, true, false),
    ROPE(3, 4, false, false),
    BOARDWALK(3, 1, false, false),
    SNOW_STONE(5, 3, true, false),
    RUINED(5, 4, false, true),
    COVERED(5, 3, true, true),
    TURRETED(5, 4, true, true);

    /** The kind of land the bridge stands in, from the biome of its banks. */
    public enum Bank { FOREST, TAIGA, DESERT, JUNGLE, SWAMP, SNOW }

    /** A turreted bridge and a fortified camp need at least this much water. */
    public static final int WIDE_RIVER = 9;
    private static final int BOARDWALK_MAX = 16;

    /** Deck width in blocks (odd). */
    public final int width;
    /** Deck height above sea level. */
    public final int deckRise;
    /** False where the deck is too frail or broken for a camp. */
    public final boolean solidDeck;
    public final boolean special;

    BridgeStyle(int width, int deckRise, boolean solidDeck, boolean special) {
        this.width = width;
        this.deckRise = deckRise;
        this.solidDeck = solidDeck;
        this.special = special;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the style with that id, or null. */
    public static BridgeStyle byId(String id) {
        for (BridgeStyle style : values()) {
            if (style.id().equalsIgnoreCase(id)) {
                return style;
            }
        }
        return null;
    }

    public static BridgeStyle choose(Bank bank, int riverWidth, Random rand, double specialChance) {
        if (rand.nextDouble() < specialChance) {
            switch (rand.nextInt(3)) {
                case 0:
                    return RUINED;
                case 1:
                    return COVERED;
                default:
                    return riverWidth >= WIDE_RIVER ? TURRETED : COVERED;
            }
        }
        switch (bank) {
            case TAIGA:
                return SPRUCE;
            case DESERT:
                return SANDSTONE;
            case JUNGLE:
                return ROPE;
            case SWAMP:
                return riverWidth <= BOARDWALK_MAX ? BOARDWALK : TIMBER;
            case SNOW:
                return SNOW_STONE;
            default:
                return riverWidth >= WIDE_RIVER && rand.nextBoolean() ? STONE_ARCH : TIMBER;
        }
    }

    /** @return true if this bridge gets a bandit camp. */
    public static boolean fortified(BridgeStyle style, int riverWidth, Random rand, double fortifiedChance) {
        if (style == TURRETED) {
            return true;
        }
        if (style.special || !style.solidDeck || riverWidth < WIDE_RIVER) {
            return false;
        }
        return rand.nextDouble() < fortifiedChance;
    }
}
