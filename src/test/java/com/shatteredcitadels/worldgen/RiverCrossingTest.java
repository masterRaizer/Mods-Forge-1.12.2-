package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.bridge.RiverCrossing;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RiverCrossingTest {

    /** Rows are z, characters are x; '~' river, 'o' ocean, '.' land. Outside the rows: land. */
    static RiverCrossing.BiomeMap map(String... rows) {
        return new RiverCrossing.BiomeMap() {
            private char at(int x, int z) {
                return z < 0 || z >= rows.length || x < 0 || x >= rows[z].length() ? '.' : rows[z].charAt(x);
            }

            @Override
            public boolean isRiver(int x, int z) {
                return at(x, z) == '~';
            }

            @Override
            public boolean isWater(int x, int z) {
                return at(x, z) != '.';
            }
        };
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    /** A river flowing along z (north-south), {@code width} wide, starting at x = 10. */
    static String[] northSouth(int width, int length) {
        String row = repeat('.', 10) + repeat('~', width) + repeat('.', 10);
        String[] rows = new String[length];
        java.util.Arrays.fill(rows, row);
        return rows;
    }

    /** The same river turned: flowing along x, starting at z = 10. */
    static String[] eastWest(int width, int length) {
        String[] rows = new String[20 + width];
        for (int z = 0; z < rows.length; z++) {
            rows[z] = repeat(z >= 10 && z < 10 + width ? '~' : '.', length);
        }
        return rows;
    }

    @Test
    public void findsStraightNorthSouthRiver() {
        RiverCrossing crossing = RiverCrossing.find(map(northSouth(7, 60)), 13, 30);
        assertNotNull(crossing);
        assertTrue(crossing.alongX);
        assertEquals(7, crossing.width());
        assertEquals(10, crossing.start);
        assertEquals(16, crossing.end);
        assertEquals(30, crossing.cross);
    }

    @Test
    public void findsEastWestRiver() {
        RiverCrossing crossing = RiverCrossing.find(map(eastWest(12, 60)), 30, 15);
        assertNotNull(crossing);
        assertFalse(crossing.alongX);
        assertEquals(12, crossing.width());
        assertEquals(10, crossing.start);
        assertEquals(21, crossing.end);
        assertEquals(30, crossing.cross);
    }

    @Test
    public void rejectsTooNarrow() {
        assertNull(RiverCrossing.find(map(northSouth(4, 60)), 11, 30));
    }

    @Test
    public void rejectsTooWide() {
        assertNull(RiverCrossing.find(map(northSouth(25, 80)), 20, 40));
    }

    @Test
    public void rejectsBend() {
        // The river shifts 8 blocks sideways just north of the crossing line.
        String[] rows = northSouth(7, 60);
        String shifted = repeat('.', 18) + repeat('~', 7) + repeat('.', 2);
        for (int z = 0; z < 29; z++) {
            rows[z] = shifted;
        }
        rows[29] = repeat('.', 10) + repeat('~', 15) + repeat('.', 2);
        assertNull(RiverCrossing.find(map(rows), 13, 31));
    }

    @Test
    public void rejectsWaterBank() {
        String row = repeat('.', 10) + repeat('~', 7) + repeat('o', 10);
        String[] rows = new String[60];
        java.util.Arrays.fill(rows, row);
        assertNull(RiverCrossing.find(map(rows), 13, 30));
    }

    @Test
    public void rejectsLakeLikeBlob() {
        String[] rows = new String[30];
        for (int z = 0; z < rows.length; z++) {
            rows[z] = z >= 10 && z < 19 ? repeat('.', 10) + repeat('~', 9) + repeat('.', 10) : repeat('.', 29);
        }
        assertNull(RiverCrossing.find(map(rows), 14, 14));
    }

    @Test
    public void nullWhenStartIsNotRiver() {
        assertNull(RiverCrossing.find(map(northSouth(7, 60)), 5, 30));
    }
}
