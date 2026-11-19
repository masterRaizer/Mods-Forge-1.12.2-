package com.shatteredcitadels.spawner;

import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Places a citadel trial spawner (used by citadels, crypts and adopted Castle Dungeons castles).
 * Worldgen-safe: flag 2, configures the tile entity in memory without markDirty().
 */
public final class TrialSpawnerPlacer implements IWaveSpawnerPlacer {

    @Override
    public boolean placeSpawner(World world, BlockPos pos, SpawnerContext context) {
        if (ModBlocks.TRIAL_SPAWNER == null || !world.setBlockState(pos, ModBlocks.TRIAL_SPAWNER.getDefaultState(), 2)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityTrialSpawner)) {
            return false;
        }
        ((TileEntityTrialSpawner) tile).configure(context);
        return true;
    }
}
