package com.shatteredcitadels.herobrine;

/**
 * The classic altar: a 3x3 of gold blocks, netherrack on the centre one and a redstone torch on each
 * of the four gold blocks next to the netherrack. Positions are relative to the netherrack.
 */
public final class AltarPattern {

    public enum Cell { GOLD, NETHERRACK, TORCH, OTHER }

    /** What stands at an offset from the netherrack. */
    public interface Cells {
        Cell at(int dx, int dy, int dz);
    }

    /** The four torch offsets (x, z) around the netherrack. */
    private static final int[][] TORCHES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private AltarPattern() {
    }

    /** @return the four torch offsets (x, z) around the netherrack */
    public static int[][] torches() {
        int[][] copy = new int[TORCHES.length][];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = TORCHES[i].clone();
        }
        return copy;
    }

    public static boolean matches(Cells cells) {
        if (cells.at(0, 0, 0) != Cell.NETHERRACK) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (cells.at(dx, -1, dz) != Cell.GOLD) {
                    return false;
                }
            }
        }
        for (int[] torch : TORCHES) {
            if (cells.at(torch[0], 0, torch[1]) != Cell.TORCH) {
                return false;
            }
        }
        return true;
    }
}
