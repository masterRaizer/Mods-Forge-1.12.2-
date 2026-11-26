package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.compat.ForeignZones;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;

/**
 * Places lairs in the overworld. Only the one candidate chunk of a region ({@link LairPlacement}) can
 * get a lair, and only when that very chunk is populated: nothing is searched and no chunk is loaded.
 */
public final class HerobrineLairGenerator implements IWorldGenerator {

    /** Runs after the citadels and the bridges, so those always win a contested place. */
    public static final int WEIGHT = CitadelWorldGenerator.WEIGHT + 20;
    /** A castle, manor or tower site keeps this many chunks around it free of lairs. */
    private static final int SITE_CLEARANCE_CHUNKS = 6;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        if (world.isRemote || world.provider.getDimension() != 0 || !HerobrineSettings.lairsEnabled()) {
            return;
        }
        WorldType type = world.getWorldType();
        if (type == WorldType.FLAT || type == WorldType.DEBUG_ALL_BLOCK_STATES) {
            return;
        }
        try {
            LairPlacement.Site site = LairPlacement.siteAt(world.getSeed(), chunkX, chunkZ,
                    HerobrineSettings.lairSpacingChunks(), HerobrineSettings.lairChance());
            if (site == null) {
                return;
            }
            BlockPos spawn = world.getSpawnPoint();
            if (LairPlacement.isNearSpawn(chunkX, chunkZ, spawn.getX() >> 4, spawn.getZ() >> 4,
                    HerobrineSettings.lairSpawnClearanceChunks())
                    || CitadelWorldGenerator.isNearSiteCandidate(world.getSeed(), chunkX, chunkZ,
                    ConfigCache.get().getCastleSectionSize(), SITE_CLEARANCE_CHUNKS)) {
                return;
            }
            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;
            if (!isLand(world.getBiomeProvider().getBiome(new BlockPos(x, 0, z)))) {
                return;
            }
            // The chunk being populated is loaded, so its own centre column may be read.
            int ground = groundAt(world, x, z);
            if (!LairPlacement.isGroundUsable(ground)) {
                return;
            }
            HerobrineLairPlan plan = new HerobrineLairPlan(site.variant, x, z, ground, site.seed, 0);
            if (!CitadelWorldGenerator.isAreaFree(world, plan.footprint()) || ForeignZones.blocks(world, plan.footprint())) {
                return;
            }
            StructureJobs.get(world).submit(plan);
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Herobrine lair planning failed in chunk [{}, {}]", chunkX, chunkZ, e);
        }
    }

    /** @return the level of the top terrain block of a loaded column, or -1 where water or lava covers it */
    public static int groundAt(World world, int x, int z) {
        BlockPos top = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
        IBlockState state = world.getBlockState(top.down());
        if (state.getMaterial().isLiquid() || world.getBlockState(top).getMaterial().isLiquid()) {
            return -1;
        }
        return top.getY() - 1;
    }

    /** @return false for water, shore and mushroom biomes and for anything that is not the overworld */
    public static boolean isLand(Biome biome) {
        return biome != null && !has(biome, BiomeDictionary.Type.OCEAN) && !has(biome, BiomeDictionary.Type.RIVER)
                && !has(biome, BiomeDictionary.Type.BEACH) && !has(biome, BiomeDictionary.Type.MUSHROOM)
                && !has(biome, BiomeDictionary.Type.NETHER) && !has(biome, BiomeDictionary.Type.END);
    }

    private static boolean has(Biome biome, BiomeDictionary.Type type) {
        return BiomeDictionary.hasType(biome, type);
    }
}
