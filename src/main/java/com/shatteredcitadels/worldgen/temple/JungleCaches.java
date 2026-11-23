package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.item.ModItems;
import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hidden caches of a jungle temple. A cache chest has fixed contents (no loot table, so a loot
 * mod never swaps its idol away): an idol in the vault variant, and a little treasure.
 */
final class JungleCaches {

    /** The slab over a cache chest: its underside does not block the lid. */
    static final IBlockState TOP_SLAB = Blocks.STONE_SLAB.getDefaultState()
            .withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.SMOOTHBRICK)
            .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP);

    private JungleCaches() {
    }

    /** A chest holding an idol (if {@code idol}) and a little treasure. */
    static void chest(BlockWriter w, BlockPos pos, EnumFacing facing, boolean idol, Random rand) {
        w.set(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing));
        List<ItemStack> items = new ArrayList<>();
        if (idol && ModItems.JUNGLE_IDOL != null) {
            items.add(new ItemStack(ModItems.JUNGLE_IDOL));
        }
        items.add(new ItemStack(Items.GOLD_NUGGET, 3 + rand.nextInt(6)));
        items.add(new ItemStack(Items.EMERALD, 1 + rand.nextInt(3)));
        if (rand.nextInt(4) == 0) {
            items.add(new ItemStack(Items.DIAMOND));
        }
        int[] slots = {4, 10, 13, 16, 22};
        w.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof IInventory) {
                for (int i = 0; i < items.size() && i < slots.length; i++) {
                    ((IInventory) tile).setInventorySlotContents(slots[i], items.get(i).copy());
                }
            }
        });
    }

    /** A painting hung at {@code pos} on the wall behind it ({@code facing} points out of the wall). */
    static void painting(BlockWriter w, BlockPos pos, EnumFacing facing) {
        w.afterPlace(pos, (world, at) -> {
            EntityPainting painting = new EntityPainting(world, at, facing);
            if (painting.onValidSurface()) {
                world.spawnEntity(painting);
            }
        });
    }
}
