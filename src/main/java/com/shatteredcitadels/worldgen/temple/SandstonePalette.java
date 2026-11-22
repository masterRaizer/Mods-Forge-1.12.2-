package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.worldgen.BlockSet;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import java.util.Random;

/** Sandstone in its cut, smooth and chiseled forms, with the odd sand-worn block. */
public final class SandstonePalette implements BlockSet {

    private final IBlockState plain = Blocks.SANDSTONE.getDefaultState();
    private final IBlockState smooth = plain.withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH);
    private final IBlockState chiseled = plain.withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.CHISELED);

    @Override
    public IBlockState wall(Random rand) {
        return rand.nextInt(10) < 6 ? smooth : plain;
    }

    @Override
    public IBlockState floor(Random rand) {
        return rand.nextInt(4) == 0 ? plain : smooth;
    }

    @Override
    public IBlockState ceiling() {
        return smooth;
    }

    @Override
    public IBlockState accent() {
        return chiseled;
    }

    @Override
    public IBlockState pillar() {
        return chiseled;
    }

    public IBlockState orangeTerracotta() {
        return Blocks.STAINED_HARDENED_CLAY.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR, net.minecraft.item.EnumDyeColor.ORANGE);
    }

    public IBlockState blueTerracotta() {
        return Blocks.STAINED_HARDENED_CLAY.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR, net.minecraft.item.EnumDyeColor.BLUE);
    }
}
