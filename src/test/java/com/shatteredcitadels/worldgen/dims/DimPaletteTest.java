package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.Blueprint;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.StructurePlans;
import com.shatteredcitadels.worldgen.TestBlocks;
import com.shatteredcitadels.worldgen.dims.fortress.FortressPlan;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DimPaletteTest {

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    @Test
    public void netherHasNoWoodWaterOrBeds() {
        assertSame(Blocks.NETHER_BRICK, DimPalette.remap(DimKind.NETHER, Blocks.COBBLESTONE.getDefaultState()).getBlock());
        assertSame(Blocks.NETHER_BRICK, DimPalette.remap(DimKind.NETHER, Blocks.PLANKS.getDefaultState()).getBlock());
        assertSame(Blocks.NETHER_BRICK_FENCE, DimPalette.remap(DimKind.NETHER, Blocks.OAK_FENCE.getDefaultState()).getBlock());
        assertSame(Blocks.NETHERRACK, DimPalette.remap(DimKind.NETHER, Blocks.GRASS.getDefaultState()).getBlock());
        assertSame(Blocks.AIR, DimPalette.remap(DimKind.NETHER, Blocks.BED.getDefaultState()).getBlock());
        assertSame(Blocks.NETHER_BRICK, DimPalette.remap(DimKind.NETHER, Blocks.NETHER_BRICK.getDefaultState()).getBlock());
        assertTrue("water must not be placed in the Nether",
                DimPalette.remap(DimKind.NETHER, Blocks.WATER.getDefaultState()).getBlock() != Blocks.WATER);
    }

    @Test
    public void stairsKeepTheirFacing() {
        IBlockState oak = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        IBlockState nether = DimPalette.remap(DimKind.NETHER, oak);
        assertSame(Blocks.NETHER_BRICK_STAIRS, nether.getBlock());
        assertEquals(EnumFacing.WEST, nether.getValue(BlockStairs.FACING));
        assertEquals(BlockStairs.EnumHalf.TOP, nether.getValue(BlockStairs.HALF));
        IBlockState end = DimPalette.remap(DimKind.END, oak);
        assertSame(Blocks.PURPUR_STAIRS, end.getBlock());
        assertEquals(EnumFacing.WEST, end.getValue(BlockStairs.FACING));
    }

    @Test
    public void endUsesPurpurEndStoneAndRods() {
        assertSame(Blocks.END_BRICKS, DimPalette.remap(DimKind.END, Blocks.COBBLESTONE.getDefaultState()).getBlock());
        assertSame(Blocks.END_STONE, DimPalette.remap(DimKind.END, Blocks.DIRT.getDefaultState()).getBlock());
        assertSame(Blocks.PURPUR_BLOCK, DimPalette.remap(DimKind.END, Blocks.PLANKS.getDefaultState()).getBlock());
        assertSame(Blocks.END_ROD, DimPalette.remap(DimKind.END, Blocks.TORCH.getDefaultState()).getBlock());
        assertSame(Blocks.AIR, DimPalette.remap(DimKind.END, Blocks.WATER.getDefaultState()).getBlock());
    }

    @Test
    public void atumFallsBackToVanillaSandstoneWithoutTheMod() {
        // The tests run without Atum: the registry lookups must fall back, never give air.
        assertSame(Blocks.SAND, DimPalette.remap(DimKind.ATUM, Blocks.GRASS.getDefaultState()).getBlock());
        assertSame(Blocks.SANDSTONE, DimPalette.remap(DimKind.ATUM, Blocks.COBBLESTONE.getDefaultState()).getBlock());
        assertSame(Blocks.SANDSTONE, DimPalette.remap(DimKind.ATUM, Blocks.SANDSTONE.getDefaultState()).getBlock());
        assertSame(Blocks.PLANKS, DimPalette.remap(DimKind.ATUM, Blocks.PLANKS.getDefaultState()).getBlock());
        assertSame(Blocks.STONE, DimPalette.mod("atum:no_such_block", Blocks.STONE.getDefaultState()).getBlock());
    }

    @Test
    public void blueprintIsRepaintedForTheDimension() {
        BlockWriter writer = new BlockWriter(new Blueprint());
        writer.set(3, 64, 3, Blocks.COBBLESTONE.getDefaultState());
        writer.set(3, 65, 3, Blocks.OAK_FENCE.getDefaultState());
        writer.set(3, 66, 3, Blocks.STONEBRICK.getDefaultState());
        writer.fillDown(4, 4, 63, Blocks.COBBLESTONE.getDefaultState(), 10);
        DimPalette.apply(writer.getBlueprint(), DimKind.NETHER);
        assertSame(Blocks.NETHER_BRICK, writer.get(3, 64, 3).getBlock());
        assertSame(Blocks.NETHER_BRICK_FENCE, writer.get(3, 65, 3).getBlock());
        assertSame(Blocks.STONEBRICK, writer.get(3, 66, 3).getBlock());
        assertEquals(3, writer.getBlueprint().blockCount());
    }

    @Test
    public void dimensionPlanSurvivesSaving() {
        StructurePlan inner = FortressPlan.plan(-1, 10, -7, 4242L, 2);
        DimensionPlan plan = new DimensionPlan(DimKind.NETHER, inner);
        NBTTagCompound tag = plan.writeToNBT();
        assertEquals(DimensionPlan.KIND, tag.getString(StructurePlan.KIND_KEY));
        StructurePlan read = StructurePlans.read(tag);
        assertTrue(read instanceof DimensionPlan);
        DimensionPlan back = (DimensionPlan) read;
        assertEquals(DimKind.NETHER, back.dim());
        assertEquals(inner.kind(), back.kind());
        assertEquals(inner.footprint().toString(), back.footprint().toString());
        assertEquals(plan.describe(), back.describe());
        assertEquals(tag, back.writeToNBT());
    }

    @Test
    public void brokenSavedPlansAreDroppedNotCrashed() {
        NBTTagCompound noInner = new NBTTagCompound();
        noInner.setString(StructurePlan.KIND_KEY, DimensionPlan.KIND);
        noInner.setString("dim", "nether");
        assertNull(DimensionPlan.read(noInner));
        // A dimension kind removed later: the structure is still finished, in its plain look.
        NBTTagCompound unknown = new DimensionPlan(DimKind.END, FortressPlan.plan(-1, 0, 0, 1L, 1)).writeToNBT();
        unknown.setString("dim", "gone");
        StructurePlan read = DimensionPlan.read(unknown);
        assertNotNull(read);
        assertTrue(read instanceof FortressPlan);
    }
}
