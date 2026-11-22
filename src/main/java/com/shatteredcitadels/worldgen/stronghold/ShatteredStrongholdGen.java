package com.shatteredcitadels.worldgen.stronghold;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStronghold;

import java.util.Random;

/**
 * Replaces the vanilla stronghold generator of the Overworld (and of world types that use it, such
 * as Biomes O' Plenty's). It keeps the vanilla sites and the vanilla nearest-site lookup, so the eye
 * of ender and {@code /locate Stronghold} lead to them, builds none of the vanilla pieces, and
 * queues our stronghold when a site chunk is populated.
 */
public class ShatteredStrongholdGen extends MapGenStronghold {

    private static final long SALT = 0x3C6EF372FE94F82BL;

    /** No vanilla stronghold pieces are ever started. */
    @Override
    public boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        return false;
    }

    /** @return true if a vanilla stronghold started before the mod (older world) overlaps the box. */
    public boolean hasLegacyStartIn(net.minecraft.world.gen.structure.StructureBoundingBox box) {
        for (net.minecraft.world.gen.structure.StructureStart start : structureMap.values()) {
            if (start != null && start.getBoundingBox() != null && start.getBoundingBox().intersectsWith(box)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if the chunk is one of the vanilla stronghold sites. */
    public boolean isSite(int chunkX, int chunkZ) {
        return super.canSpawnStructureAtCoords(chunkX, chunkZ);
    }

    @Override
    public synchronized boolean generateStructure(World worldIn, Random randomIn, ChunkPos chunkCoord) {
        // Vanilla strongholds already started in an older world keep growing their pieces.
        boolean legacy = super.generateStructure(worldIn, randomIn, chunkCoord);
        if (worldIn.isRemote || worldIn.provider.getDimension() != 0) {
            return legacy;
        }
        this.world = worldIn;
        if (!isSite(chunkCoord.x, chunkCoord.z)) {
            return legacy;
        }
        long seed = worldIn.getSeed() ^ ChunkPos.asLong(chunkCoord.x, chunkCoord.z) ^ SALT;
        StrongholdPlan plan = StrongholdPlan.plan(worldIn, chunkCoord.x, chunkCoord.z, seed);
        if (hasLegacyStartIn(plan.footprint())) {
            ShatteredCitadelsMod.LOGGER.info("Stronghold site {},{} keeps its vanilla stronghold", chunkCoord.x, chunkCoord.z);
        } else if (CitadelWorldGenerator.isAreaFreeForStronghold(worldIn, plan.footprint())) {
            StructureJobs.get(worldIn).submit(plan);
        } else {
            ShatteredCitadelsMod.LOGGER.warn("Stronghold site {},{} is occupied by a structure from before the site reservation",
                    chunkCoord.x, chunkCoord.z);
        }
        return legacy;
    }
}
