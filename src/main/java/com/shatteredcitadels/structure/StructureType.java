package com.shatteredcitadels.structure;

/** Kind of tracked structure. Stored by name in NBT, so constants must never be renamed. */
public enum StructureType {
    /** Regular multi-floor citadel (3-9 floors, towers up to the build limit). */
    CITADEL,
    /** The Citadel Above the Clouds (10-14 floors by default). */
    SKY_CITADEL,
    /** A castle built by the Castle Dungeons mod and adopted by the floor tracker. */
    CASTLE_DUNGEONS,
    /** Standalone underground crypt reached from a surface entrance. */
    CRYPT,
    /** Curtain wall, moat and courtyard (floor 0) around a keep (floors 1..n). */
    WALLED_CASTLE,
    /** Gothic manor: named zones (floor indices) cleared in route order, boss in the throne room. */
    MANOR,
    /** The End stronghold that replaces the vanilla one: explorative, no floors, no protection. */
    STRONGHOLD,
    /** Desert temple: a pyramid over an underground temple; its tomb may hold a pharaoh boss. */
    DESERT_TEMPLE,
    /** Jungle temple: stepped temple over an underground; an idol vault or a boss shrine on top. */
    JUNGLE_TEMPLE,
    /** A bridge over a river: a landmark with no floors, no boss and no protection. */
    BRIDGE,
    /** A lair of Herobrine: plain blocks, mineable, with the summoning altar inside. */
    HEROBRINE_LAIR;

    public static StructureType byName(String name) {
        for (StructureType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return CITADEL;
    }
}
