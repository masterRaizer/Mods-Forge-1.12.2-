package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.config.ModConfig;
import com.shatteredcitadels.structure.StructureType;

import java.util.Random;

/**
 * The Herobrine options, read straight from {@link ModConfig#HEROBRINE} and clamped here: a config
 * file edited by hand may hold anything.
 */
public final class HerobrineSettings {

    /** Vanilla caps the health attribute at 1024. */
    static final double MAX_HEALTH = 1024.0D;

    private HerobrineSettings() {
    }

    static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ModConfig.Herobrine config() {
        return ModConfig.HEROBRINE;
    }

    public static boolean enabled() {
        return config().herobrineEnabled;
    }

    public static boolean lairsEnabled() {
        return enabled() && config().lairsEnabled;
    }

    public static int lairSpacingChunks() {
        return clamp(config().lairSpacingChunks, 32, 512);
    }

    public static double lairChance() {
        return clamp(config().lairChance, 0.0D, 1.0D);
    }

    public static int lairSpawnClearanceChunks() {
        return clamp(config().lairSpawnClearanceChunks, 0, 256);
    }

    public static boolean playerBuiltAltar() {
        return config().playerBuiltAltar;
    }

    public static double health() {
        return clamp(config().herobrineHealth, 20.0D, MAX_HEALTH);
    }

    public static double damage() {
        return clamp(config().herobrineDamage, 1.0D, 100.0D);
    }

    public static double armor() {
        return clamp(config().herobrineArmor, 0.0D, 30.0D);
    }

    public static int maxMinions() {
        return clamp(config().herobrineMaxMinions, 0, 16);
    }

    public static int lightningWarningTicks() {
        return clamp(config().lightningWarningTicks, 20, 100);
    }

    public static double lightningDamage() {
        return clamp(config().lightningDamage, 0.0D, 40.0D);
    }

    public static boolean griefing() {
        return config().herobrineGriefing;
    }

    public static double manorBossChance() {
        return clamp(config().manorBossChance, 0.0D, 1.0D);
    }

    /** Whether Herobrine himself answers the boss altar of this structure instead of its usual guardian. */
    public static boolean takesThrone(StructureType type, Random random) {
        return takesThrone(type, enabled(), config().manorBossChance, random);
    }

    static boolean takesThrone(StructureType type, boolean enabled, double chance, Random random) {
        return enabled && type == StructureType.MANOR && random.nextDouble() < clamp(chance, 0.0D, 1.0D);
    }

    public static boolean whispers() {
        return enabled() && config().whispers;
    }

    public static boolean apparitions() {
        return enabled() && config().apparitions;
    }

    public static int apparitionIntervalMinutes() {
        return clamp(config().apparitionIntervalMinutes, 1, 240);
    }

    public static double apparitionChance() {
        return clamp(config().apparitionChance, 0.0D, 1.0D);
    }

    public static int apparitionRadiusBlocks() {
        return clamp(config().apparitionRadiusBlocks, 0, 2048);
    }
}
