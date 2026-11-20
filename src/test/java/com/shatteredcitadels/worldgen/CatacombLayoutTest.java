package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.catacomb.CatacombLayout;
import net.minecraft.util.EnumFacing;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CatacombLayoutTest {

    private static final EnumFacing[] DIRS = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    private static boolean[][] reach(CatacombLayout layout, int n) {
        boolean[][] seen = new boolean[n][n];
        Deque<int[]> queue = new ArrayDeque<>();
        CatacombLayout.Hall entrance = layout.entrance();
        queue.add(new int[]{entrance.ci0, entrance.cj0});
        seen[entrance.ci0][entrance.cj0] = true;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (EnumFacing d : DIRS) {
                if (!layout.link(c[0], c[1], d)) {
                    continue;
                }
                int ni = c[0] + d.getXOffset();
                int nj = c[1] + d.getZOffset();
                if (!seen[ni][nj]) {
                    seen[ni][nj] = true;
                    queue.add(new int[]{ni, nj});
                }
            }
        }
        return seen;
    }

    @Test
    public void everyHallReachableFromEntrance() {
        for (long seed = 1; seed <= 50; seed++) {
            CatacombLayout layout = CatacombLayout.generate(seed, 19, 9, 9, true, true);
            boolean[][] seen = reach(layout, 19);
            assertNotNull(layout.sanctum());
            assertNotNull(layout.downStair());
            assertTrue("seed " + seed + " has themed halls", layout.halls().size() >= 5);
            for (CatacombLayout.Hall hall : layout.halls()) {
                for (int i = hall.ci0; i <= hall.ci1; i++) {
                    for (int j = hall.cj0; j <= hall.cj1; j++) {
                        assertTrue("seed " + seed + " hall " + hall.kind() + " cell " + i + "," + j, seen[i][j]);
                    }
                }
            }
        }
    }

    @Test
    public void sanctumHasSingleDoorAndIsFarthestQuadrant() {
        for (long seed = 1; seed <= 30; seed++) {
            CatacombLayout layout = CatacombLayout.generate(seed, 19, 3, 3, true, false);
            CatacombLayout.Hall s = layout.sanctum();
            int doors = 0;
            for (int i = s.ci0; i <= s.ci1; i++) {
                for (int j = s.cj0; j <= s.cj1; j++) {
                    for (EnumFacing d : DIRS) {
                        int ni = i + d.getXOffset();
                        int nj = j + d.getZOffset();
                        boolean outside = ni < s.ci0 || ni > s.ci1 || nj < s.cj0 || nj > s.cj1;
                        if (outside && layout.link(i, j, d)) {
                            doors++;
                        }
                    }
                }
            }
            assertEquals("seed " + seed, 1, doors);
            assertTrue((s.ci0 + s.ci1) / 2 > 9 && (s.cj0 + s.cj1) / 2 > 9);
        }
    }

    @Test
    public void deterministic() {
        CatacombLayout a = CatacombLayout.generate(42L, 19, 9, 9, true, true);
        CatacombLayout b = CatacombLayout.generate(42L, 19, 9, 9, true, true);
        for (int i = 0; i < 19; i++) {
            for (int j = 0; j < 19; j++) {
                assertEquals(a.open(i, j), b.open(i, j));
                for (EnumFacing d : DIRS) {
                    assertEquals(a.link(i, j, d), b.link(i, j, d));
                }
            }
        }
    }

    /** Loops among corridor cells (hall interiors are always fully linked, so they are left out). */
    @Test
    public void hasLoops() {
        for (long seed = 1; seed <= 20; seed++) {
            CatacombLayout layout = CatacombLayout.generate(seed, 19, 9, 9, true, true);
            int cells = 0;
            int links = 0;
            for (int i = 0; i < 19; i++) {
                for (int j = 0; j < 19; j++) {
                    if (!layout.open(i, j) || layout.hallAt(i, j) != null) {
                        continue;
                    }
                    cells++;
                    if (i + 1 < 19 && layout.hallAt(i + 1, j) == null && layout.link(i, j, EnumFacing.EAST)) {
                        links++;
                    }
                    if (j + 1 < 19 && layout.hallAt(i, j + 1) == null && layout.link(i, j, EnumFacing.SOUTH)) {
                        links++;
                    }
                }
            }
            assertTrue("seed " + seed + ": " + links + " links, " + cells + " cells", links > cells - 1);
        }
    }

    @Test
    public void fragmentHasOneHallAndNoSanctum() {
        CatacombLayout layout = CatacombLayout.generate(5L, 7, 3, 3, false, false);
        assertEquals(null, layout.sanctum());
        assertEquals(2, layout.halls().size()); // entrance + one themed hall
    }
}
