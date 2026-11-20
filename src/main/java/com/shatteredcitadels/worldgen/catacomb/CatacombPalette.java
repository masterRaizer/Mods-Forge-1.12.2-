package com.shatteredcitadels.worldgen.catacomb;

import com.shatteredcitadels.palette.ModPaletteRegistry;
import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Old, damp masonry for the catacombs: stone bricks in every state of decay, cobble, andesite and
 * bone. Optional modded stones join the wall mix when their mod is installed.
 */
public final class CatacombPalette {

    private final List<IBlockState> walls = new ArrayList<>();
    private final List<IBlockState> floors = new ArrayList<>();
    private final IBlockState ceiling;
    private final IBlockState accent;
    private final IBlockState pillar;

    public CatacombPalette() {
        IBlockState bricks = Blocks.STONEBRICK.getDefaultState();
        IBlockState mossy = bricks.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
        IBlockState cracked = bricks.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        IBlockState andesite = Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.ANDESITE_SMOOTH);
        add(walls, bricks, 4);
        add(walls, mossy, 3);
        add(walls, cracked, 3);
        add(walls, Blocks.COBBLESTONE.getDefaultState(), 1);
        add(walls, Blocks.MOSSY_COBBLESTONE.getDefaultState(), 1);
        IBlockState modded = ModPaletteRegistry.resolveDecor("quark:world_stone_bricks[variant=stone_andesite_bricks]",
                "quark:world_stone_bricks[variant=stone_basalt_bricks]");
        if (modded != null) {
            add(walls, modded, 2);
        }
        add(floors, bricks, 3);
        add(floors, cracked, 3);
        add(floors, andesite, 2);
        add(floors, Blocks.COBBLESTONE.getDefaultState(), 1);
        add(floors, Blocks.GRAVEL.getDefaultState(), 1);
        this.ceiling = bricks;
        this.accent = bricks.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CHISELED);
        this.pillar = andesite;
    }

    private static void add(List<IBlockState> list, IBlockState state, int weight) {
        for (int i = 0; i < weight; i++) {
            list.add(state);
        }
    }

    public IBlockState wall(Random rand) {
        return walls.get(rand.nextInt(walls.size()));
    }

    public IBlockState floor(Random rand) {
        return floors.get(rand.nextInt(floors.size()));
    }

    public IBlockState ceiling() {
        return ceiling;
    }

    public IBlockState accent() {
        return accent;
    }

    public IBlockState pillar() {
        return pillar;
    }

    public IBlockState bone() {
        return Blocks.BONE_BLOCK.getDefaultState();
    }
}
