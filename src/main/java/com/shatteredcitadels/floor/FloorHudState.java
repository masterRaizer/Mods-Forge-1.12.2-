package com.shatteredcitadels.floor;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.FloorData;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of what a player's floor HUD shows. Used as the server-side change cache
 * (equals/hashCode) and as the payload of {@link com.shatteredcitadels.network.PacketSyncFloorHud}.
 * Side-neutral: contains no client classes.
 */
public final class FloorHudState {

    /** Maximum name length accepted on the wire. */
    public static final int MAX_NAME_LENGTH = 128;

    public static final FloorHudState NONE = new FloorHudState(false, new UUID(0L, 0L), "", "", false, false,
            0, 0, 0, 1, 0, 0, 0, false, false, false);

    private final boolean inside;
    private final UUID citadelId;
    private final String citadelName;
    private final String archetypeKey;
    private final boolean skyCitadel;
    private final boolean undergroundOnly;
    private final int floorIndex;
    private final int surfaceFloors;
    private final int undergroundLevels;
    private final int difficultyTier;
    private final int activeWave;
    private final int totalWaves;
    private final int remainingMobs;
    private final boolean cleared;
    private final boolean bossFloor;
    private final boolean bossDefeated;
    /** The structure is a walled castle: floor 0 is its courtyard, keep floors are numbered from 1. */
    private final boolean hasCourtyard;
    /** Named zone (manor), shown instead of a floor number; empty otherwise. */
    private final String zoneKey;

    public FloorHudState(boolean inside, UUID citadelId, String citadelName, String archetypeKey, boolean skyCitadel,
                         boolean undergroundOnly, int floorIndex, int surfaceFloors, int undergroundLevels,
                         int difficultyTier, int activeWave, int totalWaves, int remainingMobs, boolean cleared,
                         boolean bossFloor, boolean bossDefeated) {
        this(inside, citadelId, citadelName, archetypeKey, skyCitadel, undergroundOnly, floorIndex, surfaceFloors,
                undergroundLevels, difficultyTier, activeWave, totalWaves, remainingMobs, cleared, bossFloor, bossDefeated,
                false, "");
    }

    public FloorHudState(boolean inside, UUID citadelId, String citadelName, String archetypeKey, boolean skyCitadel,
                         boolean undergroundOnly, int floorIndex, int surfaceFloors, int undergroundLevels,
                         int difficultyTier, int activeWave, int totalWaves, int remainingMobs, boolean cleared,
                         boolean bossFloor, boolean bossDefeated, boolean hasCourtyard, String zoneKey) {
        this.inside = inside;
        this.citadelId = citadelId == null ? new UUID(0L, 0L) : citadelId;
        this.citadelName = clampName(citadelName);
        this.archetypeKey = clampName(archetypeKey);
        this.skyCitadel = skyCitadel;
        this.undergroundOnly = undergroundOnly;
        this.floorIndex = floorIndex;
        this.surfaceFloors = Math.max(0, surfaceFloors);
        this.undergroundLevels = Math.max(0, undergroundLevels);
        this.difficultyTier = FloorData.clampDifficulty(difficultyTier);
        this.activeWave = Math.max(0, activeWave);
        this.totalWaves = Math.max(0, totalWaves);
        this.remainingMobs = Math.max(0, remainingMobs);
        this.cleared = cleared;
        this.bossFloor = bossFloor;
        this.bossDefeated = bossDefeated;
        this.hasCourtyard = hasCourtyard;
        this.zoneKey = clampName(zoneKey);
    }

    /** @return the HUD state for a player standing on {@code floor} of {@code citadel}. */
    public static FloorHudState of(CitadelInstance citadel, FloorData floor) {
        if (citadel == null || floor == null) {
            return NONE;
        }
        return new FloorHudState(true, citadel.getCitadelId(), citadel.getDisplayName(), citadel.getArchetypeKey(),
                citadel.isSkyCitadel(), citadel.isUnderground(), floor.getIndex(), citadel.getSurfaceFloorCount(),
                citadel.getUndergroundLevelCount(), floor.getDifficultyTier(), floor.getActiveWave(),
                floor.getTotalWaves(), floor.getRemainingMobs(), floor.isCleared(), floor.isBossFloor(),
                floor.isBossDefeated(), citadel.hasCourtyard(), floor.getZoneKey());
    }

    private static String clampName(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value;
    }

    // ------------------------------------------------------------------ wire format

