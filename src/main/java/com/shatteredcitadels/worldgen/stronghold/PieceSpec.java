package com.shatteredcitadels.worldgen.stronghold;

/**
 * A kind of piece for {@link StrongholdLayout}: a box laid out in its own frame ({@code length}
 * along the direction of travel, {@code width} across, {@code height} with floor and ceiling),
 * exits F (ahead), L/R (sides), T (one side at random), weight, limit and minimum depth.
 */
public interface PieceSpec {

    /** 0 = random 5..14 (corridors). */
    int length();

    int width();

    int height();

    String exits();

    int weight();

    int limit();

    int minDepth();

    default boolean hasExits() {
        return !exits().isEmpty();
    }

    /** Floor change from entry to the far doorway (stairs). */
    default int rise() {
        return 0;
    }

    /** Blocks the piece reaches below its floor (a sunken pool). */
    default int sink() {
        return 0;
    }

    /** Corridors are retried shorter when they do not fit. */
    default boolean isCorridor() {
        return false;
    }

    /** A turn puts its side exit in the middle of its length. */
    default boolean isTurn() {
        return false;
    }
}
