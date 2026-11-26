package com.shatteredcitadels.worldgen.dims;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * Finds the ground a structure stands on where "the top block" is the wrong answer: under the
 * roof of the Nether and on the islands of the End. The rules are pure functions over a column;
 * the world adapter reads only the columns it is given (the populated chunk and its +x/+z
 * neighbours).
 */
public final class GroundFinder {

    public static final int SOLID = 0;
    public static final int AIR = 1;
    public static final int LIQUID = 2;
    /** No usable ground in the column. */
    public static final int NONE = -1;

    /** Lowest standing height in the Nether: one above the surface of the lava sea. */
    public static final int NETHER_MIN_Y = 32;
    /** Nothing is built or cleared above this in the Nether (the bedrock roof starts at 123). */
    public static final int NETHER_ROOF = 116;
    public static final int NETHER_HEADROOM = 6;

    /** One block column: {@link #SOLID}, {@link #AIR} or {@link #LIQUID} at a height. */
    public interface Column {
        int at(int y);
    }

    private GroundFinder() {
    }

    /**
     * @return the lowest Y from {@code minY} with a solid block below and {@code headroom} free
     * blocks from it up, all below {@code maxY}; {@link #NONE} over lava or inside solid rock
     */
    public static int cavernFloor(Column column, int minY, int maxY, int headroom) {
        for (int y = minY; y + headroom - 1 <= maxY; y++) {
            if (column.at(y - 1) != SOLID) {
                continue;
            }
            boolean free = true;
            for (int up = 0; up < headroom && free; up++) {
                free = column.at(y + up) == AIR;
            }
            if (free) {
                return y;
            }
        }
        return NONE;
    }

    /** @return the Y above the top solid block, or {@link #NONE} for void or a liquid surface */
    public static int surface(Column column, int topY, int minY) {
        for (int y = topY; y >= minY; y--) {
            int kind = column.at(y);
            if (kind == SOLID) {
                return y + 1;
            }
            if (kind == LIQUID) {
                return NONE;
            }
        }
        return NONE;
    }

    /**
     * @return the usable samples sorted, or null if more than one in eight has no ground
     * (lava, void) or nothing is usable at all
     */
    public static int[] usable(int[] samples) {
        int bad = 0;
        for (int sample : samples) {
            if (sample == NONE) {
                bad++;
            }
        }
        if (bad > samples.length / 8 || bad == samples.length) {
            return null;
        }
        int[] good = new int[samples.length - bad];
        int n = 0;
        for (int sample : samples) {
            if (sample != NONE) {
                good[n++] = sample;
            }
        }
        Arrays.sort(good);
        return good;
    }

    // ------------------------------------------------------------------ world adapters

    /** @return sorted ground heights on a 4-block grid like the overworld sampler, or null if the site has no ground */
    public static int[] sample(World world, DimKind kind, int minX, int minZ, int width) {
        int steps = width / 4 + 1;
        int[] samples = new int[steps * steps];
        int n = 0;
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < steps; j++) {
                samples[n++] = groundAt(world, kind, minX + Math.min(width - 1, i * 4), minZ + Math.min(width - 1, j * 4));
            }
        }
        return usable(samples);
    }

    /** @return the standing height of one column, or {@link #NONE} */
    public static int groundAt(World world, DimKind kind, int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Column column = y -> classify(world.getBlockState(cursor.setPos(x, y, z)));
        if (kind == DimKind.NETHER) {
            return cavernFloor(column, NETHER_MIN_Y, NETHER_ROOF - 10, NETHER_HEADROOM);
        }
        int y = surface(column, Math.min(250, world.getHeight(x, z) + 1), 5);
        if (y != NONE && kind == DimKind.END
                && world.getBlockState(cursor.setPos(x, y - 1, z)).getBlock() != Blocks.END_STONE) {
            // Obsidian pillars, end cities, chorus trees: only bare end stone carries a structure.
            return NONE;
        }
        return y;
    }

    static int classify(IBlockState state) {
        Material material = state.getMaterial();
        if (material.isLiquid()) {
            return LIQUID;
        }
        return material.isSolid() && material != Material.LEAVES && material != Material.WOOD ? SOLID : AIR;
    }
}
