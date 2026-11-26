package com.shatteredcitadels.worldgen.dims;

import java.util.Random;

/** Pure site rules of the other dimensions: the section grid, its candidate chunks, the outer End. */
public final class DimSites {

    /** Candidate chunks per section: a cavern or an island is often missing at the first one. */
    public static final int CANDIDATES = 3;
    private static final long SALT = 0x44D1A5C0FFEE1207L;

    private DimSites() {
    }

    /** Same mixer as the bridges use: neighbouring seeds must not give neighbouring random streams. */
    public static long scramble(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** @return the section size in chunks: the overworld size times the spacing, never smaller than the overworld size */
    public static int sectionSize(int overworldSection, double spacing) {
        return Math.max(overworldSection, (int) Math.round(overworldSection * Math.max(1.0D, spacing)));
    }

    public static long sectionSeed(long worldSeed, DimKind kind, int sx, int sz) {
        return scramble(scramble(worldSeed ^ SALT) + scramble(sx * 341873128712L + kind.ordinal()) * 31L
                + scramble(sz * 132897987541L));
    }

    /** @return {@link #CANDIDATES} chunk positions {x, z} of the section, the same for every caller */
    public static int[][] candidates(long worldSeed, DimKind kind, int sx, int sz, int section) {
        Random random = new Random(sectionSeed(worldSeed, kind, sx, sz));
        int margin = Math.min(6, section / 4);
        int span = Math.max(1, section - 2 * margin);
        int[][] sites = new int[CANDIDATES][];
        for (int i = 0; i < CANDIDATES; i++) {
            sites[i] = new int[]{sx * section + margin + random.nextInt(span), sz * section + margin + random.nextInt(span)};
        }
        return sites;
    }

    /** @return the index of the chunk among the candidates of its section, or -1 */
    public static int candidateIndex(long worldSeed, DimKind kind, int chunkX, int chunkZ, int section) {
        int[][] sites = candidates(worldSeed, kind, Math.floorDiv(chunkX, section), Math.floorDiv(chunkZ, section), section);
        for (int i = 0; i < sites.length; i++) {
            if (sites[i][0] == chunkX && sites[i][1] == chunkZ) {
                return i;
            }
        }
        return -1;
    }

    /** Build seed of a candidate chunk. */
    public static long buildSeed(long worldSeed, DimKind kind, int chunkX, int chunkZ) {
        return scramble(sectionSeed(worldSeed, kind, chunkX, chunkZ) ^ 0x51ED270B27L);
    }

    /**
     * The outer End: the whole box (block coordinates) lies farther than {@code minDistance} from
     * 0,0, so nothing reaches the central island, the exit portal or the ring of gateways.
     */
    public static boolean isOuterEnd(int minX, int minZ, int maxX, int maxZ, int minDistance) {
        long nearX = minX <= 0 && maxX >= 0 ? 0 : Math.min(Math.abs((long) minX), Math.abs((long) maxX));
        long nearZ = minZ <= 0 && maxZ >= 0 ? 0 : Math.min(Math.abs((long) minZ), Math.abs((long) maxZ));
        return nearX * nearX + nearZ * nearZ > (long) minDistance * minDistance;
    }
}
