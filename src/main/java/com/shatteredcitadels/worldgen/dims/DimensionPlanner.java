package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.CastlePlan;
import com.shatteredcitadels.worldgen.CitadelPlan;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.temple.DesertTemplePlan;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkGeneratorEnd;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * Plans the overworld structures on the ground of another dimension. Reads only the anchor chunk
 * and its +x/+z neighbours; never a biome, never another chunk.
 */
public final class DimensionPlanner {

    private static final int MIN_BASE_Y = 12;

    private DimensionPlanner() {
    }

    /** @return the floor count shortened (never below {@code minFloors}) so the roof and battlements stay below the ceiling */
    public static int fitFloors(int baseY, int floors, int minFloors, int arenaHeight, int ceiling) {
        int fitted = floors;
        while (fitted > minFloors && roofY(baseY, fitted, arenaHeight) + 4 > ceiling) {
            fitted--;
        }
        return fitted;
    }

    public static int roofY(int baseY, int floors, int arenaHeight) {
        return baseY + (floors - 1) * CitadelPlan.FLOOR_HEIGHT + arenaHeight;
    }

    /** The build ceiling of the dimension: the Nether keeps everything below its bedrock roof. */
    public static int ceiling(DimKind kind) {
        return kind == DimKind.NETHER ? GroundFinder.NETHER_ROOF : BlockWriter.MAX_Y;
    }

    /**
     * @param forced a dimension archetype to use, or null for the dimension's own
     * @param force  true for admin commands: build even without proper ground
     * @return the tower, or null if the site has no ground or no room
     */
    public static StructurePlan tower(World world, DimKind kind, int chunkX, int chunkZ, Random random,
                                      CitadelArchetype forced, boolean force) {
        CitadelPlan plan = citadel(world, kind, (chunkX << 4) + 1, (chunkZ << 4) + 1, random, forced, force, 0);
        return plan == null ? null : new DimensionPlan(kind, plan);
    }

    /** A walled castle; in the End only where the generator has island under the whole bailey. */
    public static StructurePlan castle(World world, DimKind kind, int chunkX, int chunkZ, Random random, boolean force) {
        CitadelPlan keep = citadel(world, kind, (chunkX << 4) + 1, (chunkZ << 4) + 1, random, null, force, 1);
        if (keep == null) {
            return null;
        }
        CastlePlan castle = new CastlePlan(keep, 44 + random.nextInt(9), 44 + random.nextInt(9), 10 + random.nextInt(4),
                random.nextLong());
        if (!force && kind == DimKind.END && !isEndIsland(world, castle.footprint())) {
            return null;
        }
        return new DimensionPlan(kind, castle);
    }

    /** The mod's desert temple in Atum's stone. */
    public static StructurePlan temple(World world, int chunkX, int chunkZ, long seed, Boolean pharaoh) {
        DesertTemplePlan plan = DesertTemplePlan.plan(world, chunkX, chunkZ, seed, pharaoh);
        return plan == null ? null : new DimensionPlan(DimKind.ATUM, plan);
    }

    private static CitadelPlan citadel(World world, DimKind kind, int minX, int minZ, Random random,
                                       CitadelArchetype forced, boolean force, int floorOffset) {
        ConfigCache.Snapshot config = ConfigCache.get();
        int width = 25 + 2 * random.nextInt(3);
        int floors = config.rollFloorCount(random, false);
        int arenaHeight = 12 + random.nextInt(3);
        long buildSeed = random.nextLong();
        int[] heights = GroundFinder.sample(world, kind, minX, minZ, width);
        if (heights == null) {
            if (!force) {
                return null;
            }
            int centre = GroundFinder.groundAt(world, kind, minX + width / 2, minZ + width / 2);
            heights = new int[]{centre != GroundFinder.NONE ? centre : kind == DimKind.ATUM ? world.getSeaLevel() + 1 : 64};
        }
        if (!force && heights[heights.length - 1] - heights[0] > config.getMaxFoundationDrop()) {
            return null;
        }
        int baseY = Math.max(MIN_BASE_Y, heights[heights.length / 2]);
        int ceiling = ceiling(kind);
        int minFloors = kind == DimKind.NETHER ? 2 : config.getMinFloors();
        floors = fitFloors(baseY, Math.max(floors, minFloors), minFloors, arenaHeight, ceiling);
        int roof = roofY(baseY, floors, arenaHeight);
        if (roof + 4 > ceiling) {
            return null;
        }
        boolean wings = kind == DimKind.ATUM && config.getTowerWingChance() > 0.0D;
        CitadelArchetype archetype = forced != null ? forced : kind.archetype();
        return new CitadelPlan(minX, minZ, width, baseY, floors, arenaHeight, false, archetype, roof + 4, 0, buildSeed,
                world.provider.getDimension(), wings, floorOffset);
    }

    /** Asks the vanilla End generator (no chunk is loaded); other generators give no answer, so no castle. */
    private static boolean isEndIsland(World world, StructureBoundingBox box) {
        if (!(world instanceof WorldServer)) {
            return false;
        }
        IChunkGenerator generator = ((WorldServer) world).getChunkProvider().chunkGenerator;
        if (!(generator instanceof ChunkGeneratorEnd)) {
            return false;
        }
        for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                if (!((ChunkGeneratorEnd) generator).isIslandChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
