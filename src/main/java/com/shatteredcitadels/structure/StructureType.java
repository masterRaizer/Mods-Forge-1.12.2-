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
    CRYPT;

    public static StructureType byName(String name) {
        for (StructureType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return CITADEL;
    }
}
