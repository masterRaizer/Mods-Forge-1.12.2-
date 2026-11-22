package com.shatteredcitadels.worldgen.stronghold;

/**
 * Kinds of stronghold pieces. Each is a box laid out in its own frame: {@code length} along the
 * direction of travel from its entry doorway, {@code width} across, {@code height} from floor to
 * ceiling (all including walls). Exits are F (ahead), L/R (both sides, a single side picked at
 * random for a turn), or none.
 */
public enum PieceKind implements PieceSpec {
    CROSSING(11, 11, 7, "FLR", 6, 6, 1),
    CORRIDOR(0, 5, 6, "F", 30, 999, 0),
    TURN(5, 5, 6, "T", 10, 999, 0),
    T_JUNCTION(5, 5, 6, "LR", 18, 999, 0),
    STAIRS_UP(9, 5, 12, "F", 4, 999, 0),
    STAIRS_DOWN(9, 5, 12, "F", 4, 999, 0),
    PORTAL_ROOM(16, 11, 10, "", 0, 1, 4),
    GRAND_LIBRARY(19, 15, 12, "F", 3, 1, 2),
    SMALL_LIBRARY(9, 9, 7, "", 4, 3, 0),
    PRISON(13, 11, 6, "F", 4, 2, 0),
    ARMOURY(9, 9, 6, "", 3, 2, 0),
    TREASURE(7, 7, 6, "", 2, 1, 3),
    MESS_HALL(13, 9, 7, "LR", 3, 2, 0),
    STOREROOM(7, 7, 6, "", 5, 4, 0),
    QUARTERS(9, 7, 6, "", 5, 4, 0),
    CISTERN(13, 13, 8, "FLR", 2, 2, 0),
    PILLARED_HALL(15, 11, 8, "FLR", 3, 2, 0),
    CRYPT(11, 9, 6, "F", 3, 2, 0);

    /** 0 = random 5..14 (corridors). */
    public final int length;
    public final int width;
    public final int height;
    public final String exits;
    public final int weight;
    public final int limit;
    public final int minDepth;

    PieceKind(int length, int width, int height, String exits, int weight, int limit, int minDepth) {
        this.length = length;
        this.width = width;
        this.height = height;
        this.exits = exits;
        this.weight = weight;
        this.limit = limit;
        this.minDepth = minDepth;
    }

    @Override public int length() { return length; }
    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public String exits() { return exits; }
    @Override public int weight() { return weight; }
    @Override public int limit() { return limit; }
    @Override public int minDepth() { return minDepth; }
    @Override public int rise() { return this == STAIRS_UP ? StrongholdLayout.STAIR_RISE : this == STAIRS_DOWN ? -StrongholdLayout.STAIR_RISE : 0; }
    @Override public int sink() { return this == CISTERN ? 2 : 0; }
    @Override public boolean isCorridor() { return this == CORRIDOR; }
    @Override public boolean isTurn() { return this == TURN; }

    public boolean isCorridorLike() {
        return this == CORRIDOR || this == TURN || this == T_JUNCTION || this == STAIRS_UP || this == STAIRS_DOWN
                || this == CROSSING;
    }

    @Override
    public boolean hasExits() {
        return !exits.isEmpty();
    }
}
