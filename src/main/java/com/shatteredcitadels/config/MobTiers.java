package com.shatteredcitadels.config;

/**
 * Tier bounds shared by mob scanning, wave composition and boss selection. Kept out of
 * {@link ModConfig}: a {@code category = ""} config class may only hold category objects.
 */
public final class MobTiers {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;

    private MobTiers() {
    }

    public static int clamp(int tier) {
        return tier < MIN_TIER ? MIN_TIER : (tier > MAX_TIER ? MAX_TIER : tier);
    }
}
