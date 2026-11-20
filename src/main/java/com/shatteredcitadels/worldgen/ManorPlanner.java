package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.worldgen.catacomb.CatacombBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Picks the site of a gothic manor: the anchor chunks give the ground height (loaded terrain
 * only), the biome provider checks the rest of the ~110 x 140 block area without loading chunks;
 * the manor levels the ground itself.
 */
public final class ManorPlanner {

    private static final int MIN_BASE_Y = 20;

    private ManorPlanner() {
    }

    public static ManorPlan plan(World world, int chunkX, int chunkZ, Random random, boolean ignoreTerrain) {
        int minX = (chunkX << 4) + 1;
        int minZ = (chunkZ << 4) + 1;
        int centerX = minX + 14;
        int centerZ = minZ + 14;
        Biome biome = world.getBiome(new BlockPos(centerX, 64, centerZ));
        if (!ignoreTerrain && CitadelWorldGenerator.isUnsuitableBiome(biome)) {
            return null;
        }
        int[] heights = CitadelWorldGenerator.sampleHeights(world, minX, minZ, 29);
        if (heights == null) {
            if (!ignoreTerrain) {
                return null;
            }
            heights = new int[]{world.getSeaLevel() + 1};
        }
        if (!ignoreTerrain && heights[heights.length - 1] - heights[0] > 20) {
            ShatteredCitadelsMod.LOGGER.debug("Manor site {},{} rejected: slope", centerX, centerZ);
            return null;
        }
        int baseY = Math.max(MIN_BASE_Y, heights[heights.length / 2]);
        if (baseY + 60 > BlockWriter.MAX_Y) {
            return null;
        }
        int halfX = ManorBuilder.WIDTH / 2;
        int halfZ = ManorBuilder.DEPTH / 2 + ManorBuilder.TOWER_REACH;
        if (!ignoreTerrain && !CastlePlanner.isLandSite(world, centerX, centerZ, halfX, halfZ)) {
            return null;
        }
        int levels = random.nextDouble() < ConfigCache.get().getCatacombManorChance()
                ? ConfigCache.get().rollCatacombLevels(random) : 0;
        int top = ManorBuilder.cellarY(baseY) - 10;
        while (levels > 0 && CatacombBuilder.levelY(top, levels - 1) - CatacombBuilder.SANCTUM_DEPTH < BlockWriter.MIN_Y + 1) {
            levels--;
        }
        return new ManorPlan(centerX - halfX, centerZ - ManorBuilder.DEPTH / 2, baseY, random.nextLong(),
                world.provider.getDimension(), levels);
    }
}
