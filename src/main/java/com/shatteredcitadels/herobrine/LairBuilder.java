package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Writes one lair. {@code ground} is the level of the top terrain block at the centre: floors of the
 * surface variants replace that block, the shrine lies well below it. Runs on a worker thread, so it
 * only talks to the {@link BlockWriter}.
 */
final class LairBuilder {

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final IBlockState BRICK = Blocks.STONEBRICK.getDefaultState();
    private static final IBlockState MOSSY = BRICK.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
    private static final IBlockState CRACKED = BRICK.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
    private static final IBlockState CHISELED = BRICK.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CHISELED);
    private static final IBlockState COBBLE = Blocks.COBBLESTONE.getDefaultState();
    private static final IBlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.getDefaultState();
    private static final IBlockState OBSIDIAN = Blocks.OBSIDIAN.getDefaultState();
    private static final IBlockState NETHER_BRICK = Blocks.NETHER_BRICK.getDefaultState();
    private static final IBlockState BARS = Blocks.IRON_BARS.getDefaultState();
    static final IBlockState GOLD = Blocks.GOLD_BLOCK.getDefaultState();
    static final IBlockState NETHERRACK = Blocks.NETHERRACK.getDefaultState();
    static final IBlockState TORCH = Blocks.REDSTONE_TORCH.getDefaultState();
    /** Depth of the shrine floor below the ground. */
    static final int SHRINE_DEPTH = 14;

    private final BlockWriter writer;
    private final Random random;
    private final int ox;
    private final int oz;
    private final int ground;
    private BlockPos altar;
    private BlockPos chest;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    LairBuilder(BlockWriter writer, Random random, int ox, int oz, int ground) {
        this.writer = writer;
        this.random = random;
        this.ox = ox;
        this.oz = oz;
        this.ground = ground;
    }

    /** @return the netherrack of the altar */
    BlockPos altar() {
        return altar;
    }

    BlockPos chest() {
        return chest;
    }

    /** @return {minX, minY, minZ, maxX, maxY, maxZ} of everything written so far */
    int[] touched() {
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    void build(LairVariant variant) {
        switch (variant) {
            case TOWER:
                tower();
                break;
            case SHRINE:
                shrine();
                break;
            default:
                keep();
                break;
        }
    }

    // ------------------------------------------------------------------ primitives

    private void touch(int x, int y, int z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    /** Offsets are relative to the centre column; {@code y} is absolute. */
    private void set(int dx, int y, int dz, IBlockState state) {
        touch(ox + dx, y, oz + dz);
        writer.set(ox + dx, y, oz + dz, state);
    }

    private void fillDown(int dx, int dz, int topY, IBlockState state, int depth) {
        touch(ox + dx, topY, oz + dz);
        touch(ox + dx, topY - depth + 1, oz + dz);
        writer.fillDown(ox + dx, oz + dz, topY, state, depth);
    }

    private void clearColumn(int dx, int dz, int fromY, int toY) {
        touch(ox + dx, fromY, oz + dz);
        touch(ox + dx, toY, oz + dz);
        writer.clearColumn(ox + dx, oz + dz, fromY, toY);
    }

    private IBlockState brick() {
        int roll = random.nextInt(10);
        return roll < 5 ? BRICK : roll < 8 ? CRACKED : MOSSY;
    }

    /** The altar: gold 3x3 at {@code y}, netherrack and four redstone torches on it. */
    private void altar(int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(dx, y, dz, GOLD);
            }
        }
        set(0, y + 1, 0, NETHERRACK);
        for (int[] torch : AltarPattern.torches()) {
            set(torch[0], y + 1, torch[1], TORCH);
        }
        altar = new BlockPos(ox, y + 1, oz);
    }

    private void chest(int dx, int y, int dz, EnumFacing facing) {
        set(dx, y, dz, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing));
        chest = new BlockPos(ox + dx, y, oz + dz);
    }

    // ------------------------------------------------------------------ variants

    private void keep() {
        int half = LairVariant.KEEP.half;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                clearColumn(dx, dz, ground + 1, ground + LairVariant.KEEP.above);
                set(dx, ground, dz, brick());
                fillDown(dx, dz, ground - 1, COBBLE, LairVariant.KEEP.below - 1);
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                if (ax >= half - 2 && az >= half - 2) {
                    keepTower(dx, dz, ax, az, half);
                } else if (ax == half || az == half) {
                    keepWall(dx, dz, half);
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = ground + 1; y <= ground + 3; y++) {
                    set(sx * 3, y, sz * 3, brick());
                }
                set(sx * 3, ground + 4, sz * 3, CHISELED);
            }
        }
        altar(ground);
        chest(0, ground + 1, -(half - 1), EnumFacing.SOUTH);
    }

    /** Curtain wall: ruined to a random height, breached here and there, open at the south gate. */
    private void keepWall(int dx, int dz, int half) {
        if (dz == half && Math.abs(dx) <= 1) {
            return;
        }
        int height = random.nextInt(8) == 0 ? random.nextInt(2) : 2 + random.nextInt(4);
        if (dx == 0 && dz == -half) {
            height = Math.max(height, 3);
        }
        for (int y = ground + 1; y <= ground + height; y++) {
            set(dx, y, dz, brick());
        }
    }

    /** Corner tower 3x3: a hollow shaft with a doorway on its side that faces the yard. */
    private void keepTower(int dx, int dz, int ax, int az, int half) {
        if (ax == half - 1 && az == half - 1) {
            return;
        }
        boolean doorway = ax == half - 2 && az == half - 1;
        int height = 7 + random.nextInt(3);
        for (int y = ground + (doorway ? 3 : 1); y <= ground + height; y++) {
            set(dx, y, dz, brick());
        }
    }

    private void tower() {
        int wall = LairVariant.TOWER.half - 1;
        int top = ground + 13;
        for (int dx = -wall - 1; dx <= wall + 1; dx++) {
            for (int dz = -wall - 1; dz <= wall + 1; dz++) {
                clearColumn(dx, dz, ground + 1, ground + LairVariant.TOWER.above);
            }
        }
        for (int dx = -wall; dx <= wall; dx++) {
            for (int dz = -wall; dz <= wall; dz++) {
                set(dx, ground, dz, OBSIDIAN);
                fillDown(dx, dz, ground - 1, OBSIDIAN, LairVariant.TOWER.below - 1);
                boolean edge = Math.abs(dx) == wall || Math.abs(dz) == wall;
                if (edge) {
                    for (int y = ground + 1; y <= top; y++) {
                        set(dx, y, dz, OBSIDIAN);
                    }
                    if ((dx + dz & 1) == 0) {
                        set(dx, top + 1, dz, OBSIDIAN);
                    }
                } else {
                    set(dx, ground + 7, dz, OBSIDIAN);
                    set(dx, top, dz, OBSIDIAN);
                }
            }
        }
        // Doorway to the south, slits on the other sides and upstairs.
        set(0, ground + 1, wall, AIR);
        set(0, ground + 2, wall, AIR);
        set(wall, ground + 3, 0, BARS);
        set(-wall, ground + 3, 0, BARS);
        set(wall, ground + 10, 0, BARS);
        set(-wall, ground + 10, 0, BARS);
        set(0, ground + 10, wall, BARS);
        set(0, ground + 10, -wall, BARS);
        // Ladder on the north wall, through a hole in the upper floor.
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.SOUTH);
        for (int y = ground + 1; y <= ground + 7; y++) {
            set(0, y, -(wall - 1), ladder);
        }
        altar(ground);
        chest(0, ground + 8, wall - 1, EnumFacing.NORTH);
    }

    private void shrine() {
        int inner = LairVariant.SHRINE.half - 1;
        int floor = ground - SHRINE_DEPTH;
        for (int dx = -inner - 1; dx <= inner + 1; dx++) {
            for (int dz = -inner - 1; dz <= inner + 1; dz++) {
                boolean inside = Math.abs(dx) <= inner && Math.abs(dz) <= inner;
                for (int y = floor; y <= floor + 6; y++) {
                    set(dx, y, dz, inside && y > floor && y < floor + 6 ? AIR : brick());
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = floor + 1; y <= floor + 5; y++) {
                    set(sx * 3, y, sz * 3, NETHER_BRICK);
                }
            }
        }
        // The shaft: a lined 3x3 column from the chamber ceiling to the surface, a ladder in its middle.
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.NORTH);
        for (int y = floor + 6; y <= ground; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = inner - 1; dz <= inner + 1; dz++) {
                    set(dx, y, dz, brick());
                }
            }
        }
        for (int y = floor + 1; y <= ground; y++) {
            set(0, y, inner, ladder);
        }
        // The marker above the shaft: four posts, a back wall and a roof.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = inner - 2; dz <= inner + 1; dz++) {
                clearColumn(dx, dz, ground + 1, ground + LairVariant.SHRINE.above);
            }
        }
        for (int y = ground + 1; y <= ground + 3; y++) {
            set(-1, y, inner - 1, MOSSY_COBBLE);
            set(1, y, inner - 1, MOSSY_COBBLE);
            set(-1, y, inner + 1, MOSSY_COBBLE);
            set(1, y, inner + 1, MOSSY_COBBLE);
            set(0, y, inner + 1, brick());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = inner - 1; dz <= inner + 1; dz++) {
                set(dx, ground + 4, dz, dx == 0 && dz == inner ? CHISELED : MOSSY);
            }
        }
        altar(floor);
        chest(0, floor + 1, -inner, EnumFacing.SOUTH);
    }
}
