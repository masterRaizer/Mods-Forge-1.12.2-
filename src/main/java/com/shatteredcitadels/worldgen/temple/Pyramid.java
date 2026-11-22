package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

import java.util.Random;

/**
 * The surface pyramid: stepped sandstone layers around a hollow hall with four columns and a
 * terracotta floor, an entrance on the south side behind a colonnade, two towers flanking it and
 * sand drifted against the walls.
 */
final class Pyramid {

    private final BlockWriter w;
    private final Random rand;
    private final SandstonePalette pal = new SandstonePalette();
    private final int cx;
    private final int sy;
    private final int cz;
    private final int half;

    Pyramid(BlockWriter w, Random rand, int cx, int sy, int cz) {
        this.w = w;
        this.rand = rand;
        this.cx = cx;
        this.sy = sy;
        this.cz = cz;
        this.half = 13 + rand.nextInt(4);
    }

    void build() {
        IBlockState air = Blocks.AIR.getDefaultState();
        // Floor and foundation.
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 3;
                w.set(cx + dx, sy, cz + dz, ring ? ((dx + dz) % 2 == 0 ? pal.orangeTerracotta() : pal.blueTerracotta()) : pal.floor(rand));
                w.fillDown(cx + dx, cz + dz, sy - 1, pal.wall(rand), 12);
            }
        }
        // Stepped shell; everything inside it is the hall.
        for (int k = 0; k <= half; k++) {
            int r = half - k;
            int y = sy + 1 + k;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean shell = Math.abs(dx) == r || Math.abs(dz) == r;
                    w.set(cx + dx, y, cz + dz, shell ? (k % 4 == 3 ? pal.accent() : pal.wall(rand)) : air);
                }
            }
        }
        // Columns of the hall.
        for (int[] c : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            for (int y = sy + 1; y <= sy + Math.min(8, half - 6); y++) {
                w.set(cx + c[0], y, cz + c[1], pal.pillar());
            }
        }
        // Entrance through the south face, three wide and four high.
        for (int dz = half - 4; dz <= half; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = sy + 1; y <= sy + 4; y++) {
                    w.set(cx + dx, y, cz + dz, air);
                }
            }
        }
        // Colonnade before the entrance.
        int front = cz + half + 1;
        for (int dz = 0; dz <= 3; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                w.set(cx + dx, sy, front + dz, pal.floor(rand));
                w.fillDown(cx + dx, front + dz, sy - 1, pal.wall(rand), 12);
            }
            for (int px : new int[]{cx - 2, cx + 2}) {
                for (int y = sy + 1; y <= sy + 4; y++) {
                    w.set(px, y, front + dz, dz % 2 == 0 ? pal.pillar() : air);
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                w.set(cx + dx, sy + 5, front + dz, Blocks.STONE_SLAB.getDefaultState()
                        .withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.SAND));
            }
        }
        // Two towers flanking the entrance.
        int th = half / 2 + 5;
        for (int side : new int[]{-1, 1}) {
            int tx = cx + side * (half - 2);
            int tz = cz + half - 2;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    w.fillDown(tx + dx, tz + dz, sy - 1, pal.wall(rand), 12);
                    for (int y = sy; y <= sy + th; y++) {
                        boolean band = (y - sy) % 5 == 4;
                        w.set(tx + dx, y, tz + dz, band ? pal.orangeTerracotta() : pal.wall(rand));
                    }
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        w.set(tx + dx, sy + th + 1, tz + dz, Blocks.SANDSTONE_STAIRS.getDefaultState()
                                .withProperty(BlockStairs.FACING, dx == 2 ? EnumFacing.WEST : dx == -2 ? EnumFacing.EAST
                                        : dz == 2 ? EnumFacing.NORTH : EnumFacing.SOUTH));
                    }
                }
            }
        }
        // Sand drifted against the walls.
        for (int dx = -half - 3; dx <= half + 3; dx++) {
            for (int dz = -half - 3; dz <= half + 3; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d <= half || Math.abs(dx) <= 2 && dz > half || rand.nextInt(3) == 0) {
                    continue;
                }
                int height = Math.max(0, 3 - (d - half)) + rand.nextInt(2) - 1;
                for (int y = sy; y < sy + height; y++) {
                    w.setIfNotSolid(cx + dx, y, cz + dz, Blocks.SAND.getDefaultState());
                }
            }
        }
    }
}
