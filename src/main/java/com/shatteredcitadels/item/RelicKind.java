package com.shatteredcitadels.item;

import java.util.Locale;

/** The wearable relics and the Baubles slot each one occupies (as a plain name). */
public enum RelicKind {
    /** Shard of the Shattered Crown: amulet. */
    CROWN_SHARD("AMULET"),
    /** Aegis of the Cloudspire: body. */
    CLOUDSPIRE_AEGIS("BODY"),
    /** Heart of the Goblin Forge: charm. */
    GOBLIN_FORGE_HEART("CHARM"),
    /** Guardian Folio: trinket, from the catacomb mage's pedestals. */
    GUARDIAN_FOLIO("TRINKET"),
    /** Ring of Ashen Sight: ring, from the catacomb mage's pedestals. */
    ASHEN_SIGHT_RING("RING");

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
