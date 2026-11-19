package com.shatteredcitadels.structure;

import net.minecraft.nbt.NBTTagCompound;

/**
 * One floor of a citadel (index &gt;= 0) or one crypt level (index -1, -2, -3...): its Y span,
 * difficulty and live combat state. The combat fields are driven by the wave system (stage 4);
 * the floor tracker syncs them to players. Server thread only.
 */
public final class FloorData {

    public static final int MIN_DIFFICULTY = 1;
    public static final int MAX_DIFFICULTY = 6;

    private final int index;
    private final int minY;
    private final int maxY;
    private final int difficultyTier;
    private final boolean bossFloor;
    private int totalWaves;
    private int activeWave;
    private int remainingMobs;
    private boolean cleared;
    private boolean bossDefeated;

    public FloorData(int index, int minY, int maxY, int difficultyTier, int totalWaves, boolean bossFloor) {
        this.index = index;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.difficultyTier = clampDifficulty(difficultyTier);
        this.totalWaves = Math.max(0, totalWaves);
        this.bossFloor = bossFloor;
    }

    public static int clampDifficulty(int tier) {
        return tier < MIN_DIFFICULTY ? MIN_DIFFICULTY : (tier > MAX_DIFFICULTY ? MAX_DIFFICULTY : tier);
    }

    public int getIndex() { return index; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getDifficultyTier() { return difficultyTier; }
    public boolean isBossFloor() { return bossFloor; }
    public boolean isUndergroundLevel() { return index < 0; }
    public int getTotalWaves() { return totalWaves; }
    public int getActiveWave() { return activeWave; }
    public int getRemainingMobs() { return remainingMobs; }
    public boolean isCleared() { return cleared; }
    public boolean isBossDefeated() { return bossDefeated; }

    public boolean containsY(int y) {
        return y >= minY && y <= maxY;
    }

    // ------------------------------------------------------------------ combat state (stage 4)

    public void setTotalWaves(int totalWaves) {
        this.totalWaves = Math.max(0, totalWaves);
    }

    /** @param activeWave 0 = not started, 1..totalWaves = running wave */
    public void setActiveWave(int activeWave) {
        this.activeWave = Math.max(0, activeWave);
    }

    public void setRemainingMobs(int remainingMobs) {
        this.remainingMobs = Math.max(0, remainingMobs);
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    public void setBossDefeated(boolean bossDefeated) {
        this.bossDefeated = bossDefeated;
    }

    // ------------------------------------------------------------------ NBT

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("index", index);
        tag.setInteger("minY", minY);
        tag.setInteger("maxY", maxY);
        tag.setInteger("tier", difficultyTier);
        tag.setBoolean("boss", bossFloor);
        tag.setInteger("totalWaves", totalWaves);
        tag.setInteger("activeWave", activeWave);
        tag.setInteger("remaining", remainingMobs);
        tag.setBoolean("cleared", cleared);
        tag.setBoolean("bossDefeated", bossDefeated);
        return tag;
    }

    public static FloorData readFromNBT(NBTTagCompound tag) {
        FloorData floor = new FloorData(tag.getInteger("index"), tag.getInteger("minY"), tag.getInteger("maxY"),
                tag.getInteger("tier"), tag.getInteger("totalWaves"), tag.getBoolean("boss"));
        floor.activeWave = Math.max(0, tag.getInteger("activeWave"));
        floor.remainingMobs = Math.max(0, tag.getInteger("remaining"));
        floor.cleared = tag.getBoolean("cleared");
        floor.bossDefeated = tag.getBoolean("bossDefeated");
        return floor;
    }
}
