package com.shatteredcitadels.spawner;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.advancement.CustomAdvancementTriggers;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Aggregates the trial spawners of one floor into its {@link FloorData} (active wave, remaining
 * mobs, cleared) and pushes the change to every player on that citadel immediately.
 */
public final class FloorCombatState {

    private FloorCombatState() {
    }

    public static void refresh(World world, CitadelInstance citadel, int floorIndex) {
        if (world == null || world.isRemote || citadel == null) {
            return;
        }
        FloorData floor = citadel.getFloor(floorIndex);
        if (floor == null) {
            return;
        }
        int maxWave = 0;
        int remaining = 0;
        boolean anyPoint = false;
        boolean anyActive = false;
        for (CitadelInstance.SpawnerPoint point : citadel.getSpawnerPoints()) {
            if (point.floorIndex != floorIndex) {
                continue;
            }
            anyPoint = true;
            if (!world.isBlockLoaded(point.pos)) {
                // Unknown state: never declare a floor cleared because a chunk is unloaded.
                anyActive = true;
                continue;
            }
            TileEntity tile = world.getTileEntity(point.pos);
            if (tile instanceof TileEntityTrialSpawner && !tile.isInvalid()) {
                TileEntityTrialSpawner spawner = (TileEntityTrialSpawner) tile;
                if (!spawner.isExhausted()) {
                    anyActive = true;
                }
                maxWave = Math.max(maxWave, spawner.getCurrentWave());
                remaining += spawner.getRemainingMobs();
            }
        }
        if (floor.isCleared()) {
            // A cleared floor stays cleared; its spawners are gone, keep the final wave count.
            floor.setRemainingMobs(0);
            CitadelWorldSavedData.get(world).markDirty();
            FloorTrackerHandler.resyncCitadel(world, citadel.getCitadelId());
            return;
        }
        floor.setActiveWave(maxWave);
        floor.setRemainingMobs(remaining);
        if (anyPoint && !anyActive) {
            floor.setCleared(true);
            floor.setActiveWave(Math.max(maxWave, floor.getTotalWaves()));
            floor.setRemainingMobs(0);
            ShatteredCitadelsMod.LOGGER.info("Floor {} of citadel {} cleared", floorIndex, citadel.getCitadelId());
            for (EntityPlayerMP player : FloorTrackerHandler.playersOnFloor(world, citadel.getCitadelId(), floorIndex)) {
                CustomAdvancementTriggers.floorCleared(player, floorIndex < 0);
            }
        }
        CitadelWorldSavedData.get(world).markDirty();
        FloorTrackerHandler.resyncCitadel(world, citadel.getCitadelId());
    }
}
