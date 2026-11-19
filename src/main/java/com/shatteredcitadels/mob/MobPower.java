package com.shatteredcitadels.mob;

import com.shatteredcitadels.config.MobTiers;

import java.util.Locale;

/** Pure scoring rules for the mob scanner (unit-tested, no Minecraft state). */
public final class MobPower {

    /** Upper score bounds of tiers 1-4; anything above the last bound is tier 5. */
    static final double[] TIER_BOUNDS = {30.0D, 40.0D, 60.0D, 100.0D};

    /** Registry path keywords of mobs that belong to the underground pool. */
    private static final String[] UNDERGROUND_KEYWORDS = {
            "goblin", "kobold", "troll", "orc", "gnome", "dwarf", "cave", "mole", "worm", "crawler", "deep",
            "under", "tunnel", "burrow", "mine", "ghoul", "imp", "nether", "magma", "lava", "infernal", "abyss",
            "spider", "wraith"
    };

    private MobPower() {
    }

    /** Combat power from base attributes; non-finite or negative inputs count as 0. */
    public static double score(double maxHealth, double attackDamage, double armor) {
        return safe(maxHealth) + 3.0D * safe(attackDamage) + 1.5D * safe(armor);
    }

    public static int tierFor(double score) {
        for (int i = 0; i < TIER_BOUNDS.length; i++) {
            if (score <= TIER_BOUNDS[i]) {
                return MobTiers.MIN_TIER + i;
            }
        }
        return MobTiers.MAX_TIER;
    }

    /** @return true if the registry path suggests a cave/underground/nether dweller. */
    public static boolean looksUnderground(String registryPath) {
        if (registryPath == null) {
            return false;
        }
        String path = registryPath.toLowerCase(Locale.ROOT);
        for (String keyword : UNDERGROUND_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static double safe(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D ? 0.0D : Math.min(value, 100000.0D);
    }
}
