package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.worldgen.BlockSet;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.catacomb.CatacombBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

import java.util.Random;

/** The 5x5 spiral shaft of a temple: from its surface floor down into the underground start hall. */
final class TempleShaft {

    private TempleShaft() {
    }

    /**
     * @param surfaceY the surface floor (the holes around the core open there)
     * @param floor    the start hall's floor
     * @param startHeight the start hall's height (its ceiling is where the ring walls begin)
     */
    static void build(BlockWriter w, Random random, BlockSet pal, Block stairs, int cx, int cz, int surfaceY, int floor,
                      int startHeight) {
        int startCeiling = floor + startHeight - 1;
        for (int y = startCeiling; y < surfaceY; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    w.set(cx + dx, y, cz + dz, ring ? pal.wall(random) : Blocks.AIR.getDefaultState());
                }
            }
        }
        for (int y = floor + 1; y <= surfaceY; y++) {
            w.set(cx, y, cz, pal.pillar());
            if (y == surfaceY) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            w.set(cx + dx, y, cz + dz, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
        }
        for (int[] step : CatacombBuilder.spiralPath(surfaceY, floor)) {
            w.set(cx + step[0], step[1], cz + step[2], stairs.getDefaultState()
                    .withProperty(BlockStairs.FACING, EnumFacing.byIndex(step[3]))
                    .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM));
        }
    }
}
