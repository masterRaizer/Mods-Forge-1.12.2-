package com.shatteredcitadels.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

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
    /** Optional horizontal extent {minX, minZ, maxX, maxZ}; null = the whole structure at this height. */
    private int[] area;
    /** The courtyard of a walled castle (floor 0, shown as "Courtyard"). */
    private boolean courtyard;
    /** Named zone of a manor ("great_hall", ...); empty for numbered floors. */
    private String zoneKey = "";
    /** Floor that must be cleared before this one's spawners wake up; MIN_VALUE = none. */
    private int requiresFloor = Integer.MIN_VALUE;

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

    /** @return true if the position lies in this floor's height span and horizontal area (if any). */
    public boolean contains(BlockPos pos) {
        return containsY(pos.getY()) && (area == null || (pos.getX() >= area[0] && pos.getX() <= area[2]
                && pos.getZ() >= area[1] && pos.getZ() <= area[3]));
    }

    /** @return horizontal area in blocks; floors without an area count as unbounded. */
    public long areaSize() {
        return area == null ? Long.MAX_VALUE : (long) (area[2] - area[0] + 1) * (area[3] - area[1] + 1);
    }

    public boolean hasArea() { return area != null; }

    public FloorData setArea(int minX, int minZ, int maxX, int maxZ) {
        this.area = new int[]{Math.min(minX, maxX), Math.min(minZ, maxZ), Math.max(minX, maxX), Math.max(minZ, maxZ)};
        return this;
    }

    public boolean isCourtyard() { return courtyard; }

    public String getZoneKey() { return zoneKey; }

    public FloorData setZoneKey(String zoneKey) {
        this.zoneKey = zoneKey == null ? "" : zoneKey;
        return this;
    }

    public int getRequiresFloor() { return requiresFloor; }

    public FloorData setRequiresFloor(int requiresFloor) {
        this.requiresFloor = requiresFloor;
        return this;
    }

    public FloorData setCourtyard(boolean courtyard) {
        this.courtyard = courtyard;
        return this;
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
        if (area != null) {
            tag.setIntArray("area", area.clone());
        }
        tag.setBoolean("courtyard", courtyard);
        if (!zoneKey.isEmpty()) {
            tag.setString("zone", zoneKey);
        }
        if (requiresFloor != Integer.MIN_VALUE) {
            tag.setInteger("requires", requiresFloor);
        }
        return tag;
    }

    public static FloorData readFromNBT(NBTTagCompound tag) {
        FloorData floor = new FloorData(tag.getInteger("index"), tag.getInteger("minY"), tag.getInteger("maxY"),
                tag.getInteger("tier"), tag.getInteger("totalWaves"), tag.getBoolean("boss"));
        floor.activeWave = Math.max(0, tag.getInteger("activeWave"));
        floor.remainingMobs = Math.max(0, tag.getInteger("remaining"));
        floor.cleared = tag.getBoolean("cleared");
        floor.bossDefeated = tag.getBoolean("bossDefeated");
        int[] area = tag.getIntArray("area");
        if (area.length == 4) {
            floor.area = area;
        }
        floor.courtyard = tag.getBoolean("courtyard");
        floor.zoneKey = tag.getString("zone");
        floor.requiresFloor = tag.hasKey("requires") ? tag.getInteger("requires") : Integer.MIN_VALUE;
        return floor;
    }
}
