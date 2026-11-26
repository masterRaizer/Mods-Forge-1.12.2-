package com.shatteredcitadels.worldgen.bridge;

import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.BlockWoodSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws one river bridge. Positions are given as {@code (a, y, c)}: {@code a} along the deck,
 * {@code c} sideways from the centre line. The walkway is {@code style.width} wide; one more column
 * on each side carries the railing, so riders are not squeezed. Everything that meets the terrain
 * (piers, abutments, approach steps) uses the writer's column operations.
 */
public final class BridgeBuilder {

    /** Deck blocks on land past each end of the river run. */
    public static final int ABUTMENT = 3;
    /** Room past the approaches and beside the deck for a camp. */
    private static final int END_MARGIN = 6;
    private static final int SIDE_MARGIN = 8;

    private final BlockWriter w;
    private final Random rand;
    private final BridgeStyle style;
    private final boolean alongX;
    private final int start;
    private final int end;
    private final int cross;
    private final int seaLevel;
    private final int deckY;
    private final int half;
    private final int edge;
    private final int first;
    private final int last;
    private final List<Integer> piers = new ArrayList<>();

    public BridgeBuilder(BlockWriter w, Random rand, BridgeStyle style, boolean alongX, int start, int end, int cross, int seaLevel) {
        this.w = w;
        this.rand = rand;
        this.style = style;
        this.alongX = alongX;
        this.start = start;
        this.end = end;
        this.cross = cross;
        this.seaLevel = seaLevel;
        this.deckY = deckY(style, seaLevel);
        this.half = style.width / 2;
        this.edge = half + 1;
        this.first = start - ABUTMENT;
        this.last = end + ABUTMENT;
    }

    /** @return the y of the deck blocks: {@code deckRise} above the water surface block. */
    public static int deckY(BridgeStyle style, int seaLevel) {
        return seaLevel - 1 + style.deckRise;
    }

    /** @return the number of steps from the deck down to the bank at each end. */
    public static int approachLength(BridgeStyle style) {
        return style.deckRise - 1;
    }

    public static StructureBoundingBox footprint(BridgeStyle style, boolean alongX, int start, int end, int cross, int seaLevel) {
        int reach = ABUTMENT + approachLength(style) + END_MARGIN;
        int side = style.width / 2 + 1 + SIDE_MARGIN;
        int minY = Math.max(BlockWriter.MIN_Y, seaLevel - 14);
        int maxY = Math.min(BlockWriter.MAX_Y, deckY(style, seaLevel) + 9);
        return alongX
                ? new StructureBoundingBox(start - reach, minY, cross - side, end + reach, maxY, cross + side)
                : new StructureBoundingBox(cross - side, minY, start - reach, cross + side, maxY, end + reach);
    }

    public int deckY() {
        return deckY;
    }

    public int deckFirst() {
        return first;
    }

    public int deckLast() {
        return last;
    }

    /** @return the last walkway column sideways from the centre line. */
    public int half() {
        return half;
    }

    /** @return the world position of {@code (a, y, c)}. */
    public BlockPos at(int a, int y, int c) {
        return alongX ? new BlockPos(a, y, cross + c) : new BlockPos(cross + c, y, a);
    }

    /** @return the corner of the camp's ground: just past the far abutment, beside the deck's railing. */
    public BlockPos campOrigin() {
        return at(last - 1, seaLevel, edge + 2);
    }

    /** The direction of growing {@code a}. */
    public EnumFacing forward() {
        return alongX ? EnumFacing.EAST : EnumFacing.SOUTH;
    }

    /** The direction of growing {@code c}. */
    public EnumFacing sideways() {
        return alongX ? EnumFacing.SOUTH : EnumFacing.EAST;
    }

    void put(int a, int y, int c, IBlockState state) {
        if (alongX) {
            w.set(a, y, cross + c, state);
        } else {
            w.set(cross + c, y, a, state);
        }
    }

    void fillDown(int a, int topY, int c, IBlockState state, int depth) {
        if (alongX) {
            w.fillDown(a, cross + c, topY, state, depth);
        } else {
            w.fillDown(cross + c, a, topY, state, depth);
        }
    }

    void clearColumn(int a, int c, int fromY, int toY) {
        if (alongX) {
            w.clearColumn(a, cross + c, fromY, toY);
        } else {
            w.clearColumn(cross + c, a, fromY, toY);
        }
    }

    private void putIfNotSolid(int a, int y, int c, IBlockState state) {
        if (alongX) {
            w.setIfNotSolid(a, y, cross + c, state);
        } else {
            w.setIfNotSolid(cross + c, y, a, state);
        }
    }

    // ---- materials -------------------------------------------------------------------------------

