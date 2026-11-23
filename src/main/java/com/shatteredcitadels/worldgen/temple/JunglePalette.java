package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.worldgen.BlockSet;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import java.util.Random;

/** Overgrown jungle masonry: mossy cobble and stone bricks in every state, jungle wood accents. */
public final class JunglePalette implements BlockSet {

    private final IBlockState bricks = Blocks.STONEBRICK.getDefaultState();
    private final IBlockState mossy = bricks.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
    private final IBlockState cracked = bricks.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
    private final IBlockState chiseled = bricks.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CHISELED);

    @Override
    public IBlockState wall(Random rand) {
        int r = rand.nextInt(10);
        return r < 3 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : r < 6 ? mossy : r < 8 ? bricks : cracked;
    }

    @Override
    public IBlockState floor(Random rand) {
        int r = rand.nextInt(6);
        return r < 2 ? mossy : r < 4 ? bricks : r == 4 ? cracked : Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }

    @Override
    public IBlockState ceiling() {
        return mossy;
    }

    @Override
    public IBlockState accent() {
        return chiseled;
    }

    @Override
    public IBlockState pillar() {
        return Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.JUNGLE)
                .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y);
    }

    public IBlockState cracked() {
        return cracked;
    }

    public IBlockState chiseled() {
        return chiseled;
    }
}
