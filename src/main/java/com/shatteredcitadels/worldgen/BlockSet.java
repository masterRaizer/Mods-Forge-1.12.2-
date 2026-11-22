package com.shatteredcitadels.worldgen;

import net.minecraft.block.state.IBlockState;

import java.util.Random;

/** The masonry of a structure built from pieces: walls, floors, ceilings, accents, pillars. */
public interface BlockSet {

    IBlockState wall(Random rand);

    IBlockState floor(Random rand);

    IBlockState ceiling();

    IBlockState accent();

    IBlockState pillar();
}
