package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.palette.CitadelArchetype;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DimSitesTest {

    /** A column from a string read bottom up from y = 0: '#' solid, '.' air, '~' liquid; air above it. */
    private static GroundFinder.Column column(String cells) {
        return y -> {
            if (y < 0 || y >= cells.length()) {
                return GroundFinder.AIR;
            }
            char c = cells.charAt(y);
            return c == '#' ? GroundFinder.SOLID : c == '~' ? GroundFinder.LIQUID : GroundFinder.AIR;
        };
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    // ------------------------------------------------------------------ ground

    @Test
    public void cavernFloorIsTheFirstFloorWithHeadroom() {
        // rock to 39, cavern 40..59, roof from 60
        GroundFinder.Column cave = column(repeat('#', 40) + repeat('.', 20) + repeat('#', 68));
        assertEquals(40, GroundFinder.cavernFloor(cave, 32, 116, 6));
    }

    @Test
    public void cavernFloorSkipsLowLedgesAndLava() {
        // a 3-high pocket at 34..36 is too low; the real floor is at 50
        GroundFinder.Column ledge = column(repeat('#', 34) + "..." + repeat('#', 13) + repeat('.', 30) + repeat('#', 48));
        assertEquals(50, GroundFinder.cavernFloor(ledge, 32, 116, 6));
        // a lava sea up to 31 with open air above: no floor
        GroundFinder.Column lava = column(repeat('#', 10) + repeat('~', 22) + repeat('.', 80) + repeat('#', 16));
        assertEquals(GroundFinder.NONE, GroundFinder.cavernFloor(lava, 32, 116, 6));
        // solid rock all the way: no floor, and never the top of the bedrock roof
        assertEquals(GroundFinder.NONE, GroundFinder.cavernFloor(column(repeat('#', 128)), 32, 116, 6));
    }

    @Test
    public void cavernFloorNeedsHeadroomBelowTheCeiling() {
        // floor at 113 with air above: 113..118 would pass the ceiling of 116
        GroundFinder.Column high = column(repeat('#', 113) + repeat('.', 15));
        assertEquals(GroundFinder.NONE, GroundFinder.cavernFloor(high, 32, 116, 6));
        assertEquals(113, GroundFinder.cavernFloor(high, 32, 118, 6));
    }

    @Test
    public void surfaceOfIslandAndVoid() {
        assertEquals(61, GroundFinder.surface(column(repeat('.', 40) + repeat('#', 21)), 255, 5));
        assertEquals(GroundFinder.NONE, GroundFinder.surface(column(""), 255, 5));
        assertEquals(GroundFinder.NONE, GroundFinder.surface(column(repeat('#', 30) + "~~"), 255, 5));
    }

    @Test
    public void usableRejectsSitesHangingOverTheEdge() {
        int[] flat = new int[16];
        Arrays.fill(flat, 60);
        flat[3] = 58;
        assertEquals(16, GroundFinder.usable(flat).length);
        assertEquals(58, GroundFinder.usable(flat)[0]);
        flat[0] = GroundFinder.NONE;
        flat[1] = GroundFinder.NONE;
        assertEquals("two of sixteen are allowed", 14, GroundFinder.usable(flat).length);
        flat[2] = GroundFinder.NONE;
        assertNull("three of sixteen hang over the void", GroundFinder.usable(flat));
        int[] none = new int[4];
        Arrays.fill(none, GroundFinder.NONE);
        assertNull(GroundFinder.usable(none));
    }

    // ------------------------------------------------------------------ dimension profile

    @Test
    public void kindByDimension() {
        assertEquals(DimKind.NETHER, DimKind.of(-1, "the_nether", "net.minecraft.world.WorldProviderHell"));
        assertEquals(DimKind.NETHER, DimKind.of(-1, "the_nether", "biomesoplenty.common.world.WorldProviderHellBOP"));
        assertEquals(DimKind.END, DimKind.of(1, "the_end", "net.minecraft.world.WorldProviderEnd"));
        assertNull(DimKind.of(0, "overworld", "net.minecraft.world.WorldProviderSurface"));
        assertEquals(DimKind.ATUM, DimKind.of(17, "atum", "whatever.Provider"));
        assertEquals(DimKind.ATUM, DimKind.of(17, "other", "com.teammetallurgy.atum.world.WorldProviderAtum"));
        assertNull(DimKind.of(7, "twilight_forest", "twilightforest.world.WorldProviderTwilightForest"));
        assertNull(DimKind.of(7, null, null));
    }

    @Test
    public void everyKindHasItsOwnArchetypeAndKey() {
        assertEquals(CitadelArchetype.NETHER_CITADEL, DimKind.NETHER.archetype());
        assertEquals(CitadelArchetype.END_CITADEL, DimKind.END.archetype());
        assertEquals(CitadelArchetype.ATUM_CITADEL, DimKind.ATUM.archetype());
        for (DimKind kind : DimKind.values()) {
            assertEquals(kind, DimKind.byKey(kind.key()));
        }
        assertNull(DimKind.byKey("overworld"));
    }

    // ------------------------------------------------------------------ sites

    @Test
    public void sectionGrowsWithSpacing() {
        assertEquals(48, DimSites.sectionSize(32, 1.5D));
        assertEquals(32, DimSites.sectionSize(32, 0.2D));
        assertEquals(64, DimSites.sectionSize(32, 2.0D));
    }

    @Test
    public void candidatesAreDeterministicAndInsideTheirSection() {
        int section = 48;
        for (int sx = -3; sx <= 3; sx++) {
            for (int sz = -3; sz <= 3; sz++) {
                int[][] sites = DimSites.candidates(12345L, DimKind.NETHER, sx, sz, section);
                assertEquals(DimSites.CANDIDATES, sites.length);
                int[][] again = DimSites.candidates(12345L, DimKind.NETHER, sx, sz, section);
                for (int i = 0; i < sites.length; i++) {
                    assertArrayEquals(sites[i], again[i]);
                    assertEquals(sx, Math.floorDiv(sites[i][0], section));
                    assertEquals(sz, Math.floorDiv(sites[i][1], section));
                    assertTrue(DimSites.candidateIndex(12345L, DimKind.NETHER, sites[i][0], sites[i][1], section) >= 0);
                }
            }
        }
    }

    @Test
    public void candidatesAreRareAndDifferByKindAndSeed() {
        int section = 48;
        int hits = 0;
        for (int x = 0; x < section; x++) {
            for (int z = 0; z < section; z++) {
                hits += DimSites.candidateIndex(99L, DimKind.END, x, z, section) >= 0 ? 1 : 0;
            }
        }
        assertTrue("candidates in one section: " + hits, hits >= 1 && hits <= DimSites.CANDIDATES);
        assertFalse(Arrays.deepEquals(DimSites.candidates(99L, DimKind.END, 2, 5, section),
                DimSites.candidates(99L, DimKind.NETHER, 2, 5, section)));
        assertFalse(Arrays.deepEquals(DimSites.candidates(99L, DimKind.END, 2, 5, section),
                DimSites.candidates(100L, DimKind.END, 2, 5, section)));
    }

    @Test
    public void neighbouringSeedsAreScrambled() {
        HashSet<Integer> first = new HashSet<>();
        for (int chunk = 0; chunk < 64; chunk++) {
            first.add(new Random(DimSites.buildSeed(1L, DimKind.NETHER, chunk, 0)).nextInt(1000));
        }
        assertTrue("first draws of neighbouring chunks repeat: " + first.size(), first.size() > 50);
        assertNotEquals(DimSites.scramble(1L), DimSites.scramble(2L));
    }

    @Test
    public void outerEndKeepsTheWholeBoxAwayFromTheCentre() {
        assertFalse("central island", DimSites.isOuterEnd(-20, -20, 20, 20, 1000));
        assertFalse("box reaching back inside the ring", DimSites.isOuterEnd(950, -30, 1100, 30, 1000));
        assertTrue(DimSites.isOuterEnd(1001, -30, 1100, 30, 1000));
        assertTrue(DimSites.isOuterEnd(-1200, -1200, -1100, -1100, 1000));
        assertFalse("diagonal still inside", DimSites.isOuterEnd(700, 700, 800, 800, 1000));
        assertTrue(DimSites.isOuterEnd(710, 710, 800, 800, 1000));
        assertFalse("gateway ring", DimSites.isOuterEnd(90, 0, 100, 10, 1000));
    }

    // ------------------------------------------------------------------ planner and garrison

    @Test
    public void towersAreShortenedUnderTheNetherRoof() {
        int ceiling = DimensionPlanner.ceiling(DimKind.NETHER);
        assertEquals(GroundFinder.NETHER_ROOF, ceiling);
        int fitted = DimensionPlanner.fitFloors(40, 9, 2, 12, ceiling);
        assertTrue(fitted < 9 && fitted >= 2);
        assertTrue(DimensionPlanner.roofY(40, fitted, 12) + 4 <= ceiling);
        assertTrue("one more floor would not fit", DimensionPlanner.roofY(40, fitted + 1, 12) + 4 > ceiling);
        assertEquals("never below the minimum", 2, DimensionPlanner.fitFloors(100, 6, 2, 12, ceiling));
        assertEquals("the End has room", 9, DimensionPlanner.fitFloors(60, 9, 2, 12, DimensionPlanner.ceiling(DimKind.END)));
    }

    @Test
    public void garrisonNamesAreParsedAndPicked() {
        List<ResourceLocation> ids = DimensionMobs.parse(new String[] {"minecraft:blaze", " wither_skeleton ", "", null,
                "minecraft:blaze", "atum:mummy"});
        assertEquals(Arrays.asList(new ResourceLocation("minecraft", "blaze"), new ResourceLocation("minecraft", "wither_skeleton"),
                new ResourceLocation("atum", "mummy")), ids);
        assertTrue(DimensionMobs.parse(null).isEmpty());
        List<ResourceLocation> two = DimensionMobs.pick(ids, new Random(5L), 2);
        assertEquals(2, two.size());
        assertNotEquals(two.get(0), two.get(1));
        assertTrue(ids.containsAll(two));
        assertEquals(two, DimensionMobs.pick(ids, new Random(5L), 2));
        assertEquals(3, DimensionMobs.pick(ids, new Random(5L), 9).size());
        assertEquals(1, DimensionMobs.pick(ids, new Random(5L), 0).size());
    }
}
