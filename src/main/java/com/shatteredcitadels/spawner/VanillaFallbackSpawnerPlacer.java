package com.shatteredcitadels.spawner;

import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Default placer until the wave spawner block exists: a vanilla mob spawner with a mob of the
 * requested tier taken from {@code manualMobTierOverrides}, never a blacklisted mob, and a
 * nearby-entity limit derived from {@code maxActiveMobsPerFloor}.
 */
public final class VanillaFallbackSpawnerPlacer implements IWaveSpawnerPlacer {

    private static final ResourceLocation ZOMBIE = new ResourceLocation("minecraft", "zombie");

    @Override
    public boolean placeSpawner(World world, BlockPos pos, SpawnerContext context) {
        if (!world.setBlockState(pos, Blocks.MOB_SPAWNER.getDefaultState(), 2)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityMobSpawner)) {
            return false;
        }
        Random random = new Random(world.getSeed() ^ pos.toLong() ^ context.getTier());
        MobSpawnerBaseLogic logic = ((TileEntityMobSpawner) tile).getSpawnerBaseLogic();
        logic.setEntityId(chooseMob(context.getTier(), random));

        ConfigCache.Snapshot config = ConfigCache.get();
        NBTTagCompound tag = logic.writeToNBT(new NBTTagCompound());
        tag.setShort("MaxNearbyEntities", (short) Math.max(1, Math.min(6, config.getMaxActiveMobsPerFloor() / 2)));
        tag.setShort("SpawnCount", (short) 2);
        tag.setShort("RequiredPlayerRange", (short) 12);
        tag.setShort("SpawnRange", (short) 3);
        tag.setShort("MinSpawnDelay", (short) 200);
        tag.setShort("MaxSpawnDelay", (short) 600);
        logic.readFromNBT(tag);
        // No tile.markDirty(): its comparator update may load neighbouring chunks during
        // population. setBlockState already marked the chunk as modified.
        return true;
    }

    /** @return a registered, non-blacklisted living mob of the tier (or the nearest lower tier). */
    public static ResourceLocation chooseMob(int tier, Random random) {
        ConfigCache.Snapshot config = ConfigCache.get();
        for (int t = tier; t >= 1; t--) {
            List<ResourceLocation> candidates = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Integer> entry : config.getManualMobTierOverrides().entrySet()) {
                if (entry.getValue() == t && isUsable(entry.getKey(), config)) {
                    candidates.add(entry.getKey());
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }
        return ZOMBIE;
    }

    private static boolean isUsable(ResourceLocation id, ConfigCache.Snapshot config) {
        if (config.isBlacklisted(id) || !ForgeRegistries.ENTITIES.containsKey(id)) {
            return false;
        }
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(id);
        return entry != null && entry.getEntityClass() != null
                && EntityLiving.class.isAssignableFrom(entry.getEntityClass());
    }
}