    public void write(ByteBuf buf) {
        buf.writeBoolean(inside);
        if (!inside) {
            return;
        }
        buf.writeLong(citadelId.getMostSignificantBits());
        buf.writeLong(citadelId.getLeastSignificantBits());
        ByteBufUtils.writeUTF8String(buf, citadelName);
        ByteBufUtils.writeUTF8String(buf, archetypeKey);
        int flags = (skyCitadel ? 1 : 0) | (undergroundOnly ? 2 : 0) | (cleared ? 4 : 0) | (bossFloor ? 8 : 0)
                | (bossDefeated ? 16 : 0) | (hasCourtyard ? 32 : 0);
        buf.writeByte(flags);
        buf.writeShort(floorIndex);
        buf.writeByte(surfaceFloors);
        buf.writeByte(undergroundLevels);
        buf.writeByte(difficultyTier);
        buf.writeShort(activeWave);
        buf.writeShort(totalWaves);
        buf.writeInt(remainingMobs);
        ByteBufUtils.writeUTF8String(buf, zoneKey);
    }

    public static FloorHudState read(ByteBuf buf) {
        if (!buf.readBoolean()) {
            return NONE;
        }
        UUID id = new UUID(buf.readLong(), buf.readLong());
        String name = ByteBufUtils.readUTF8String(buf);
        String archetype = ByteBufUtils.readUTF8String(buf);
        int flags = buf.readUnsignedByte();
        int floorIndex = buf.readShort();
        int surface = buf.readUnsignedByte();
        int underground = buf.readUnsignedByte();
        int tier = buf.readUnsignedByte();
        int activeWave = buf.readUnsignedShort();
        int totalWaves = buf.readUnsignedShort();
        int remaining = buf.readInt();
        String zone = ByteBufUtils.readUTF8String(buf);
        return new FloorHudState(true, id, name, archetype, (flags & 1) != 0, (flags & 2) != 0, floorIndex, surface,
                underground, tier, activeWave, totalWaves, remaining, (flags & 4) != 0, (flags & 8) != 0,
                (flags & 16) != 0, (flags & 32) != 0, zone);
    }

    // ------------------------------------------------------------------ accessors

    public boolean isInside() { return inside; }
    public UUID getCitadelId() { return citadelId; }
    public String getCitadelName() { return citadelName; }
    public String getArchetypeKey() { return archetypeKey; }
    public boolean isSkyCitadel() { return skyCitadel; }
    public boolean isUndergroundOnly() { return undergroundOnly; }
    public int getFloorIndex() { return floorIndex; }
    public int getSurfaceFloors() { return surfaceFloors; }
    public int getUndergroundLevels() { return undergroundLevels; }
    public int getDifficultyTier() { return difficultyTier; }
    public int getActiveWave() { return activeWave; }
    public int getTotalWaves() { return totalWaves; }
    public int getRemainingMobs() { return remainingMobs; }
    public boolean isCleared() { return cleared; }
    public boolean isBossFloor() { return bossFloor; }
    public boolean isBossDefeated() { return bossDefeated; }
    public boolean hasCourtyard() { return hasCourtyard; }
    public String getZoneKey() { return zoneKey; }

    /** @return true if both states are on the same floor of the same structure. */
    public boolean sameFloorAs(FloorHudState other) {
        return other != null && inside && other.inside && citadelId.equals(other.citadelId) && floorIndex == other.floorIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FloorHudState)) {
            return false;
        }
        FloorHudState s = (FloorHudState) o;
        if (!inside || !s.inside) {
            return inside == s.inside;
        }
        return skyCitadel == s.skyCitadel && undergroundOnly == s.undergroundOnly && floorIndex == s.floorIndex
                && surfaceFloors == s.surfaceFloors && undergroundLevels == s.undergroundLevels
                && difficultyTier == s.difficultyTier && activeWave == s.activeWave && totalWaves == s.totalWaves
                && remainingMobs == s.remainingMobs && cleared == s.cleared && bossFloor == s.bossFloor
                && bossDefeated == s.bossDefeated && hasCourtyard == s.hasCourtyard && zoneKey.equals(s.zoneKey)
                && citadelId.equals(s.citadelId)
                && citadelName.equals(s.citadelName) && archetypeKey.equals(s.archetypeKey);
    }

    @Override
    public int hashCode() {
        if (!inside) {
            return 0;
        }
        return Objects.hash(citadelId, citadelName, archetypeKey, skyCitadel, undergroundOnly, floorIndex, surfaceFloors,
                undergroundLevels, difficultyTier, activeWave, totalWaves, remainingMobs, cleared, bossFloor, bossDefeated,
                hasCourtyard, zoneKey);
    }
}
