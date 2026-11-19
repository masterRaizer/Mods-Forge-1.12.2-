package com.shatteredcitadels.tile;

import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrialSpawnerNbtTest {

    @BeforeClass
    public static void setUp() {
        Bootstrap.register();
        if (TileEntity.getKey(TileEntityTrialSpawner.class) == null) {
            TileEntity.register(TileEntityTrialSpawner.ID.toString(), TileEntityTrialSpawner.class);
        }
    }

    @Test
    public void allFieldsSurviveRoundTrip() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", TileEntityTrialSpawner.ID.toString());
        tag.setInteger("x", 10);
        tag.setInteger("y", 70);
        tag.setInteger("z", -4);
        tag.setUniqueId("citadelId", UUID.fromString("0a2b3c4d-1111-2222-3333-444455556666"));
        tag.setInteger("floorIndex", -2);
        tag.setInteger("difficultyTier", 5);
        tag.setBoolean("underground", true);
        NBTTagList combo = new NBTTagList();
        combo.appendTag(new NBTTagString("minecraft:husk"));
        combo.appendTag(new NBTTagString("testmod:cave_goblin"));
        tag.setTag("assignedMobCombo", combo);
        tag.setInteger("currentWave", 2);
        tag.setInteger("totalWaves", 4);
        tag.setInteger("cooldownTicks", 37);
        tag.setInteger("toSpawnInWave", 3);
        tag.setBoolean("waveInProgress", true);
        NBTTagList alive = new NBTTagList();
        NBTTagCompound mob = new NBTTagCompound();
        mob.setUniqueId("id", UUID.fromString("99999999-8888-7777-6666-555544443333"));
        mob.setLong("spawnedAt", 123456L);
        alive.appendTag(mob);
        tag.setTag("aliveSpawnedMobs", alive);
        tag.setBoolean("isExhausted", false);
        tag.setBoolean("floorResolved", true);

        TileEntityTrialSpawner spawner = new TileEntityTrialSpawner();
        spawner.readFromNBT(tag);
        NBTTagCompound written = spawner.writeToNBT(new NBTTagCompound());
        assertEquals(tag, written);
        assertEquals(2, spawner.getAssignedMobCombo().size());
        assertEquals(1, spawner.getAliveSpawnedMobs());
        assertEquals(4, spawner.getRemainingMobs());
        assertTrue(spawner.isWaveInProgress());
    }

    @Test
    public void configureFromWorldgenContext() {
        TileEntityTrialSpawner spawner = new TileEntityTrialSpawner();
        UUID id = UUID.randomUUID();
        spawner.configure(new SpawnerContext(SpawnerSource.CRYPT, id, -1, 4, RoomTag.GOBLIN_DEN));
        NBTTagCompound written = spawner.writeToNBT(new NBTTagCompound());
        assertEquals(id, written.getUniqueId("citadelId"));
        assertEquals(-1, written.getInteger("floorIndex"));
        assertEquals(4, written.getInteger("difficultyTier"));
        assertTrue(written.getBoolean("underground"));
    }
}
