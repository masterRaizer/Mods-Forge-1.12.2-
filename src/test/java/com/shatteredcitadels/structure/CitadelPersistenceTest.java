package com.shatteredcitadels.structure;

import com.shatteredcitadels.palette.CitadelArchetype;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CitadelPersistenceTest {

    private static CitadelInstance sample(UUID id, int x, int z) {
        CitadelInstance c = new CitadelInstance(id, CitadelNameGenerator.generate("sky_citadel", new Random(7)),
                StructureType.SKY_CITADEL, CitadelArchetype.SKY_CITADEL, 0, new BlockPos(x, 64, z),
                new StructureBoundingBox(x, 40, z, x + 22, 230, z + 22));
        c.setBossArena(new StructureBoundingBox(x, 180, z, x + 22, 194, z + 22), new BlockPos(x + 11, 183, z + 11));
        FloorData f0 = c.addFloor(0, 64, 71, 2, 3, false);
        f0.setActiveWave(2);
        f0.setRemainingMobs(5);
        FloorData top = c.addFloor(1, 72, 85, 6, 1, true);
        top.setCleared(true);
        top.setBossDefeated(true);
        c.addFloor(-1, 50, 54, 5, 4, false);
        c.addSpawnerPoint(new BlockPos(x + 3, 65, z + 4), 0, 2);
        c.addChestPoint(new BlockPos(x + 5, 65, z + 1));
        c.addRoom(new StructureBoundingBox(x + 1, 64, z + 1, x + 9, 71, z + 9), RoomTag.LIBRARY, 0);
        c.setHasCrypt(true);
        return c;
    }

    private static void assertSameInstance(CitadelInstance a, CitadelInstance b) {
        assertEquals(a.getCitadelId(), b.getCitadelId());
        assertEquals(a.getDisplayName(), b.getDisplayName());
        assertEquals(a.getType(), b.getType());
        assertSame(a.getArchetype(), b.getArchetype());
        assertEquals(a.isSkyCitadel(), b.isSkyCitadel());
        assertEquals(a.isUnderground(), b.isUnderground());
        assertEquals(a.getDimension(), b.getDimension());
        assertEquals(a.getOrigin(), b.getOrigin());
        assertArrayEquals(a.getTotalBounds().toNBTTagIntArray().getIntArray(), b.getTotalBounds().toNBTTagIntArray().getIntArray());
        assertArrayEquals(a.getBossArenaBounds().toNBTTagIntArray().getIntArray(), b.getBossArenaBounds().toNBTTagIntArray().getIntArray());
        assertEquals(a.getBossAltarPos(), b.getBossAltarPos());
        assertEquals(a.getFloors().keySet(), b.getFloors().keySet());
        for (Integer index : a.getFloors().keySet()) {
            FloorData fa = a.getFloor(index);
            FloorData fb = b.getFloor(index);
            assertEquals(fa.getMinY(), fb.getMinY());
            assertEquals(fa.getMaxY(), fb.getMaxY());
            assertEquals(fa.getDifficultyTier(), fb.getDifficultyTier());
            assertEquals(fa.isBossFloor(), fb.isBossFloor());
            assertEquals(fa.getTotalWaves(), fb.getTotalWaves());
            assertEquals(fa.getActiveWave(), fb.getActiveWave());
            assertEquals(fa.getRemainingMobs(), fb.getRemainingMobs());
            assertEquals(fa.isCleared(), fb.isCleared());
            assertEquals(fa.isBossDefeated(), fb.isBossDefeated());
        }
        assertEquals(a.getSpawnerPoints().size(), b.getSpawnerPoints().size());
        assertEquals(a.getSpawnerPoints().get(0).pos, b.getSpawnerPoints().get(0).pos);
        assertEquals(a.getChestPoints(), b.getChestPoints());
        assertEquals(a.getRooms().get(0).tag, b.getRooms().get(0).tag);
        assertEquals(a.hasCrypt(), b.hasCrypt());
        assertEquals(a.writeToNBT(), b.writeToNBT());
    }

    @Test
    public void instanceNbtIsSymmetric() {
        CitadelInstance original = sample(UUID.randomUUID(), 100, -300);
        CitadelInstance restored = CitadelInstance.readFromNBT(original.writeToNBT());
        assertNotNull(restored);
        assertSameInstance(original, restored);
        assertEquals(2, restored.getSurfaceFloorCount());
        assertEquals(1, restored.getUndergroundLevelCount());
    }

    @Test
    public void corruptEntryIsRejected() {
        assertNull(CitadelInstance.readFromNBT(new NBTTagCompound()));
    }

    @Test
    public void savedDataSurvivesDiskRoundTripAndIndexes() throws IOException {
        CitadelWorldSavedData data = new CitadelWorldSavedData(CitadelWorldSavedData.DATA_NAME);
        CitadelInstance a = sample(UUID.randomUUID(), 100, -300);
        CitadelInstance b = sample(UUID.randomUUID(), -1000, 2000);
        data.register(a);
        data.register(b);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(data.writeToNBT(new NBTTagCompound()), bytes);
        NBTTagCompound read = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        CitadelWorldSavedData loaded = new CitadelWorldSavedData(CitadelWorldSavedData.DATA_NAME);
        loaded.readFromNBT(read);

        assertEquals(2, loaded.size());
        assertSameInstance(a, loaded.getById(a.getCitadelId()));
        assertSameInstance(b, loaded.getById(b.getCitadelId()));
        assertEquals(data.indexSnapshot(), loaded.indexSnapshot());

        CitadelInstance hit = loaded.getCitadelAt(new BlockPos(110, 66, -290));
        assertNotNull(hit);
        assertEquals(a.getCitadelId(), hit.getCitadelId());
        assertEquals(0, hit.floorAt(66).getIndex());
        assertEquals(-1, hit.floorAt(52).getIndex());
        assertNull(loaded.getCitadelAt(new BlockPos(110, 20, -290)));
        assertNull(loaded.getCitadelAt(new BlockPos(5000, 66, 5000)));
        assertTrue(loaded.isChunkOccupied(100 >> 4, -300 >> 4));
        assertTrue(!loaded.isFarEnough(100 >> 4, -300 >> 4, 8));
        assertTrue(loaded.isFarEnough(300, 300, 8));
    }

    @Test
    public void reRegisteringReplacesIndexEntries() {
        CitadelWorldSavedData data = new CitadelWorldSavedData(CitadelWorldSavedData.DATA_NAME);
        UUID id = UUID.randomUUID();
        data.register(sample(id, 0, 0));
        int chunks = data.indexedChunkCount();
        data.register(sample(id, 0, 0));
        assertEquals(1, data.size());
        assertEquals(chunks, data.indexedChunkCount());
        for (Integer count : data.indexSnapshot().values()) {
            assertEquals(Integer.valueOf(1), count);
        }
    }

    @Test
    public void generatedNamesParse() {
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            String name = CitadelNameGenerator.generate("titan_forge", random);
            String[] keys = CitadelNameGenerator.parseKeys(name);
            assertNotNull(keys);
            assertTrue(keys[0].startsWith(CitadelNameGenerator.KEY_TITLE + "titan_forge."));
            assertTrue(keys[1].startsWith(CitadelNameGenerator.KEY_EPITHET));
        }
        assertNull(CitadelNameGenerator.parseKeys("Plain Name"));
        assertNull(CitadelNameGenerator.parseKeys("@name:crypt:9:0"));
    }

    @Test
    public void gatesAndPlayerPlacedBlocksSurviveSaving() throws IOException {
        UUID id = UUID.randomUUID();
        CitadelInstance c = new CitadelInstance(id, "", StructureType.CITADEL, CitadelArchetype.KEEPERS_CITADEL, 0,
                new BlockPos(0, 64, 0), new StructureBoundingBox(0, 40, 0, 26, 160, 26));
        c.addFloor(0, 64, 71, 1, 2, false);
        c.addFloor(1, 72, 85, 2, 1, true);
        c.addGate(new BlockPos(3, 72, 4), 0, 42);
        c.addGate(new BlockPos(3, 50, 4), -1, 7);
        c.markPlayerPlaced(new BlockPos(5, 65, 5));
        assertTrue(!c.isConquered());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(c.writeToNBT(), out);
        CitadelInstance read = CitadelInstance.readFromNBT(
                CompressedStreamTools.readCompressed(new ByteArrayInputStream(out.toByteArray())));
        assertNotNull(read);
        assertEquals(2, read.getGates().size());
        CitadelInstance.GatePoint gate = read.getGates().get(0);
        assertEquals(new BlockPos(3, 72, 4), gate.pos);
        assertEquals(0, gate.requiredFloor);
        assertEquals(42, gate.restoreStateId);
        assertEquals(-1, read.getGates().get(1).requiredFloor);
        assertTrue(read.isPlayerPlaced(new BlockPos(5, 65, 5)));
        assertTrue(read.consumePlayerPlaced(new BlockPos(5, 65, 5)));
        assertTrue(!read.isPlayerPlaced(new BlockPos(5, 65, 5)));

        read.getFloor(1).setBossDefeated(true);
        assertTrue(read.isConquered());
        read.removeGate(gate);
        assertEquals(1, read.getGates().size());
    }

    @Test
    public void sideBossFloorDoesNotConquer() {
        CitadelInstance c = new CitadelInstance(UUID.randomUUID(), "Test", StructureType.CITADEL, CitadelArchetype.KEEPERS_CITADEL, 0,
                new BlockPos(0, 64, 0), new StructureBoundingBox(0, 5, 0, 30, 120, 30));
        c.addFloor(3, 85, 92, 4, 1, true);
        FloorData sanctum = c.addFloor(-10, 20, 34, 5, 0, true).setSideBoss(true);
        sanctum.setBossDefeated(true);
        org.junit.Assert.assertFalse(c.isConquered());
        CitadelInstance copy = CitadelInstance.readFromNBT(c.writeToNBT());
        assertTrue(copy.getFloor(-10).isSideBoss());
        org.junit.Assert.assertFalse(copy.isConquered());
        c.getFloor(3).setBossDefeated(true);
        assertTrue(c.isConquered());
    }

    /** Beating the surface boss does not hand out the catacombs: they stay sealed and protected until the mage dies. */
    @Test
    public void catacombsStayProtectedUntilMageDies() {
        CitadelInstance c = new CitadelInstance(UUID.randomUUID(), "Test", StructureType.CITADEL, CitadelArchetype.KEEPERS_CITADEL, 0,
                new BlockPos(0, 64, 0), new StructureBoundingBox(-50, 5, -50, 50, 120, 50));
        c.addFloor(3, 85, 92, 4, 1, true).setBossDefeated(true);
        FloorData sanctum = c.addFloor(-10, 20, 34, 5, 0, true).setSideBoss(true);
        assertTrue(c.isConqueredAt(new BlockPos(0, 70, 0)));
        org.junit.Assert.assertFalse(c.isConqueredAt(new BlockPos(0, 40, 0)));
        c.addGate(new BlockPos(1, 40, 1), -2, 0);
        c.addGate(new BlockPos(1, 70, 1), 2, 0);
        org.junit.Assert.assertFalse(SealedGates.opensOnConquest(c.getGates().get(0)));
        assertTrue(SealedGates.opensOnConquest(c.getGates().get(1)));
        sanctum.setBossDefeated(true);
        assertTrue(c.isConqueredAt(new BlockPos(0, 40, 0)));
    }

    /** Crypts from worlds made before 1.4.0 have no parts either; they must not pass for buried fragments. */
    @Test
    public void oldCryptsAreNotBuriedFragments() {
        CitadelInstance oldCrypt = new CitadelInstance(UUID.randomUUID(), "Old", StructureType.CRYPT, CitadelArchetype.KEEPERS_CITADEL, 0,
                new BlockPos(0, 40, 0), new StructureBoundingBox(0, 40, 0, 20, 74, 20));
        org.junit.Assert.assertFalse(oldCrypt.isBuriedFragment());
        org.junit.Assert.assertFalse(CitadelInstance.readFromNBT(oldCrypt.writeToNBT()).isBuriedFragment());
        // A fragment saved by 1.6.0/1.6.1 (no flag yet): one low level, no parts.
        CitadelInstance legacyFragment = new CitadelInstance(UUID.randomUUID(), "Frag", StructureType.CRYPT,
                CitadelArchetype.KEEPERS_CITADEL, 0, new BlockPos(8, 30, 8), new StructureBoundingBox(-11, 29, -11, 27, 38, 27));
        assertTrue(legacyFragment.isBuriedFragment());
        CitadelInstance flagged = new CitadelInstance(UUID.randomUUID(), "Frag", StructureType.CRYPT,
                CitadelArchetype.KEEPERS_CITADEL, 0, new BlockPos(8, 30, 8), new StructureBoundingBox(-11, 5, -11, 27, 90, 27));
        flagged.setBuriedFragment(true);
        assertTrue(CitadelInstance.readFromNBT(flagged.writeToNBT()).isBuriedFragment());
    }
}
