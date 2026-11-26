package com.shatteredcitadels.worldgen.hut;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockChest;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Loot tables of the witch hut and the props that need a tile entity. */
public final class WitchHutLoot {

    /** The final chest of the cellar: rare potions and brewing ingredients. */
    public static final ResourceLocation CELLAR = new ResourceLocation(Reference.MODID, "witch_hut/cellar");
    /** The chest of the hut and of the herb store: herbs and common ingredients. */
    public static final ResourceLocation HUT = new ResourceLocation(Reference.MODID, "witch_hut/hut");

    private WitchHutLoot() {
    }

    /** A chest that rolls {@code table} when first opened. */
    static void chest(BlockWriter w, CitadelInstance record, BlockPos pos, EnumFacing facing, ResourceLocation table,
                      long lootSeed) {
        w.set(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing));
        record.addChestPoint(pos);
        w.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityLockableLoot) {
                ((TileEntityLockableLoot) tile).setLootTable(table, lootSeed ^ at.toLong());
            }
        });
    }

    /** A flower pot with a mushroom. */
    static void pot(BlockWriter w, BlockPos pos, boolean red) {
        w.set(pos, Blocks.FLOWER_POT.getDefaultState());
        w.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityFlowerPot) {
                ((TileEntityFlowerPot) tile).setItemStack(new ItemStack(red ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM));
                tile.markDirty();
            }
        });
    }
}
