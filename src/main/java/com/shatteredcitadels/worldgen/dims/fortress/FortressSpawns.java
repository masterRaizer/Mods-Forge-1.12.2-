package com.shatteredcitadels.worldgen.dims.fortress;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.List;
import java.util.Random;

/** Where the fortress stands and where its mobs (blazes, wither skeletons, magma cubes) may spawn. */
public final class FortressSpawns {

    /** Vanilla fortresses are placed one to a region of this many chunks. */
    public static final int REGION = 16;

    private FortressSpawns() {
    }

    /** @return true if the position lies in one of the volumes; an empty list holds nothing */
    public static boolean inside(List<StructureBoundingBox> boxes, BlockPos pos) {
        for (StructureBoundingBox box : boxes) {
            if (box.isVecInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** @return true inside a room, platform, bridge or corridor of one of the mod's fortresses */
    public static boolean inPiece(World world, BlockPos pos) {
        CitadelInstance fortress = fortressAt(world, pos);
        return fortress != null && inside(fortress.getParts(), pos);
    }

    /** @return true anywhere inside the outer bounds of one of the mod's fortresses */
    public static boolean inBounds(World world, BlockPos pos) {
        return fortressAt(world, pos) != null;
    }

    private static CitadelInstance fortressAt(World world, BlockPos pos) {
        if (world == null || world.isRemote) {
            return null;
        }
        CitadelInstance record = CitadelWorldSavedData.get(world).getCitadelAt(pos);
        return record != null && record.getType() == StructureType.NETHER_FORTRESS && record.contains(pos) ? record : null;
    }

    /**
     * The vanilla fortress site of a region of {@link #REGION} x {@link #REGION} chunks, by the same
     * draws as the vanilla generator: a third of the regions have one.
     *
     * @return {chunkX, chunkZ}, or null if the region has no fortress
     */
    public static int[] siteInRegion(long worldSeed, int regionX, int regionZ) {
        Random random = new Random((long) (regionX ^ regionZ << 4) ^ worldSeed);
        random.nextInt();
        if (random.nextInt(3) != 0) {
            return null;
        }
        int chunkX = (regionX << 4) + 4 + random.nextInt(8);
        int chunkZ = (regionZ << 4) + 4 + random.nextInt(8);
        return new int[] {chunkX, chunkZ};
    }

    public static boolean isSite(long worldSeed, int chunkX, int chunkZ) {
        int[] site = siteInRegion(worldSeed, chunkX >> 4, chunkZ >> 4);
        return site != null && site[0] == chunkX && site[1] == chunkZ;
    }

    /**
     * @param reach regions to look through in every direction
     * @return {chunkX, chunkZ} of the site nearest to the chunk, or null if there is none in reach
     */
    public static int[] nearestSite(long worldSeed, int chunkX, int chunkZ, int reach) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int rx = (chunkX >> 4) - reach; rx <= (chunkX >> 4) + reach; rx++) {
            for (int rz = (chunkZ >> 4) - reach; rz <= (chunkZ >> 4) + reach; rz++) {
                int[] site = siteInRegion(worldSeed, rx, rz);
                if (site == null) {
                    continue;
                }
                long dx = site[0] - chunkX;
                long dz = site[1] - chunkZ;
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = site;
                }
            }
        }
        return best;
    }
}
