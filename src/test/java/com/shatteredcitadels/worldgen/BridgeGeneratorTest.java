package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.bridge.BridgeGenerator;
import com.shatteredcitadels.worldgen.bridge.BridgePlan;
import com.shatteredcitadels.worldgen.bridge.BridgeStyle;
import com.shatteredcitadels.worldgen.bridge.RiverCrossing;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BridgeGeneratorTest {

    private static final BiFunction<Integer, Integer, BridgeStyle.Bank> FOREST = (x, z) -> BridgeStyle.Bank.FOREST;

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    /** A river 7 wide along z at x = 10..16: the centre of chunk (0, 1) is the river column (8, 24)... */
    private static RiverCrossing.BiomeMap river() {
        String[] rows = new String[80];
        java.util.Arrays.fill(rows, "....~~~~~~~..............");
        return RiverCrossingTest.map(rows);
    }

    private static BridgePlan plan(double chance, BiFunction<Integer, Integer, BridgeStyle.Bank> bank, long seed) {
        return BridgeGenerator.planAt(river(), bank, 0, 2, seed, 63, 0, chance, 0.2, 0.1);
    }

    @Test
    public void planAtRiverChunkGivesPlan() {
        BridgePlan plan = plan(1.0, FOREST, 5L);
        assertNotNull(plan);
        assertEquals(0, plan.dimension());
        // The deck spans the river (x 4..10) on the row of the chunk centre (z 40).
        assertTrue(plan.describe(), plan.describe().endsWith(" at 7,40"));
        assertTrue(plan.footprint().minX < 4 && plan.footprint().maxX > 10);
    }

    @Test
    public void planAtLandChunkIsNull() {
        assertNull(BridgeGenerator.planAt(river(), FOREST, 1, 2, 5L, 63, 0, 1.0, 0.2, 0.1));
    }

    @Test
    public void chanceZeroGivesNoPlan() {
        assertNull(plan(0.0, FOREST, 5L));
    }

    @Test
    public void mountainBankGivesNoPlan() {
        assertNull(plan(1.0, (x, z) -> null, 5L));
        // One bank is enough to refuse.
        assertNull(plan(1.0, (x, z) -> x > 7 ? null : BridgeStyle.Bank.FOREST, 5L));
    }

    @Test
    public void startBankDecidesTheStyle() {
        for (long seed = 0; seed < 20; seed++) {
            BridgePlan plan = BridgeGenerator.planAt(river(),
                    (x, z) -> x < 7 ? BridgeStyle.Bank.DESERT : BridgeStyle.Bank.FOREST, 0, 2, seed, 63, 0, 1.0, 0.0, 0.0);
            assertEquals(BridgeStyle.SANDSTONE, plan.style());
        }
    }

    @Test
    public void sameSeedSamePlan() {
        for (long seed = 0; seed < 30; seed++) {
            BridgePlan a = plan(1.0, FOREST, seed);
            BridgePlan b = plan(1.0, FOREST, seed);
            assertEquals(a.describe(), b.describe());
            assertEquals(a.writeToNBT(), b.writeToNBT());
        }
    }

    @Test
    public void chanceIsRoughlyHonoured() {
        int made = 0;
        for (long seed = 0; seed < 400; seed++) {
            if (plan(0.5, FOREST, seed) != null) {
                made++;
            }
        }
        assertTrue("made " + made, made > 150 && made < 250);
    }

    @Test
    public void secondCrossingNearPendingBridgeIsSkipped() {
        List<StructurePlan> pending = new ArrayList<>();
        pending.add(plan(1.0, FOREST, 5L));
        // The pending bridge sits in chunk (0, 2).
        assertTrue(StructureJobs.isNearKind(pending, BridgePlan.KIND, 3, 10, 24));
        assertFalse(StructureJobs.isNearKind(pending, BridgePlan.KIND, 30, 2, 24));
        assertFalse(StructureJobs.isNearKind(pending, "citadel", 0, 2, 24));
    }

    @Test
    public void specialsStayRareAcrossTheChunksOfOneWorld() {
        RiverCrossing.BiomeMap map = new RiverCrossing.BiomeMap() {
            @Override
            public boolean isRiver(int x, int z) {
                return x >= 4 && x <= 14;
            }

            @Override
            public boolean isWater(int x, int z) {
                return isRiver(x, z);
            }
        };
        for (long seed : new long[]{0L, 77L, -4502399128455731L}) {
            int made = 0;
            int special = 0;
            for (int cz = -1000; cz < 1000; cz++) {
                BridgePlan plan = BridgeGenerator.planAt(map, FOREST, 0, cz, seed, 63, 0, 0.5, 0.2, 0.1);
                if (plan != null) {
                    made++;
                    if (plan.style().special) {
                        special++;
                    }
                }
            }
            assertTrue("made " + made, made > 850 && made < 1150);
            assertTrue("special " + special + " of " + made, special > made * 0.05 && special < made * 0.16);
        }
    }

    @Test
    public void neighbouringChunksRollIndependently() {
        RiverCrossing.BiomeMap map = new RiverCrossing.BiomeMap() {
            @Override
            public boolean isRiver(int x, int z) {
                int m = Math.floorMod(x, 32);
                return m >= 4 && m <= 14;
            }

            @Override
            public boolean isWater(int x, int z) {
                return isRiver(x, z);
            }
        };
        long seed = 2274807239826543300L;
        int both = 0;
        int pairs = 0;
        for (int cx = -40; cx <= 40; cx += 2) {
            boolean previous = false;
            for (int cz = -100; cz < 100; cz++) {
                boolean made = BridgeGenerator.planAt(map, FOREST, cx, cz, seed, 63, 0, 0.5, 0.2, 0.1) != null;
                if (cz > -100) {
                    pairs++;
                    if (made && previous) {
                        both++;
                    }
                }
                previous = made;
            }
        }
        // Independent halves meet in a quarter of the pairs; a seed that only steps does it far more often.
        assertTrue("both " + both + " of " + pairs, both > pairs * 0.21 && both < pairs * 0.29);
    }

    @Test
    public void noBridgeNearTheSpawn() {
        assertTrue(BridgeGenerator.isNearSpawn(3, -4, 0, 0));
        assertTrue(BridgeGenerator.isNearSpawn(18, 12, 10, 10));
        assertFalse(BridgeGenerator.isNearSpawn(9, 0, 0, 0));
        assertFalse(BridgeGenerator.isNearSpawn(0, -9, 0, 0));
    }

    @Test
    public void commandPlanOverNonRiverUsesFallbackSpan() {
        RiverCrossing.BiomeMap land = RiverCrossingTest.map("....");
        BridgePlan plan = BridgeGenerator.planForCommand(land, 100, 200, true, 1, BridgeStyle.ROPE, null,
                BridgeStyle.Bank.FOREST, 9L, 63, 0);
        assertEquals(BridgeStyle.ROPE, plan.style());
        // A span of 12 centred 10 blocks ahead on +x.
        assertTrue(plan.describe(), plan.describe().endsWith(" at 110,200"));
        BridgePlan z = BridgeGenerator.planForCommand(land, 100, 200, false, 1, null, Boolean.TRUE,
                BridgeStyle.Bank.FOREST, 9L, 63, 0);
        assertTrue(z.describe(), z.describe().endsWith(" at 100,210"));
        assertTrue(z.fortified() || !z.style().solidDeck);
    }

    @Test
    public void commandPlanFindsTheRiverAhead() {
        BridgePlan plan = BridgeGenerator.planForCommand(river(), -6, 40, true, 1, BridgeStyle.STONE_ARCH, Boolean.FALSE,
                BridgeStyle.Bank.FOREST, 9L, 63, 0);
        assertTrue(plan.describe(), plan.describe().endsWith(" at 7,40"));
        assertFalse(plan.fortified());
    }

    @Test
    public void configLangKeysExistInBothLanguages() throws Exception {
        String[] keys = {"bridges", "bridges.bridgesEnabled", "bridges.bridgeMinDistanceChunks", "bridges.bridgeChance",
                "bridges.bridgeFortifiedChance", "bridges.bridgeSpecialChance", "bridges.bridgeDimensions"};
        for (String lang : new String[]{"en_us", "ru_ru"}) {
            String text = new String(Files.readAllBytes(
                    new File("src/main/resources/assets/shatteredcitadels/lang/" + lang + ".lang").toPath()),
                    StandardCharsets.UTF_8);
            for (String key : keys) {
                assertTrue(lang + " " + key, text.contains("\nconfig.shatteredcitadels." + key + "="));
            }
        }
    }
}
