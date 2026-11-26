package com.shatteredcitadels.herobrine;

import java.util.Random;

/**
 * Where lairs stand. The overworld is cut into square regions of {@code spacing} chunks; each region
 * has one candidate chunk, decided by the world seed alone, and a seeded roll that leaves some
 * regions empty. Nothing is searched, so nothing can cascade.
 */
public final class LairPlacement {

    private static final long SALT = 0x4E0B21E5L;
    /** Candidates keep this far from the region edge, so two lairs are never neighbours. */
    static final int MAX_MARGIN = 8;

    /** The candidate of a region. */
    public static final class Site {
        public final int chunkX;
        public final int chunkZ;
        /** False where the seeded roll left the region empty. */
        public final boolean present;
        public final LairVariant variant;
        public final long seed;

        Site(int chunkX, int chunkZ, boolean present, LairVariant variant, long seed) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.present = present;
            this.variant = variant;
            this.seed = seed;
        }
    }

    private LairPlacement() {
    }

    /** The finishing step of SplitMix64: every bit of the input reaches every bit of the result. */
    static long scramble(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static int region(int chunk, int spacing) {
        return Math.floorDiv(chunk, Math.max(1, spacing));
    }

    /** @return the candidate of the region; regions next to each other get unrelated answers. */
    public static Site siteOf(long worldSeed, int regionX, int regionZ, int spacing, double chance) {
        int size = Math.max(1, spacing);
        // Seeds of neighbouring regions differ in a bit or two; java.util.Random answers such seeds
        // alike, so the seed is scrambled first.
        long key = ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
        Random random = new Random(scramble(worldSeed ^ scramble(key ^ SALT)));
        int margin = Math.min(MAX_MARGIN, size / 4);
        int span = size - 2 * margin;
        int chunkX = regionX * size + margin + random.nextInt(span);
        int chunkZ = regionZ * size + margin + random.nextInt(span);
        boolean present = random.nextDouble() < chance;
        LairVariant variant = LairVariant.values()[random.nextInt(LairVariant.values().length)];
        return new Site(chunkX, chunkZ, present, variant, random.nextLong());
    }

    /** @return the lair site of exactly this chunk, or null: most chunks have none. */
    public static Site siteAt(long worldSeed, int chunkX, int chunkZ, int spacing, double chance) {
        Site site = siteOf(worldSeed, region(chunkX, spacing), region(chunkZ, spacing), spacing, chance);
        return site.present && site.chunkX == chunkX && site.chunkZ == chunkZ ? site : null;
    }

    /** @return true if the chunk lies within {@code clearance} chunks of the spawn chunk. */
    public static boolean isNearSpawn(int chunkX, int chunkZ, int spawnChunkX, int spawnChunkZ, int clearance) {
        return Math.abs(chunkX - spawnChunkX) <= clearance && Math.abs(chunkZ - spawnChunkZ) <= clearance;
    }

    /** @return true if a lair may stand on ground of this height. */
    public static boolean isGroundUsable(int groundY) {
        return groundY >= 50 && groundY <= 180;
    }
}
