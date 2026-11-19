package com.shatteredcitadels.block;

import com.shatteredcitadels.spawner.FloorCombatState;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * Citadel trial spawner. Unbreakable in survival and immune to explosions: a floor is cleared by
 * fighting its waves, after which the spawner removes itself. Drops nothing.
 */
public class BlockTrialSpawner extends Block {

    public BlockTrialSpawner() {
        super(Material.ROCK);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setSoundType(SoundType.METAL);
        setLightLevel(0.3F);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityTrialSpawner();
    }

    @Override
    @SuppressWarnings("deprecation") // Mojang marks it deprecated for callers, not overriders
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation") // Mojang marks it deprecated for callers, not overriders
    public boolean isFullCube(IBlockState state) {
        return true;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    /** Admin removal (creative): let the floor re-evaluate once the tile entity is gone. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        CitadelInstance citadel = null;
        int floorIndex = 0;
        if (!world.isRemote && tile instanceof TileEntityTrialSpawner && ((TileEntityTrialSpawner) tile).getCitadelId() != null) {
            citadel = CitadelWorldSavedData.get(world).getById(((TileEntityTrialSpawner) tile).getCitadelId());
            floorIndex = ((TileEntityTrialSpawner) tile).getFloorIndex();
        }
        super.breakBlock(world, pos, state);
        if (citadel != null) {
            FloorCombatState.refresh(world, citadel, floorIndex);
        }
    }
}
