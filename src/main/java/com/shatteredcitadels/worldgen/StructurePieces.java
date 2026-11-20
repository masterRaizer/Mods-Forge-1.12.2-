package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.WaveSpawnerHooks;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import net.minecraft.block.BlockChest;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * Blocks that need a tile entity set up after they exist: loot chests, wave spawners and the
 * boss altar. The block goes into the blueprint now; the tile entity is configured when the
 * blueprint is written, with every random value drawn at build time so rebuilds are identical.
 */
public final class StructurePieces {

    private StructurePieces() {
    }

    /** Loot is set as a table (never pre-filled), which is what makes the chest Lootr-compatible. */
    public static boolean chest(BlockWriter writer, CitadelInstance record, BlockPos pos, EnumFacing facing,
                                ResourceLocation lootTable, long lootSeed) {
        if (!writer.set(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing))) {
            return false;
        }
        writer.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityLockableLoot) {
                ((TileEntityLockableLoot) tile).setLootTable(lootTable, lootSeed);
            }
        });
        record.addChestPoint(pos);
        return true;
    }

    /**
     * Reserves the position with a trial spawner block and records the spawner point; the active
     * placer (trial spawner or vanilla fallback) configures it at write time.
     */
    public static boolean spawner(BlockWriter writer, CitadelInstance record, BlockPos pos, SpawnerContext context) {
        if (ModBlocks.TRIAL_SPAWNER == null || !writer.set(pos, ModBlocks.TRIAL_SPAWNER.getDefaultState())) {
            return false;
        }
        writer.afterPlace(pos, (world, at) -> {
            // The placeholder must go first: placers set their block, which fails on an identical state.
            world.setBlockState(at, Blocks.AIR.getDefaultState(), BlockWriter.FLAGS);
            WaveSpawnerHooks.place(world, at, context);
        });
        record.addSpawnerPoint(pos, context.getFloorIndex(), context.getTier());
        return true;
    }

    public static void bossAltar(BlockWriter writer, BlockPos pos, CitadelInstance record) {
        if (ModBlocks.BOSS_ALTAR == null || !writer.set(pos, ModBlocks.BOSS_ALTAR.getDefaultState())) {
            return;
        }
        java.util.UUID id = record.getCitadelId();
        writer.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityBossAltar) {
                ((TileEntityBossAltar) tile).configure(id);
            }
        });
    }
}
