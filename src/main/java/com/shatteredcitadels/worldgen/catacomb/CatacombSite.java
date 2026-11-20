package com.shatteredcitadels.worldgen.catacomb;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Where the first catacomb level goes. The levels are much wider than the structure above, so
 * they must stay under the lowest ground around it (a valley, a river, the sea floor). Heights of
 * unloaded chunks are unknown at planning time; the biome provider (which never loads a chunk)
 * gives each biome's base height, from which the terrain level is estimated.
 */
public final class CatacombSite {

    /** Margin between the estimated lowest surface and the first level's floor. */
    private static final int MARGIN = 12;

    private CatacombSite() {
    }

    /** Rough terrain level of a biome base height (ocean -1.0 ~ 47, river -0.5 ~ 55, plains ~ 66). */
    public static int estimatedSurface(float baseHeight) {
        return Math.round(64 + baseHeight * 17);
    }

    /** @return y of the first level's floor: ten below the structure, and below the lowest ground around. */
    public static int topLevelY(int baseY, float minBaseHeight) {
        return Math.min(baseY - 10, estimatedSurface(minBaseHeight) - MARGIN);
    }

    /** @return the lowest biome base height on a 9x9 grid over the square of radius {@code r}. */
    public static float minBaseHeight(World world, int cx, int cz, int r) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i <= 8; i++) {
            for (int j = 0; j <= 8; j++) {
                Biome biome = world.getBiomeProvider().getBiome(new BlockPos(cx - r + 2 * r * i / 8, 64, cz - r + 2 * r * j / 8),
                        Biomes.PLAINS);
                min = Math.min(min, biome.getBaseHeight());
            }
        }
        return min;
    }
}
