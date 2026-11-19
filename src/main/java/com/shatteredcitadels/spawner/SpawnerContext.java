package com.shatteredcitadels.spawner;

import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.structure.RoomTag;

import java.util.UUID;

/** Immutable description of a spawner request handed to {@link IWaveSpawnerPlacer}. */
public final class SpawnerContext {

    private final SpawnerSource source;
    private final UUID structureId;
    private final int floorIndex;
    private final int tier;
    private final RoomTag roomTag;

    public SpawnerContext(SpawnerSource source, UUID structureId, int floorIndex, int tier, RoomTag roomTag) {
        this.source = source;
        this.structureId = structureId;
        this.floorIndex = floorIndex;
        this.tier = MobTiers.clamp(tier);
        this.roomTag = roomTag == null ? RoomTag.GUARD_ROOM : roomTag;
    }

    public SpawnerSource getSource() { return source; }
    /** @return ID of the owning structure, or null if it is not tracked. */
    public UUID getStructureId() { return structureId; }
    public int getFloorIndex() { return floorIndex; }
    public int getTier() { return tier; }
    public RoomTag getRoomTag() { return roomTag; }
}
