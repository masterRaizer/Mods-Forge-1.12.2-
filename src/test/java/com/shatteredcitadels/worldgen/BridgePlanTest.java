package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.bridge.BridgePlan;
import com.shatteredcitadels.worldgen.bridge.BridgeStyle;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BridgePlanTest {

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    private static int blocks(BridgePlan plan) {
        Blueprint blueprint = new Blueprint();
        assertNotNull(plan.build(new BlockWriter(blueprint)));
        return blueprint.blockCount();
    }

    @Test
    public void nbtRoundTripBuildsSameBlueprint() {
        for (BridgeStyle style : BridgeStyle.values()) {
            BridgePlan plan = new BridgePlan(style, true, style.ordinal() % 2 == 0, 100, 111, -40, 63, 77L + style.ordinal(), 0);
            BridgePlan copy = (BridgePlan) StructurePlans.read(plan.writeToNBT());
            assertNotNull(style.id(), copy);
            assertEquals(plan.describe(), copy.describe());
            assertEquals(style.id(), blocks(plan), blocks(copy));
            assertSame(style, copy.style());
            assertEquals(plan.fortified(), copy.fortified());
            assertEquals(plan.footprint().toString(), copy.footprint().toString());
        }
    }

    @Test
    public void recordIsBridgeAndUnprotected() {
        BridgePlan plan = new BridgePlan(BridgeStyle.STONE_ARCH, true, true, 100, 111, -40, 63, 5L, 0);
        CitadelInstance record = plan.build(new BlockWriter(new Blueprint()));
        assertSame(StructureType.BRIDGE, record.getType());
        assertTrue(record.isUnprotected());
        assertTrue(record.getFloors().isEmpty());
        assertTrue(record.getGates().isEmpty());
        assertEquals(1, record.getChestPoints().size());
        assertEquals("bridge stone_arch fortified at 105,-40", plan.describe());
        // The record survives a save.
        assertSame(StructureType.BRIDGE, CitadelInstance.readFromNBT(record.writeToNBT()).getType());
    }

    @Test
    public void campNeedsASolidDeck() {
        assertFalse(new BridgePlan(BridgeStyle.ROPE, true, true, 100, 111, -40, 63, 5L, 0).fortified());
        assertTrue(new BridgePlan(BridgeStyle.TIMBER, true, true, 100, 111, -40, 63, 5L, 0).fortified());
    }

    @Test
    public void unknownStyleInNbtFallsBackToTimber() {
        NBTTagCompound tag = new BridgePlan(BridgeStyle.SPRUCE, false, false, 10, 20, 5, 63, 1L, 0).writeToNBT();
        tag.setString("style", "from_a_newer_version");
        assertSame(BridgeStyle.TIMBER, BridgePlan.read(tag).style());
    }

    @Test
    public void structureTypeBridgeKeepsItsPlace() {
        // Appended after every older type; later parts append after it, never before.
        assertEquals(StructureType.JUNGLE_TEMPLE.ordinal() + 1, StructureType.BRIDGE.ordinal());
        assertSame(StructureType.BRIDGE, StructureType.byName("BRIDGE"));
    }
}
