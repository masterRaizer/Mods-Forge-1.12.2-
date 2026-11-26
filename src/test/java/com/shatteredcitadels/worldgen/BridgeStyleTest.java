package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.bridge.BridgeStyle;
import com.shatteredcitadels.worldgen.bridge.BridgeStyle.Bank;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BridgeStyleTest {

    private static Set<BridgeStyle> styles(Bank bank, int width, double special) {
        Set<BridgeStyle> seen = EnumSet.noneOf(BridgeStyle.class);
        Random rand = new Random(7);
        for (int i = 0; i < 2000; i++) {
            seen.add(BridgeStyle.choose(bank, width, rand, special));
        }
        return seen;
    }

    @Test
    public void everyBankHasItsStyle() {
        assertEquals(EnumSet.of(BridgeStyle.TIMBER, BridgeStyle.STONE_ARCH), styles(Bank.FOREST, 12, 0));
        assertEquals(EnumSet.of(BridgeStyle.TIMBER), styles(Bank.FOREST, 6, 0));
        assertEquals(EnumSet.of(BridgeStyle.SPRUCE), styles(Bank.TAIGA, 12, 0));
        assertEquals(EnumSet.of(BridgeStyle.SANDSTONE), styles(Bank.DESERT, 12, 0));
        assertEquals(EnumSet.of(BridgeStyle.ROPE), styles(Bank.JUNGLE, 12, 0));
        assertEquals(EnumSet.of(BridgeStyle.BOARDWALK), styles(Bank.SWAMP, 12, 0));
        assertEquals(EnumSet.of(BridgeStyle.TIMBER), styles(Bank.SWAMP, 20, 0));
        assertEquals(EnumSet.of(BridgeStyle.SNOW_STONE), styles(Bank.SNOW, 12, 0));
    }

    @Test
    public void specialChanceOneGivesOnlySpecials() {
        assertEquals(EnumSet.of(BridgeStyle.RUINED, BridgeStyle.COVERED, BridgeStyle.TURRETED), styles(Bank.FOREST, 12, 1));
    }

    @Test
    public void specialChanceZeroGivesNone() {
        for (Bank bank : Bank.values()) {
            for (BridgeStyle style : styles(bank, 12, 0)) {
                assertFalse(style.special);
            }
        }
    }

    @Test
    public void turretedNeedsWidth9() {
        assertFalse(styles(Bank.FOREST, 8, 1).contains(BridgeStyle.TURRETED));
        assertTrue(styles(Bank.FOREST, 9, 1).contains(BridgeStyle.TURRETED));
    }

    @Test
    public void turretedAlwaysFortified() {
        assertTrue(BridgeStyle.fortified(BridgeStyle.TURRETED, 9, new Random(1), 0));
        assertFalse(BridgeStyle.fortified(BridgeStyle.COVERED, 20, new Random(1), 1));
        assertFalse(BridgeStyle.fortified(BridgeStyle.RUINED, 20, new Random(1), 1));
    }

    @Test
    public void ropeAndBoardwalkNeverFortified() {
        assertFalse(BridgeStyle.fortified(BridgeStyle.ROPE, 20, new Random(1), 1));
        assertFalse(BridgeStyle.fortified(BridgeStyle.BOARDWALK, 16, new Random(1), 1));
        assertFalse(BridgeStyle.fortified(BridgeStyle.TIMBER, 8, new Random(1), 1));
        assertTrue(BridgeStyle.fortified(BridgeStyle.TIMBER, 9, new Random(1), 1));
    }

    @Test
    public void fortifiedShareIsAboutOneFifth() {
        Random rand = new Random(42);
        int count = 0;
        for (int i = 0; i < 10000; i++) {
            if (BridgeStyle.fortified(BridgeStyle.TIMBER, 12, rand, 0.2)) {
                count++;
            }
        }
        assertTrue("fortified " + count, count >= 1700 && count <= 2300);
    }

    @Test
    public void byIdRoundTrips() {
        for (BridgeStyle style : BridgeStyle.values()) {
            assertEquals(style, BridgeStyle.byId(style.id()));
        }
        assertNull(BridgeStyle.byId("nope"));
    }
}
