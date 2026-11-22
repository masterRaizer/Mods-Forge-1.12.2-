package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.Random;

/**
 * Replaces the vanilla scattered-feature generator: on desert sites (as vanilla chooses the desert
 * pyramid) it builds our desert temple instead; jungle temples, witch huts and igloos stay vanilla,
 * and so does the witch spawning of swamp huts.
 */
public class ScatteredFeatureGen extends MapGenScatteredFeature {

    private static final long SALT = 0x2545F4914F6CDD1DL;

    /** @return true for the biomes where vanilla places the desert pyramid. */
    public static boolean isOurs(Biome biome) {
        return biome == Biomes.DESERT || biome == Biomes.DESERT_HILLS;
    }

    private Biome siteBiome(int chunkX, int chunkZ) {
        return world.getBiomeProvider().getBiome(new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8));
    }

    /** Vanilla sites, except the desert ones: those get our temple. */
    @Override
    public boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        // Only the overworld gets our temple; other dimensions with an overworld generator keep the vanilla one.
        return super.canSpawnStructureAtCoords(chunkX, chunkZ)
                && (world.provider.getDimension() != 0 || !isOurs(siteBiome(chunkX, chunkZ)));
    }

    private boolean isOurSite(int chunkX, int chunkZ) {
        return world.provider.getDimension() == 0 && super.canSpawnStructureAtCoords(chunkX, chunkZ) && isOurs(siteBiome(chunkX, chunkZ));
    }

    /** @return true if a vanilla temple started before the mod (older world) overlaps the box. */
    public boolean hasLegacyStartIn(StructureBoundingBox box) {
        for (StructureStart start : structureMap.values()) {
            if (start != null && start.getBoundingBox() != null && start.getBoundingBox().intersectsWith(box)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean generateStructure(World worldIn, Random randomIn, ChunkPos chunkCoord) {
        boolean vanilla = super.generateStructure(worldIn, randomIn, chunkCoord);
        if (worldIn.isRemote || worldIn.provider.getDimension() != 0) {
            return vanilla;
        }
        this.world = worldIn;
        if (!isOurSite(chunkCoord.x, chunkCoord.z)) {
            return vanilla;
        }
        long seed = worldIn.getSeed() ^ ChunkPos.asLong(chunkCoord.x, chunkCoord.z) ^ SALT;
        DesertTemplePlan plan = DesertTemplePlan.plan(worldIn, chunkCoord.x, chunkCoord.z, seed);
        if (hasLegacyStartIn(plan.footprint())) {
            ShatteredCitadelsMod.LOGGER.info("Desert site {},{} keeps its vanilla pyramid", chunkCoord.x, chunkCoord.z);
        } else if (CitadelWorldGenerator.isAreaFree(worldIn, plan.footprint())) {
            StructureJobs.get(worldIn).submit(plan);
        }
        return vanilla;
    }
}
