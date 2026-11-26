package com.shatteredcitadels.worldgen.bridge;

/**
 * A straight, axis-aligned place to bridge a river, found on the biome map alone (no blocks, no chunk
 * loads): the river is 5 to 24 wide there, runs on for at least twice that, and has land on both banks.
 */
public final class RiverCrossing {

    public static final int MIN_WIDTH = 5;
    public static final int MAX_WIDTH = 24;
    /** Runs are measured up to this far each way; a run that reaches it counts as "long". */
    private static final int REACH = 41;
    private static final int SIDE = 3;
    private static final int SIDE_TOLERANCE = 4;

    /** The biome map as the finder sees it. */
    public interface BiomeMap {
        boolean isRiver(int x, int z);

        /** River, ocean or any other open-water biome. A river column is water. */
        boolean isWater(int x, int z);
    }

    /** True if the deck runs along X (the river flows along Z). */
    public final boolean alongX;
    /** First and last river coordinate on the deck axis. */
    public final int start;
    public final int end;
    /** The deck's coordinate on the other axis. */
    public final int cross;

    public RiverCrossing(boolean alongX, int start, int end, int cross) {
        this.alongX = alongX;
        this.start = start;
        this.end = end;
        this.cross = cross;
    }

    public int width() {
        return end - start + 1;
    }

    /** @return the crossing through the river column {@code (x, z)}, or null if this is no place for a bridge. */
    public static RiverCrossing find(BiomeMap map, int x, int z) {
        if (!map.isRiver(x, z)) {
            return null;
        }
        int[] runX = run(map, true, x, z);
        int[] runZ = run(map, false, z, x);
        int lengthX = runX[1] - runX[0] + 1;
        int lengthZ = runZ[1] - runZ[0] + 1;
        boolean alongX = lengthX <= lengthZ;
        int[] deck = alongX ? runX : runZ;
        int width = alongX ? lengthX : lengthZ;
        int other = alongX ? lengthZ : lengthX;
        int cross = alongX ? z : x;
        int along = alongX ? x : z;
        if (width < MIN_WIDTH || width > MAX_WIDTH || other < 2 * width) {
            return null;
        }
        for (int side = -SIDE; side <= SIDE; side += 2 * SIDE) {
            if (!river(map, alongX, along, cross + side)) {
                return null;
            }
            int[] beside = run(map, alongX, along, cross + side);
            if (Math.abs(beside[0] - deck[0]) > SIDE_TOLERANCE || Math.abs(beside[1] - deck[1]) > SIDE_TOLERANCE) {
                return null;
            }
        }
        for (int out = 1; out <= 4; out++) {
            if (water(map, alongX, deck[0] - out, cross) || water(map, alongX, deck[1] + out, cross)) {
                return null;
            }
        }
        return new RiverCrossing(alongX, deck[0], deck[1], cross);
    }

    private static boolean river(BiomeMap map, boolean alongX, int along, int cross) {
        return alongX ? map.isRiver(along, cross) : map.isRiver(cross, along);
    }

    private static boolean water(BiomeMap map, boolean alongX, int along, int cross) {
        return alongX ? map.isWater(along, cross) : map.isWater(cross, along);
    }

    /** @return the first and last river coordinate of the run through {@code along} on the given axis. */
    private static int[] run(BiomeMap map, boolean alongX, int along, int cross) {
        int first = along;
        int last = along;
        while (along - first < REACH && river(map, alongX, first - 1, cross)) {
            first--;
        }
        while (last - along < REACH && river(map, alongX, last + 1, cross)) {
            last++;
        }
        return new int[]{first, last};
    }
}
