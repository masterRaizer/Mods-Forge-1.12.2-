package com.shatteredcitadels.tile;

import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MageSigilTest {

    @BeforeClass
    @SuppressWarnings("deprecation")
    public static void bootstrap() {
        Bootstrap.register();
        if (TileEntity.getKey(TileEntityMageSigil.class) == null) {
            TileEntity.register(TileEntityMageSigil.ID.toString(), TileEntityMageSigil.class);
        }
    }

    @Test
    public void shouldSpawnTruthTable() {
        assertTrue(TileEntityMageSigil.shouldSpawn(false, true, false, true, false));
        assertFalse(TileEntityMageSigil.shouldSpawn(true, true, false, true, false));
        assertFalse(TileEntityMageSigil.shouldSpawn(false, false, false, true, false));
        assertFalse(TileEntityMageSigil.shouldSpawn(false, true, true, true, false));
        assertFalse(TileEntityMageSigil.shouldSpawn(false, true, false, false, false));
        assertFalse(TileEntityMageSigil.shouldSpawn(false, true, false, true, true));
    }

    @Test
    public void nbtRoundTrip() {
        TileEntityMageSigil sigil = new TileEntityMageSigil();
        StructureBoundingBox area = new StructureBoundingBox(10, 20, 30, 32, 34, 52);
        UUID structure = UUID.randomUUID();
        sigil.configure(structure, -10, area, Arrays.asList(new BlockPos(12, 21, 33), new BlockPos(30, 21, 50)));
        UUID mage = UUID.randomUUID();
        sigil.setSpawned(true, mage);
        NBTTagCompound tag = sigil.writeToNBT(new NBTTagCompound());
        TileEntityMageSigil copy = new TileEntityMageSigil();
        copy.readFromNBT(tag);
        assertEquals(structure, copy.getStructureId());
        assertEquals(-10, copy.getFloorIndex());
        assertArrayEquals(area.toNBTTagIntArray().getIntArray(), copy.getArea().toNBTTagIntArray().getIntArray());
        assertEquals(2, copy.getAnchors().size());
        assertEquals(new BlockPos(12, 21, 33), copy.getAnchors().get(0));
        assertTrue(copy.isSpawned());
        assertEquals(mage, copy.getMageId());
    }
}
