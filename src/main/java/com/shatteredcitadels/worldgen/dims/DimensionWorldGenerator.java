package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.compat.ForeignZones;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;

/**
 * Towers, castles and temples of the Nether, the outer End and Atum. A chunk is a site only by
 * the seed and its coordinates; the ground is measured in that chunk and its +x/+z neighbours and
 * the structure itself is built through the job queue as its chunks load.
 */
public final class DimensionWorldGenerator implements IWorldGenerator {

    public static final int WEIGHT = 100;
    /** Blocks around the anchor chunk a tower may reach; used for the cheap End distance check. */
    private static final int TOWER_REACH = 40;

    private boolean generating;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        if (world.isRemote || generating) {
            return;
        }
        DimKind kind = DimsConfig.active(world);
        ConfigCache.Snapshot config = ConfigCache.get();
        if (kind == null || !config.isCitadelGenerationEnabled()
                || world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            return;
        }
        int section = DimSites.sectionSize(config.getCastleSectionSize(), DimsConfig.spacing(kind));
        if (DimSites.candidateIndex(world.getSeed(), kind, chunkX, chunkZ, section) < 0) {
            return;
        }
        int x = chunkX << 4;
        int z = chunkZ << 4;
        if (kind == DimKind.END && !DimSites.isOuterEnd(x - TOWER_REACH, z - TOWER_REACH, x + TOWER_REACH, z + TOWER_REACH,
                DimsConfig.endMinDistance())) {
            return;
        }
        generating = true;
        try {
            StructureJobs jobs = StructureJobs.get(world);
            CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
            // The spare candidates of a section stand down once one of them is built.
            int minDistance = Math.max(config.getMinDistanceBetweenCitadelsChunks(), section);
            if (!data.isFarEnough(chunkX, chunkZ, minDistance) || jobs.isNear(chunkX, chunkZ, minDistance)) {
                return;
            }
            StructurePlan plan = plan(world, kind, chunkX, chunkZ, DimSites.buildSeed(world.getSeed(), kind, chunkX, chunkZ));
            if (plan != null && allowed(kind, plan.footprint()) && CitadelWorldGenerator.isAreaFree(world, plan.footprint())
                    && !ForeignZones.blocks(world, plan.footprint())) {
                jobs.submit(plan);
            }
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Dimension structure planning failed in chunk [{}, {}]", chunkX, chunkZ, e);
        } finally {
            generating = false;
        }
    }

    private static StructurePlan plan(World world, DimKind kind, int chunkX, int chunkZ, long buildSeed) {
        Random random = new Random(buildSeed);
        double roll = random.nextDouble();
        StructurePlan plan = null;
        if (roll < DimsConfig.castleChance(kind)) {
            plan = DimensionPlanner.castle(world, kind, chunkX, chunkZ, random, false);
        } else if (roll < DimsConfig.templeChance(kind)) {
            plan = DimensionPlanner.temple(world, chunkX, chunkZ, random.nextLong(), null);
        }
        if (plan == null) {
            // No castle or temple (not rolled, or no room for one): a tower still fits here.
            plan = DimensionPlanner.tower(world, kind, chunkX, chunkZ, new Random(buildSeed ^ 0x51ED270B27L), null, false);
        }
        return plan;
    }

    /** The End keeps the whole structure, not only its anchor, away from the central island. */
    private static boolean allowed(DimKind kind, StructureBoundingBox box) {
        return kind != DimKind.END || DimSites.isOuterEnd(box.minX, box.minZ, box.maxX, box.maxZ, DimsConfig.endMinDistance());
    }
}
