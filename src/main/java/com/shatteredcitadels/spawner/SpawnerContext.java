package com.shatteredcitadels.spawner;

import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.structure.RoomTag;

/** Immutable description of a spawner request handed to {@link IWaveSpawnerPlacer}. */
public final class SpawnerContext {

    private final SpawnerSource source;
    private final long structureId;
    private final int floorIndex;
    private final int tier;
    private final RoomTag roomTag;

    public SpawnerContext(SpawnerSource source, long structureId, int floorIndex, int tier, RoomTag roomTag) {
        this.source = source;
        this.structureId = structureId;
        this.floorIndex = floorIndex;
        this.tier = MobTiers.clamp(tier);
        this.roomTag = roomTag == null ? RoomTag.GUARD_ROOM : roomTag;
    }

    public SpawnerSource getSource() { return source; }
    /** @return tracker ID of the owning structure, or 0 if it is not tracked. */
    public long getStructureId() { return structureId; }
    public int getFloorIndex() { return floorIndex; }
    public int getTier() { return tier; }
    public RoomTag getRoomTag() { return roomTag; }
}
