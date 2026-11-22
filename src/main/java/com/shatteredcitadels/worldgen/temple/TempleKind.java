package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.worldgen.stronghold.Catalogue;
import com.shatteredcitadels.worldgen.stronghold.PieceKind;
import com.shatteredcitadels.worldgen.stronghold.PieceSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Rooms of the desert temple's underground; corridors and stairs are the stronghold's. */
public enum TempleKind implements PieceSpec {
    ENTRY_HALL(11, 11, 7, "FLR", 0, 1, 0),
    BURIAL_CHAMBER(11, 9, 6, "F", 5, 4, 1),
    STOREROOM(7, 7, 6, "", 4, 3, 0),
    SCROLL_LIBRARY(9, 9, 7, "", 3, 2, 1),
    PILLARED_HALL(13, 11, 8, "LR", 3, 2, 0),
    TREASURY(9, 7, 6, "", 2, 1, 2),
    TOMB(15, 11, 9, "", 0, 1, 3);

    public static final Catalogue CATALOGUE;

    static {
        List<PieceSpec> kinds = new ArrayList<>(Arrays.<PieceSpec>asList(PieceKind.CORRIDOR, PieceKind.TURN, PieceKind.T_JUNCTION,
                PieceKind.STAIRS_DOWN));
        kinds.addAll(Arrays.asList(values()));
        CATALOGUE = new Catalogue(ENTRY_HALL, TOMB, kinds, 40, 25, 40, 12);
    }

    private final int length;
    private final int width;
    private final int height;
    private final String exits;
    private final int weight;
    private final int limit;
    private final int minDepth;

    TempleKind(int length, int width, int height, String exits, int weight, int limit, int minDepth) {
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
}
