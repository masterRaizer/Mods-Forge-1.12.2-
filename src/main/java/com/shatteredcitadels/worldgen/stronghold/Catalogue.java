package com.shatteredcitadels.worldgen.stronghold;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The pieces a layout is grown from, its start, its goal room and its size limits. */
public final class Catalogue {

    public static final Catalogue STRONGHOLD = new Catalogue(PieceKind.CROSSING, PieceKind.PORTAL_ROOM,
            Arrays.<PieceSpec>asList(PieceKind.values()), StrongholdLayout.RADIUS, 60, 90, 20);

    public final PieceSpec start;
    /** Placed once, on the deepest open doorway (portal room, tomb). */
    public final PieceSpec goal;
    public final List<PieceSpec> kinds;
    public final int radius;
    public final int minPieces;
    public final int maxPieces;
    public final int maxDepth;

    public Catalogue(PieceSpec start, PieceSpec goal, List<PieceSpec> kinds, int radius, int minPieces, int maxPieces,
                     int maxDepth) {
        this.start = start;
        this.goal = goal;
        this.kinds = Collections.unmodifiableList(kinds);
        this.radius = radius;
        this.minPieces = minPieces;
        this.maxPieces = maxPieces;
        this.maxDepth = maxDepth;
    }
}
