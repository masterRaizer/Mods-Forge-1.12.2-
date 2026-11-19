package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.CitadelArchetype;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/** Immutable layout decisions for one citadel, made before any block is written. */
public final class CitadelPlan {

    public static final int FLOOR_HEIGHT = 8;
    public static final int CRYPT_LEVEL_HEIGHT = 5;

    public final int minX;
    public final int minZ;
    /** Square footprint, always odd so the citadel has a centre column. */
    public final int width;
    /** Y of the ground-floor slab. */
    public final int baseY;
    public final int floors;
    /** Interior height of the boss arena on the top floor (12-14). */
    public final int arenaHeight;
    public final boolean sky;
    public final CitadelArchetype archetype;
    /** Highest Y the spire reaches (Citadel Above the Clouds), or roof + battlements. */
    public final int spireTop;
    /** Number of crypt levels below the citadel, 0 = none. */
    public final int cryptLevels;
    public final long buildSeed;

    public CitadelPlan(int minX, int minZ, int width, int baseY, int floors, int arenaHeight, boolean sky,
                       CitadelArchetype archetype, int spireTop, int cryptLevels, long buildSeed) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.baseY = baseY;
        this.floors = floors;
        this.arenaHeight = arenaHeight;
        this.sky = sky;
        this.archetype = archetype;
        this.spireTop = spireTop;
        this.cryptLevels = cryptLevels;
        this.buildSeed = buildSeed;
    }

    public int maxX() { return minX + width - 1; }
    public int maxZ() { return minZ + width - 1; }
    public int centerX() { return minX + width / 2; }
    public int centerZ() { return minZ + width / 2; }

    /** @return Y of the slab of floor {@code index} (0-based). */
    public int floorY(int index) {
        return baseY + index * FLOOR_HEIGHT;
    }

    public int topFloorIndex() {
        return floors - 1;
    }

    /** @return Y of the roof slab above the boss arena. */
    public int roofY() {
        return floorY(topFloorIndex()) + arenaHeight;
    }

    /** @return Y of the floor of crypt level {@code level} (0 = shallowest). */
    public int cryptLevelY(int level) {
        return baseY - 10 - level * CRYPT_LEVEL_HEIGHT;
    }

    public int lowestY() {
        return cryptLevels > 0 ? cryptLevelY(cryptLevels - 1) : baseY - 1 - CitadelBuilder.FOUNDATION_MAX_DEPTH;
    }

    public StructureBoundingBox totalBounds() {
        return new StructureBoundingBox(minX, Math.max(BlockWriter.MIN_Y, lowestY()), minZ,
                maxX(), Math.min(BlockWriter.MAX_Y, Math.max(spireTop, roofY() + 3)), maxZ());
    }
}
