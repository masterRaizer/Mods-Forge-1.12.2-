package com.shatteredcitadels.floor;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.spawner.WaveMobFactory;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Wave mob deaths: tells the owning trial spawner, which updates the floor state and pushes the
 * HUD to the players at once. Runs last and ignores cancelled deaths (totems, revive mods).
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class FloorCombatHandler {

    private FloorCombatHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(WaveMobFactory.TAG_SPAWNER, Constants.NBT.TAG_LONG)) {
            return;
        }
        BlockPos spawnerPos = BlockPos.fromLong(data.getLong(WaveMobFactory.TAG_SPAWNER));
        if (!entity.world.isBlockLoaded(spawnerPos)) {
            // The spawner's anti-stall cleanup will drop this mob when its chunk is active again.
            return;
        }
        TileEntity tile = entity.world.getTileEntity(spawnerPos);
        if (tile instanceof TileEntityTrialSpawner) {
            ((TileEntityTrialSpawner) tile).onTrackedMobDeath(entity.getUniqueID());
        }
    }
}
