package com.shatteredcitadels.worldgen.catacomb;

import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Plan of one catacomb level on a square grid of cells (pure logic, no world access). Halls are
 * placed first (entrance stair, optional sanctum and down stair, then themed halls), every other
 * cell becomes a corridor joined into a randomized maze, halls open onto the maze through one or
 * two doorways (the sanctum through exactly one) and extra links turn the maze into loops.
 */
public final class CatacombLayout {

    public enum HallKind { CRYPT, SPIDER_CAVE, SKELETON_HALL, STOREROOM, GUARD_POST, SANCTUM, STAIR }

    /** Rectangle of cells, inclusive. */
    public static final class Hall {
        public final int ci0;
        public final int cj0;
        public final int ci1;
        public final int cj1;
        private final HallKind kind;

        Hall(HallKind kind, int ci0, int cj0, int ci1, int cj1) {
            this.kind = kind;
            this.ci0 = ci0;
            this.cj0 = cj0;
            this.ci1 = ci1;
            this.cj1 = cj1;
        }

        public HallKind kind() {
            return kind;
        }

        public boolean contains(int ci, int cj) {
            return ci >= ci0 && ci <= ci1 && cj >= cj0 && cj <= cj1;
        }

        /** True if this hall plus a gap of {@code gap} cells overlaps the rectangle. */
        boolean near(int i0, int j0, int i1, int j1, int gap) {
            return i0 <= ci1 + gap && i1 >= ci0 - gap && j0 <= cj1 + gap && j1 >= cj0 - gap;
        }
    }

    private static final int SANCTUM_CELLS = 5;

    private final int n;
    private final boolean[][] open;
    /** Link to (i+1, j). */
    private final boolean[][] east;
    /** Link to (i, j+1). */
    private final boolean[][] south;
    private final Hall[][] hallOf;
    private final List<Hall> halls = new ArrayList<>();
    private Hall entrance;
    private Hall sanctum;
    private Hall downStair;

    private CatacombLayout(int n) {
        this.n = n;
        this.open = new boolean[n][n];
        this.east = new boolean[n][n];
        this.south = new boolean[n][n];
        this.hallOf = new Hall[n][n];
    }

