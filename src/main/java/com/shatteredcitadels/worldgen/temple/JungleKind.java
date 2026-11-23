package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.worldgen.stronghold.Catalogue;
import com.shatteredcitadels.worldgen.stronghold.PieceKind;
import com.shatteredcitadels.worldgen.stronghold.PieceSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rooms of the jungle temple's underground; corridors, stairs and the cistern are the stronghold's.
 * The goal is the idol vault (an antechamber before a sealed vault) or, when the boss is on top, a
 * treasure storeroom.
 */
public enum JungleKind implements PieceSpec {
    ENTRY_HALL(11, 11, 7, "FLR", 0, 1, 0),
    SPIDER_DEN(9, 9, 6, "", 3, 2, 1),
    STOREROOM(7, 7, 6, "", 4, 3, 0),
    PIT_HALL(13, 7, 6, "F", 3, 2, 1),
    GOAL(15, 11, 8, "", 0, 1, 3);

    /** The pit hall's pit sinks this far below its floor (its box includes it). */
    public static final int PIT_DEPTH = 3;
    /** Length of the vault's antechamber: the vault wall stands at this depth into the goal room. */
    public static final int ANTECHAMBER = 5;

    public static final Catalogue CATALOGUE;

    static {
        List<PieceSpec> kinds = new ArrayList<>(Arrays.<PieceSpec>asList(PieceKind.CORRIDOR, PieceKind.TURN, PieceKind.T_JUNCTION,
                PieceKind.STAIRS_DOWN, PieceKind.CISTERN));
        kinds.addAll(Arrays.asList(values()));
        CATALOGUE = new Catalogue(ENTRY_HALL, GOAL, kinds, 30, 12, 20, 8);
    }

    private final int length;
    private final int width;
    private final int height;
    private final String exits;
    private final int weight;
    private final int limit;
    private final int minDepth;

    JungleKind(int length, int width, int height, String exits, int weight, int limit, int minDepth) {
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
    @Override public int sink() { return this == PIT_HALL ? PIT_DEPTH : 0; }
}