    private static IBlockState planks(BlockPlanks.EnumType type) {
        return Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, type);
    }

    private static IBlockState woodSlab(BlockPlanks.EnumType type, boolean top) {
        return Blocks.WOODEN_SLAB.getDefaultState().withProperty(BlockWoodSlab.VARIANT, type)
                .withProperty(BlockSlab.HALF, top ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }

    private static IBlockState stoneSlab(BlockStoneSlab.EnumType type, boolean top) {
        return Blocks.STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, type)
                .withProperty(BlockSlab.HALF, top ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }

    private static IBlockState log(BlockPlanks.EnumType type, BlockLog.EnumAxis axis) {
        return Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, type).withProperty(BlockLog.LOG_AXIS, axis);
    }

    private static IBlockState brick(BlockStoneBrick.EnumType type) {
        return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, type);
    }

    private static IBlockState stairs(Block block, EnumFacing facing, boolean upsideDown) {
        return block.getDefaultState().withProperty(BlockStairs.FACING, facing)
                .withProperty(BlockStairs.HALF, upsideDown ? BlockStairs.EnumHalf.TOP : BlockStairs.EnumHalf.BOTTOM);
    }

    private IBlockState beam(BlockPlanks.EnumType type) {
        return log(type, alongX ? BlockLog.EnumAxis.X : BlockLog.EnumAxis.Z);
    }

    private IBlockState stone() {
        if (style == BridgeStyle.RUINED) {
            int roll = rand.nextInt(10);
            return brick(roll < 3 ? BlockStoneBrick.EnumType.MOSSY : roll < 5 ? BlockStoneBrick.EnumType.CRACKED
                    : BlockStoneBrick.EnumType.DEFAULT);
        }
        return brick(BlockStoneBrick.EnumType.DEFAULT);
    }

    // ---- building --------------------------------------------------------------------------------

    public void build() {
        clearAbove();
        switch (style) {
            case TIMBER:
                timber(BlockPlanks.EnumType.OAK, Blocks.OAK_FENCE, Blocks.OAK_STAIRS);
                break;
            case SPRUCE:
                timber(BlockPlanks.EnumType.SPRUCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS);
                break;
            case COVERED:
                timber(BlockPlanks.EnumType.SPRUCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS);
                roof();
                break;
            case STONE_ARCH:
            case RUINED:
                stoneBridge(false);
                break;
            case SNOW_STONE:
                stoneBridge(true);
                break;
            case TURRETED:
                stoneBridge(false);
                gatehouse(first, first + 2);
                gatehouse(last - 2, last);
                break;
            case SANDSTONE:
                sandstone();
                break;
            case ROPE:
                rope();
                break;
            case BOARDWALK:
                boardwalk();
                break;
            default:
                throw new IllegalStateException("No shape for " + style);
        }
    }

    /** Headroom over the deck and the approaches, whatever grows or rises there. */
    private void clearAbove() {
        int steps = approachLength(style);
        for (int a = first - steps; a <= last + steps; a++) {
            for (int c = -edge; c <= edge; c++) {
                // Under the roof of a covered bridge too.
                clearColumn(a, c, deckY + 1, deckY + (style == BridgeStyle.COVERED ? 5 : 4));
            }
        }
    }

    /** Evenly spaced piers over the water; an even number, so that the middle stays open. */
    private void placePiers(int spacing, boolean evenOnly) {
        int width = end - start + 1;
        int count = width / spacing;
        if (evenOnly && count % 2 == 1) {
            count--;
        }
        for (int i = 1; i <= count; i++) {
            piers.add(start + i * width / (count + 1));
        }
    }

    private boolean inGap(int a) {
        if (style != BridgeStyle.RUINED) {
            return false;
        }
        int gap = Math.max(3, (end - start + 1) / 4);
        int from = (start + end) / 2 - gap / 2;
        return a >= from && a < from + gap;
    }

    /** Steps from each deck end down to the bank, only where the bank is lower than the step. */
    private void approaches(Block stairBlock, IBlockState footing) {
        int steps = approachLength(style);
        for (int i = 1; i <= steps; i++) {
            for (int c = -edge; c <= edge; c++) {
                putIfNotSolid(first - i, deckY - i, c, stairs(stairBlock, forward(), false));
                fillDown(first - i, deckY - i - 1, c, footing, 6);
                putIfNotSolid(last + i, deckY - i, c, stairs(stairBlock, forward().getOpposite(), false));
                fillDown(last + i, deckY - i - 1, c, footing, 6);
            }
        }
    }

    private void abutments(IBlockState footing) {
        for (int a = first; a <= last; a++) {
            if (a < start || a > end) {
                for (int c = -edge; c <= edge; c++) {
                    fillDown(a, deckY - 1, c, footing, 8);
                }
            }
        }
    }

    private void timber(BlockPlanks.EnumType wood, Block fence, Block stairBlock) {
        placePiers(7, false);
        IBlockState post = log(wood, BlockLog.EnumAxis.Y);
        for (int a = first; a <= last; a++) {
            for (int c = -edge; c <= edge; c++) {
                boolean side = Math.abs(c) == edge;
                put(a, deckY, c, side ? beam(wood) : planks(wood));
                if (side) {
                    put(a, deckY + 1, c, fence.getDefaultState());
                }
            }
        }
        for (int pier : piers) {
            fillDown(pier, deckY - 1, -edge, post, 12);
            fillDown(pier, deckY - 1, edge, post, 12);
            for (int c = -half; c <= half; c++) {
                put(pier, deckY - 1, c, woodSlab(wood, true));
            }
        }
        for (int a : new int[]{first, last}) {
            for (int c : new int[]{-edge, edge}) {
                put(a, deckY + 1, c, post);
                if (style != BridgeStyle.COVERED) {
                    put(a, deckY + 2, c, Blocks.TORCH.getDefaultState());
                }
            }
        }
        abutments(planks(wood));
        approaches(stairBlock, planks(wood));
    }

    /** A gable roof on posts over the whole deck; three blocks of headroom stay free. */
    private void roof() {
        BlockPlanks.EnumType wood = BlockPlanks.EnumType.SPRUCE;
        IBlockState post = log(wood, BlockLog.EnumAxis.Y);
        for (int a = first; a <= last; a++) {
            if ((a - first) % 3 == 0 || a == last) {
                for (int c : new int[]{-edge, edge}) {
                    for (int y = deckY + 1; y <= deckY + 3; y++) {
                        put(a, y, c, post);
                    }
                }
            }
            put(a, deckY + 4, -edge, stairs(Blocks.DARK_OAK_STAIRS, sideways(), false));
            put(a, deckY + 4, edge, stairs(Blocks.DARK_OAK_STAIRS, sideways().getOpposite(), false));
            put(a, deckY + 5, -edge + 1, stairs(Blocks.DARK_OAK_STAIRS, sideways(), false));
            put(a, deckY + 5, edge - 1, stairs(Blocks.DARK_OAK_STAIRS, sideways().getOpposite(), false));
            for (int c = -edge + 2; c <= edge - 2; c++) {
                put(a, deckY + 6, c, woodSlab(BlockPlanks.EnumType.DARK_OAK, false));
            }
            if (a == first || a == last) {
                for (int c = -edge + 2; c <= edge - 2; c++) {
                    put(a, deckY + 5, c, planks(wood));
                }
            }
        }
    }

    private void stoneBridge(boolean lanterns) {
        placePiers(6, true);
        IBlockState wall = Blocks.COBBLESTONE_WALL.getDefaultState();
        IBlockState light = lanterns ? ModPaletteRegistry.resolveDecor("modernbackports:lantern") : null;
        if (light == null) {
            light = Blocks.TORCH.getDefaultState();
        }
        int mid = (start + end) / 2;
        for (int a = first; a <= last; a++) {
            if (inGap(a)) {
                continue;
            }
            for (int c = -edge; c <= edge; c++) {
                put(a, deckY, c, stone());
                if (Math.abs(c) == edge && !(style == BridgeStyle.RUINED && rand.nextInt(10) < 3)) {
                    put(a, deckY + 1, c, wall);
                    if (lanterns && (a - first) % 5 == 2) {
                        put(a, deckY + 2, c, wall);
                        put(a, deckY + 3, c, light);
                    }
                }
            }
            if (a < start || a > end || piers.contains(a) || Math.abs(a - mid) <= 1) {
                continue;
            }
            haunch(a);
        }
        for (int pier : piers) {
            for (int c = -edge; c <= edge; c++) {
                fillDown(pier, deckY - 1, c, stone(), 12);
            }
        }
        abutments(brick(BlockStoneBrick.EnumType.DEFAULT));
        approaches(Blocks.STONE_BRICK_STAIRS, brick(BlockStoneBrick.EnumType.DEFAULT));
    }

    /** The curve of an arch: thicker under the deck next to each support. */
    private void haunch(int a) {
        int distance = Integer.MAX_VALUE;
        int support = a;
        List<Integer> supports = new ArrayList<>(piers);
        supports.add(start - 1);
        supports.add(end + 1);
        for (int s : supports) {
            if (Math.abs(a - s) < distance) {
                distance = Math.abs(a - s);
                support = s;
            }
        }
        if (distance > 2) {
            return;
        }
        EnumFacing toSupport = support < a ? forward().getOpposite() : forward();
        IBlockState curve = stairs(Blocks.STONE_BRICK_STAIRS, toSupport, true);
        for (int c = -edge; c <= edge; c++) {
            if (distance == 1) {
                put(a, deckY - 1, c, stone());
                if (style.deckRise >= 4) {
                    put(a, deckY - 2, c, curve);
                }
            } else {
                put(a, deckY - 1, c, curve);
            }
        }
    }

    /** A gate tower across the deck from {@code from} to {@code to}: a passage three wide and three high. */
    private void gatehouse(int from, int to) {
        for (int a = from; a <= to; a++) {
            for (int c = -edge; c <= edge; c++) {
                boolean passage = Math.abs(c) <= 1;
                for (int y = deckY + 1; y <= deckY + 6; y++) {
                    if (!passage || y >= deckY + 4) {
                        put(a, y, c, brick(BlockStoneBrick.EnumType.DEFAULT));
                    }
                }
                boolean rim = a == from || a == to || Math.abs(c) == edge;
                if (rim && (a + c) % 2 == 0) {
                    put(a, deckY + 7, c, brick(BlockStoneBrick.EnumType.DEFAULT));
                }
            }
        }
    }

    private void sandstone() {
        placePiers(6, true);
        IBlockState smooth = Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH);
        IBlockState chiselled = Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.CHISELED);
        IBlockState plain = Blocks.SANDSTONE.getDefaultState();
        for (int a = first; a <= last; a++) {
            boolean postHere = a == first || a == last || (a - first) % 4 == 0;
            for (int c = -edge; c <= edge; c++) {
                put(a, deckY, c, smooth);
                if (Math.abs(c) == edge) {
                    put(a, deckY + 1, c, postHere ? chiselled : stoneSlab(BlockStoneSlab.EnumType.SAND, false));
                }
            }
        }
        for (int pier : piers) {
            for (int c = -edge; c <= edge; c++) {
                fillDown(pier, deckY - 1, c, plain, 12);
            }
        }
        abutments(plain);
        approaches(Blocks.SANDSTONE_STAIRS, plain);
    }

    /** A hanging bridge of slabs that sags half a block at a time toward the middle; no piers. */
    private void rope() {
        BlockPlanks.EnumType wood = BlockPlanks.EnumType.JUNGLE;
        IBlockState fence = Blocks.JUNGLE_FENCE.getDefaultState();
        IBlockState post = log(wood, BlockLog.EnumAxis.Y);
        IBlockState leaves = Blocks.LEAVES.getDefaultState().withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.JUNGLE)
                .withProperty(BlockLeaves.DECAYABLE, false).withProperty(BlockLeaves.CHECK_DECAY, false);
        int width = end - start + 1;
        for (int a = first; a <= last; a++) {
            int fromBank = Math.min(a - start, end - a);
            // Sag in half-block steps: none on land, half a block near the banks, a whole one in the middle third.
            int sag = fromBank < 0 ? 0 : fromBank < Math.max(1, width / 3) ? 1 : 2;
            int y = sag == 2 ? deckY - 1 : deckY;
            IBlockState slab = woodSlab(wood, sag != 1);
            for (int c = -edge; c <= edge; c++) {
                put(a, y, c, slab);
                if (Math.abs(c) == edge) {
                    put(a, y + 1, c, fence);
                    if (fromBank >= 0 && sag != 2 && rand.nextInt(3) == 0) {
                        put(a, y - 1, c, leaves);
                    }
                }
            }
        }
        for (int a : new int[]{first, last}) {
            for (int c : new int[]{-edge, edge}) {
                for (int y = deckY; y <= deckY + 3; y++) {
                    put(a, y, c, post);
                }
                fillDown(a, deckY - 1, c, post, 8);
                put(a, deckY + 4, c, leaves);
            }
        }
        abutments(planks(wood));
        approaches(Blocks.JUNGLE_STAIRS, planks(wood));
    }

    /** A low walk of slabs on piles, level with the banks; a railing on one side only. */
    private void boardwalk() {
        BlockPlanks.EnumType wood = BlockPlanks.EnumType.OAK;
        IBlockState pile = log(wood, BlockLog.EnumAxis.Y);
        for (int a = first; a <= last; a++) {
            boolean piled = (a - first) % 3 == 0 || a == last;
            for (int c = -edge; c <= edge; c++) {
                boolean side = Math.abs(c) == edge;
                if (side && piled) {
                    put(a, deckY, c, pile);
                    put(a, deckY + 1, c, pile);
                    fillDown(a, deckY - 1, c, pile, 8);
                } else {
                    put(a, deckY, c, woodSlab(wood, true));
                    if (c == edge) {
                        put(a, deckY + 1, c, Blocks.OAK_FENCE.getDefaultState());
                    }
                }
            }
        }
    }
}
