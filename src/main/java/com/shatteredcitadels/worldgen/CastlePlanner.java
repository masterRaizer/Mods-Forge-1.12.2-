package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.proxy.CommonProxy;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks the site of a walled castle. The keep is measured like a citadel (on the loaded anchor
 * chunks); the much larger bailey is checked through the biome provider only, which never loads
 * a chunk, and the castle terraforms whatever relief remains (fills below, clears above).
 */
public final class CastlePlanner {

    private static volatile List<Biome> allowed;

    private CastlePlanner() {
    }

    /**
     * @param ignoreTerrain true for admin commands: skip biome rejection
     * @return the plan, or null if the site is unsuitable
     */
    public static CastlePlan plan(World world, int chunkX, int chunkZ, Random random, CitadelArchetype archetype,
                                  boolean ignoreTerrain) {
        CitadelPlan keep = CommonProxy.WORLD_GENERATOR.planCitadel(world, (chunkX << 4) + 1, (chunkZ << 4) + 1, random,
                Boolean.FALSE, null, archetype, ignoreTerrain, 1);
        if (keep == null) {
            return null;
        }
        int halfX = 44 + random.nextInt(13);
        int halfZ = 44 + random.nextInt(13);
        int wallHeight = 10 + random.nextInt(4);
        if (!ignoreTerrain && !isLandSite(world, keep.centerX(), keep.centerZ(), halfX + CastlePlan.MOAT_REACH,
                halfZ + CastlePlan.MOAT_REACH)) {
            return null;
        }
        return new CastlePlan(keep, halfX, halfZ, wallHeight, random.nextLong());
    }

    /**
     * Samples the biome provider (never loads chunks) on a 9x9 grid over the castle: the site is
     * rejected when more than 20 % is water (ocean, river, swamp) or more than 35 % is mountains.
     * A stream or a hill here and there is fine: the castle fills and levels its ground.
     */
    static boolean isLandSite(World world, int cx, int cz, int rx, int rz) {
        int samples = 0;
        int water = 0;
        int mountains = 0;
        for (int i = 0; i <= 8; i++) {
            for (int j = 0; j <= 8; j++) {
                int x = cx - rx + (2 * rx * i) / 8;
                int z = cz - rz + (2 * rz * j) / 8;
                Biome biome = world.getBiomeProvider().getBiome(new BlockPos(x, 64, z), Biomes.PLAINS);
                samples++;
                if (!allowedBiomes().contains(biome)) {
                    if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.MOUNTAIN)) {
                        mountains++;
                    } else {
                        water++;
                    }
                }
            }
        }
        boolean ok = water * 5 <= samples && mountains * 100 <= samples * 35;
        if (!ok) {
            ShatteredCitadelsMod.LOGGER.debug("Castle site {},{} rejected: {} water and {} mountain samples of {}",
                    cx, cz, water, mountains, samples);
        }
        return ok;
    }

    /** Land biomes without steep relief: no ocean, river, beach, mushroom isle, mountains or swamp. */
    private static List<Biome> allowedBiomes() {
        List<Biome> list = allowed;
        if (list == null) {
            list = new ArrayList<>();
            for (Biome biome : ForgeRegistries.BIOMES.getValuesCollection()) {
                if (!CitadelWorldGenerator.isUnsuitableBiome(biome)
                        && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.MOUNTAIN)
                        && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SWAMP)
                        && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.WATER)) {
                    list.add(biome);
                }
            }
            allowed = list;
        }
        return list;
    }
}