    /**
     * @param cells     cells per side (odd; 19 for a full level, 7 for a fragment)
     * @param sanctum   reserve the mage sanctum in the corner farthest from the entrance
     * @param downStair reserve a stair hall leading to the next level
     */
    public static CatacombLayout generate(long seed, int cells, int stairCi, int stairCj, boolean sanctum, boolean downStair) {
        Random rand = new Random(seed);
        CatacombLayout layout = new CatacombLayout(cells);
        int si = clamp(stairCi, 0, cells - 1);
        int sj = clamp(stairCj, 0, cells - 1);
        layout.entrance = layout.addHall(HallKind.STAIR, si, sj, si, sj);
        if (sanctum && cells >= SANCTUM_CELLS + 4) {
            layout.sanctum = layout.placeSanctum(si, sj);
        }
        if (downStair) {
            layout.downStair = layout.placeDownStair(rand, si, sj);
        }
        layout.placeThemedHalls(rand);
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                layout.open[i][j] = true;
            }
        }
        layout.carveMaze(rand);
        layout.openHalls(rand);
        layout.pruneDeadEnds(rand, cells >= 11 ? 12 : 1);
        layout.addLoops(rand);
        return layout;
    }

    // ------------------------------------------------------------------ queries

    public int cells() {
        return n;
    }

    public boolean open(int ci, int cj) {
        return inside(ci, cj) && open[ci][cj];
    }

    public boolean link(int ci, int cj, EnumFacing dir) {
        int ni = ci + dir.getXOffset();
        int nj = cj + dir.getZOffset();
        if (!inside(ci, cj) || !inside(ni, nj) || dir.getAxis() == EnumFacing.Axis.Y) {
            return false;
        }
        int ai = Math.min(ci, ni);
        int aj = Math.min(cj, nj);
        return dir.getAxis() == EnumFacing.Axis.X ? east[ai][aj] : south[ai][aj];
    }

    /** @return the hall covering the cell, or null for a corridor cell. */
    public Hall hallAt(int ci, int cj) {
        return inside(ci, cj) ? hallOf[ci][cj] : null;
    }

    public List<Hall> halls() {
        return Collections.unmodifiableList(halls);
    }

    public Hall entrance() {
        return entrance;
    }

    public Hall sanctum() {
        return sanctum;
    }

    public Hall downStair() {
        return downStair;
    }

    // ------------------------------------------------------------------ halls

    private Hall addHall(HallKind kind, int i0, int j0, int i1, int j1) {
        Hall hall = new Hall(kind, i0, j0, i1, j1);
        halls.add(hall);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                hallOf[i][j] = hall;
                if (i < i1) {
                    east[i][j] = true;
                }
                if (j < j1) {
                    south[i][j] = true;
                }
            }
        }
        return hall;
    }

    private boolean free(int i0, int j0, int i1, int j1) {
        if (i0 < 0 || j0 < 0 || i1 >= n || j1 >= n) {
            return false;
        }
        for (Hall hall : halls) {
            if (hall.near(i0, j0, i1, j1, 1)) {
                return false;
            }
        }
        return true;
    }

    /** In the corner farthest from the entrance, one cell in from the edge. */
    private Hall placeSanctum(int si, int sj) {
        boolean high_i = si < n / 2;
        boolean high_j = sj < n / 2;
        int i0 = high_i ? n - 1 - SANCTUM_CELLS : 1;
        int j0 = high_j ? n - 1 - SANCTUM_CELLS : 1;
        return addHall(HallKind.SANCTUM, i0, j0, i0 + SANCTUM_CELLS - 1, j0 + SANCTUM_CELLS - 1);
    }

    private Hall placeDownStair(Random rand, int si, int sj) {
        int min = n / 3;
        for (int attempt = 0; attempt < 400; attempt++) {
            int i = 1 + rand.nextInt(n - 2);
            int j = 1 + rand.nextInt(n - 2);
            if (Math.abs(i - si) + Math.abs(j - sj) >= min && free(i, j, i, j)) {
                return addHall(HallKind.STAIR, i, j, i, j);
            }
        }
        // Fallback: any free cell, scanning from the far corner.
        for (int i = n - 2; i > 0; i--) {
            for (int j = n - 2; j > 0; j--) {
                if (free(i, j, i, j) && (i != si || j != sj)) {
                    return addHall(HallKind.STAIR, i, j, i, j);
                }
            }
        }
        return null;
    }

    private void placeThemedHalls(Random rand) {
        List<HallKind> big = new ArrayList<>();
        Collections.addAll(big, HallKind.CRYPT, HallKind.SPIDER_CAVE, HallKind.SKELETON_HALL);
        Collections.shuffle(big, rand);
        List<HallKind> wanted = new ArrayList<>();
        if (n < 11) {
            wanted.add(big.get(0));
        } else {
            int themed = 2 + rand.nextInt(3);
            for (int k = 0; k < themed; k++) {
                wanted.add(k < big.size() ? big.get(k) : HallKind.SKELETON_HALL);
            }
            int rooms = 1 + rand.nextInt(2);
            for (int k = 0; k < rooms; k++) {
                wanted.add(rand.nextBoolean() ? HallKind.STOREROOM : HallKind.GUARD_POST);
            }
        }
        for (HallKind kind : wanted) {
            boolean small = kind == HallKind.STOREROOM || kind == HallKind.GUARD_POST || n < 11;
            for (int attempt = 0; attempt < 80; attempt++) {
                int w = small ? 2 : 2 + rand.nextInt(3);
                int d = small ? 2 : 2 + rand.nextInt(2);
                if (rand.nextBoolean()) {
                    int t = w;
                    w = d;
                    d = t;
                }
                int i0 = rand.nextInt(Math.max(1, n - w + 1));
                int j0 = rand.nextInt(Math.max(1, n - d + 1));
                if (free(i0, j0, i0 + w - 1, j0 + d - 1)) {
                    addHall(kind, i0, j0, i0 + w - 1, j0 + d - 1);
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ maze

    private static final EnumFacing[] DIRS = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    private void setLink(int ci, int cj, EnumFacing dir) {
        int ni = ci + dir.getXOffset();
        int nj = cj + dir.getZOffset();
        int ai = Math.min(ci, ni);
        int aj = Math.min(cj, nj);
        if (dir.getAxis() == EnumFacing.Axis.X) {
            east[ai][aj] = true;
        } else {
            south[ai][aj] = true;
        }
    }

    private boolean inside(int i, int j) {
        return i >= 0 && j >= 0 && i < n && j < n;
    }

    /** Randomized depth-first maze over every corridor cell (iterative, so large grids are safe). */
    private void carveMaze(Random rand) {
        boolean[][] visited = new boolean[n][n];
        for (int si = 0; si < n; si++) {
            for (int sj = 0; sj < n; sj++) {
                if (hallOf[si][sj] != null || visited[si][sj]) {
                    continue;
                }
                List<int[]> stack = new ArrayList<>();
                stack.add(new int[]{si, sj});
                visited[si][sj] = true;
                while (!stack.isEmpty()) {
                    int[] c = stack.get(stack.size() - 1);
                    List<EnumFacing> options = new ArrayList<>(4);
                    for (EnumFacing d : DIRS) {
                        int ni = c[0] + d.getXOffset();
                        int nj = c[1] + d.getZOffset();
                        if (inside(ni, nj) && hallOf[ni][nj] == null && !visited[ni][nj]) {
                            options.add(d);
                        }
                    }
                    if (options.isEmpty()) {
                        stack.remove(stack.size() - 1);
                        continue;
                    }
                    EnumFacing d = options.get(rand.nextInt(options.size()));
                    setLink(c[0], c[1], d);
                    int ni = c[0] + d.getXOffset();
                    int nj = c[1] + d.getZOffset();
                    visited[ni][nj] = true;
                    stack.add(new int[]{ni, nj});
                }
                // A second component (cut off by halls) is joined later through a hall's doorways.
            }
        }
    }

    /** Doorways from each hall to the corridors: one for the sanctum, one or two for the rest. */
    private void openHalls(Random rand) {
        for (Hall hall : halls) {
            List<int[]> candidates = new ArrayList<>();
            for (int i = hall.ci0; i <= hall.ci1; i++) {
                for (int j = hall.cj0; j <= hall.cj1; j++) {
                    for (EnumFacing d : DIRS) {
                        int ni = i + d.getXOffset();
                        int nj = j + d.getZOffset();
                        if (inside(ni, nj) && hallOf[ni][nj] == null) {
                            candidates.add(new int[]{i, j, d.getIndex()});
                        }
                    }
                }
            }
            Collections.shuffle(candidates, rand);
            int doors = hall.kind == HallKind.SANCTUM ? 1 : Math.min(candidates.size(), 1 + rand.nextInt(2));
            for (int k = 0; k < doors && k < candidates.size(); k++) {
                int[] c = candidates.get(k);
                setLink(c[0], c[1], EnumFacing.byIndex(c[2]));
            }
        }
        joinComponents();
    }

    /** Corridor regions separated by halls are joined by linking across the dividing hall's border. */
    private void joinComponents() {
        int[][] comp = new int[n][n];
        for (int[] row : comp) {
            java.util.Arrays.fill(row, -1);
        }
        int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (comp[i][j] < 0) {
                    flood(comp, i, j, count++);
                }
            }
        }
        while (count > 1) {
            boolean joined = false;
            for (int i = 0; i < n && !joined; i++) {
                for (int j = 0; j < n && !joined; j++) {
                    for (EnumFacing d : DIRS) {
                        int ni = i + d.getXOffset();
                        int nj = j + d.getZOffset();
                        if (!inside(ni, nj) || comp[i][j] == comp[ni][nj]) {
                            continue;
                        }
                        // Never add a second doorway to the sanctum.
                        if (hallOf[i][j] != null && hallOf[i][j] == sanctum || hallOf[ni][nj] != null && hallOf[ni][nj] == sanctum) {
                            continue;
                        }
                        setLink(i, j, d);
                        joined = true;
                        break;
                    }
                }
            }
            if (!joined) {
                return;
            }
            for (int[] row : comp) {
                java.util.Arrays.fill(row, -1);
            }
            count = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (comp[i][j] < 0) {
                        flood(comp, i, j, count++);
                    }
                }
            }
        }
    }

    private void flood(int[][] comp, int si, int sj, int id) {
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[]{si, sj});
        comp[si][sj] = id;
        while (!stack.isEmpty()) {
            int[] c = stack.remove(stack.size() - 1);
            for (EnumFacing d : DIRS) {
                int ni = c[0] + d.getXOffset();
                int nj = c[1] + d.getZOffset();
                if (inside(ni, nj) && comp[ni][nj] < 0 && link(c[0], c[1], d)) {
                    comp[ni][nj] = id;
                    stack.add(new int[]{ni, nj});
                }
            }
        }
    }

    /** About one extra corridor link per twelve corridor cells, so there is more than one way. */
    private void addLoops(Random rand) {
        int corridorCells = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (hallOf[i][j] == null && open[i][j]) {
                    corridorCells++;
                }
            }
        }
        int wanted = Math.max(1, corridorCells / 12);
        for (int attempt = 0; attempt < wanted * 20 && wanted > 0; attempt++) {
            int i = rand.nextInt(n);
            int j = rand.nextInt(n);
            EnumFacing d = DIRS[rand.nextInt(4)];
            int ni = i + d.getXOffset();
            int nj = j + d.getZOffset();
            if (!inside(ni, nj) || hallOf[i][j] != null || hallOf[ni][nj] != null || !open[i][j] || !open[ni][nj]
                    || link(i, j, d)) {
                continue;
            }
            setLink(i, j, d);
            wanted--;
        }
    }

    /**
     * Fills back part of the dead ends, a few passes deep, so stretches of solid rock break up the
     * grid and the corridors that remain wind. Removing a dead end never disconnects anything.
     */
    private void pruneDeadEnds(Random rand, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            List<int[]> ends = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (!open[i][j] || hallOf[i][j] != null) {
                        continue;
                    }
                    int links = 0;
                    for (EnumFacing d : DIRS) {
                        if (link(i, j, d)) {
                            links++;
                        }
                    }
                    if (links <= 1) {
                        ends.add(new int[]{i, j});
                    }
                }
            }
            for (int[] c : ends) {
                if (rand.nextInt(10) < 8) {
                    close(c[0], c[1]);
                }
            }
        }
    }

    private void close(int ci, int cj) {
        open[ci][cj] = false;
        for (EnumFacing d : DIRS) {
            int ni = ci + d.getXOffset();
            int nj = cj + d.getZOffset();
            if (!inside(ni, nj)) {
                continue;
            }
            int ai = Math.min(ci, ni);
            int aj = Math.min(cj, nj);
            if (d.getAxis() == EnumFacing.Axis.X) {
                east[ai][aj] = false;
            } else {
                south[ai][aj] = false;
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
