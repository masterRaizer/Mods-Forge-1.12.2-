package com.shatteredcitadels.item;

import java.util.Locale;

/** The three wearable relics and the Baubles slot each one occupies (as a plain name). */
public enum RelicKind {
    /** Shard of the Shattered Crown: amulet. */
    CROWN_SHARD("AMULET"),
    /** Aegis of the Cloudspire: body. */
    CLOUDSPIRE_AEGIS("BODY"),
    /** Heart of the Goblin Forge: charm. */
    GOBLIN_FORGE_HEART("CHARM");

    private final String baubleSlot;

    RelicKind(String baubleSlot) {
        this.baubleSlot = baubleSlot;
    }

    /** Name of the {@code baubles.api.BaubleType} constant; resolved only when Baubles is present. */
    public String getBaubleSlot() {
        return baubleSlot;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
