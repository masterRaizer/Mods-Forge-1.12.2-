package com.shatteredcitadels.structure;

/**
 * Semantic tag of a room. Wave composition (stage 4) uses it to pick fitting mobs:
 * goblin-type mobs for {@link #GOBLIN_DEN}, cave/underground mobs for {@link #UNDERGROUND_LAIR}.
 */
public enum RoomTag {
    ENTRANCE_HALL,
    GUARD_ROOM,
    LIBRARY,
    BARRACKS,
    TREASURY,
    BOSS_ARENA,
    GOBLIN_DEN,
    UNDERGROUND_LAIR,
    OSSUARY,
    TRAP_CORRIDOR,
    PANTRY,
    BEDROOM,
    SMELTERY;

    public static RoomTag byName(String name) {
        for (RoomTag tag : values()) {
            if (tag.name().equals(name)) {
                return tag;
            }
        }
        return GUARD_ROOM;
    }
}
