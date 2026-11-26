package com.shatteredcitadels.compat;

import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForeignZonesTest {

    @Test
    public void footprintInsideTheZoneIsRefused() {
        assertTrue(ForeignZones.blocks(null, new StructureBoundingBox(1010, 0, 1010, 1040, 255, 1040)));
    }

    @Test
    public void footprintTouchingTheEdgeIsRefused() {
        assertTrue(ForeignZones.blocks(null, new StructureBoundingBox(960, 0, 1090, 1000, 255, 1200)));
    }

    @Test
    public void footprintElsewhereIsFree() {
        assertFalse(ForeignZones.blocks(null, new StructureBoundingBox(0, 0, 0, 60, 255, 60)));
    }

    @Test
    public void missingModMeansNoZones() {
        assertFalse(ForeignZones.lookup("no.such.mod.Zone").test(null, new StructureBoundingBox(1010, 0, 1010, 1040, 255, 1040)));
    }
}
