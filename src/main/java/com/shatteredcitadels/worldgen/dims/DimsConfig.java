package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.config.ModConfig;

/** Reads the {@code dimensions} config category (live values: the category has no cached snapshot). */
public final class DimsConfig {

    private DimsConfig() {
    }

    /** @return the dimension kind of the world if the mod builds there by the rules of this package, else null */
    public static DimKind active(net.minecraft.world.World world) {
        DimKind kind = DimKind.of(world);
        return kind != null && enabled(kind) ? kind : null;
    }

    public static boolean enabled(DimKind kind) {
        switch (kind) {
            case NETHER:
                return ModConfig.DIMENSIONS.netherEnabled;
            case END:
                return ModConfig.DIMENSIONS.endEnabled;
            default:
                return ModConfig.DIMENSIONS.atumEnabled;
        }
    }

    public static double spacing(DimKind kind) {
        switch (kind) {
            case NETHER:
                return ModConfig.DIMENSIONS.netherSpacing;
            case END:
                return ModConfig.DIMENSIONS.endSpacing;
            default:
                return ModConfig.DIMENSIONS.atumSpacing;
        }
    }

    /** Share of walled castles (the Nether, the End); Atum builds no castles. */
    public static double castleChance(DimKind kind) {
        switch (kind) {
            case NETHER:
                return ModConfig.DIMENSIONS.netherCastleChance;
            case END:
                return ModConfig.DIMENSIONS.endCastleChance;
            default:
                return 0.0D;
        }
    }

    public static double templeChance(DimKind kind) {
        return kind == DimKind.ATUM ? ModConfig.DIMENSIONS.atumTempleChance : 0.0D;
    }

    public static String[] mobs(DimKind kind) {
        switch (kind) {
            case NETHER:
                return ModConfig.DIMENSIONS.netherMobs;
            case END:
                return ModConfig.DIMENSIONS.endMobs;
            default:
                return ModConfig.DIMENSIONS.atumMobs;
        }
    }

    public static int endMinDistance() {
        return ModConfig.DIMENSIONS.endMinDistance;
    }

    public static boolean replaceNetherFortress() {
        return ModConfig.DIMENSIONS.replaceNetherFortress;
    }

    public static int fortressBlazeSpawners() {
        return ModConfig.DIMENSIONS.fortressBlazeSpawners;
    }
}
