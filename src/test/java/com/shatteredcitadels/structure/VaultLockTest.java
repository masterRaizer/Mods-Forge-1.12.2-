package com.shatteredcitadels.structure;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.worldgen.TestBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VaultLockTest {

    static final BlockPos S1 = new BlockPos(10, 40, 10);
    static final BlockPos S2 = new BlockPos(11, 40, 10);
    static final BlockPos S3 = new BlockPos(12, 40, 10);

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    static VaultLock lock() {
        return new VaultLock(new StructureBoundingBox(5, 35, 12, 20, 45, 25), Arrays.asList(S1, S2, S3));
    }

    /** A temple record without parts: unprotected except its locked vault. */
    static CitadelInstance temple(VaultLock vault) {
        CitadelInstance rec = new CitadelInstance(UUID.randomUUID(), "", StructureType.DESERT_TEMPLE, CitadelArchetype.KEEPERS_CITADEL, 0,
                BlockPos.ORIGIN, new StructureBoundingBox(-60, 5, -60, 60, 100, 60));
        rec.setVault(vault);
        return rec;
    }

    @Test
    public void placeOnlyOnceAndOnlyOwnSockets() {
        VaultLock vault = lock();
        assertTrue(vault.place(S1));
        assertFalse(vault.place(S1));
        assertFalse(vault.place(new BlockPos(0, 0, 0)));
        assertTrue(vault.isFilled(S1));
        assertFalse(vault.isFilled(S2));
    }

    @Test
    public void opensWhenAllThreeFilled() {
        VaultLock vault = lock();
        vault.place(S1);
        vault.place(S2);
        assertFalse(vault.isOpen());
        vault.place(S3);
        assertTrue(vault.isOpen());
    }

    @Test
    public void roundTripKeepsFilledSockets() {
        VaultLock vault = lock();
        vault.place(S2);
        CitadelInstance copy = CitadelInstance.readFromNBT(temple(vault).writeToNBT());
        assertNotNull(copy.getVault());
        assertEquals(3, copy.getVault().sockets().size());
        assertTrue(copy.getVault().isFilled(S2));
        assertFalse(copy.getVault().isFilled(S1));
        assertEquals(vault.box().toString(), copy.getVault().box().toString());
    }

    @Test
    public void vaultProtectedUntilOpenEvenWhenConquered() {
        VaultLock vault = lock();
        CitadelInstance rec = temple(vault);
        rec.addFloor(0, 30, 50, 5, 0, true).setBossDefeated(true);
        assertTrue(rec.isConquered());
        BlockPos inside = new BlockPos(15, 40, 20);
        assertTrue(CitadelProtectionHandler.protects(rec, inside));
        assertFalse(CitadelProtectionHandler.protects(rec, new BlockPos(40, 40, 40)));
        vault.place(S1);
        vault.place(S2);
        vault.place(S3);
        assertFalse(CitadelProtectionHandler.protects(rec, inside));
    }

    @Test
    public void vaultGatesIgnoredByConquestAndReconcileWhileLocked() {
        VaultLock vault = lock();
        CitadelInstance rec = temple(vault);
        rec.addGate(new BlockPos(15, 40, 12), VaultLock.GATE_FLOOR, 0);
        CitadelInstance.GatePoint gate = rec.getGates().get(0);
        assertFalse(SealedGates.opensOnConquest(gate));
        assertFalse(SealedGates.mayOpen(rec, gate));
        vault.place(S1);
        vault.place(S2);
        vault.place(S3);
        assertTrue(SealedGates.mayOpen(rec, gate));
    }

    /** A buried fragment overlapping the vault must not hide the temple from its sockets. */
    @Test
    public void socketOwnerFoundPastOtherRecords() {
        CitadelInstance fragment = new CitadelInstance(UUID.randomUUID(), "", StructureType.CRYPT, CitadelArchetype.KEEPERS_CITADEL, 0,
                BlockPos.ORIGIN, new StructureBoundingBox(0, 30, 0, 30, 45, 30));
        CitadelInstance temple = temple(lock());
        assertEquals(temple, VaultLock.ownerOf(Arrays.asList(fragment, temple), S2));
        assertEquals(null, VaultLock.ownerOf(Arrays.asList(fragment, temple), new BlockPos(1, 2, 3)));
    }
}
