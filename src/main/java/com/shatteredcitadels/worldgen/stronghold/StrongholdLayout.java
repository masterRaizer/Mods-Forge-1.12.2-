package com.shatteredcitadels.worldgen.stronghold;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plan of a stronghold (pure logic): a central crossing, then pieces attached doorway to doorway,
 * breadth first, each rotated so its entry faces the doorway it grows from, kept only when it
 * overlaps nothing and stays within {@link #RADIUS} of the centre. The portal room goes on the
 * deepest free doorway once the stronghold has its size.
 */
public final class StrongholdLayout {

    public static final int RADIUS = 70;
    public static final int MIN_FLOOR_Y = 12;
    public static final int MAX_DEPTH = 20;
    public static final int STAIR_RISE = 6;
    private static final int LEVEL_SPAN = 12;
    private static final int ATTEMPTS = 12;

    /** An opening in a piece's wall: {@code pos} is the wall block at walk level in the middle of it. */
    public static final class Doorway {
        public final BlockPos pos;
        public final EnumFacing out;
        public final int width = 3;
        public boolean used;

        Doorway(BlockPos pos, EnumFacing out) {
            this.pos = pos;
            this.out = out;
        }
    }

    public static final class Piece {
        public final PieceSpec kind;
        public final StructureBoundingBox box;
        /** Direction of travel from the entry doorway (for the start: NORTH). */
        public final EnumFacing facing;
        public final int depth;
        /** Floor (the floor block's y) at the entry; stairs end {@link #rise} higher or lower. */
        public final int floorY;
        public final int rise;
        public final int length;
        public final List<Doorway> doors = new ArrayList<>();

        Piece(PieceSpec kind, StructureBoundingBox box, EnumFacing facing, int depth, int floorY, int rise, int length) {
            this.kind = kind;
            this.box = box;
            this.facing = facing;
            this.depth = depth;
            this.floorY = floorY;
            this.rise = rise;
            this.length = length;
        }
    }

    private final int centreX;
    private final int centreZ;
    private final int baseFloor;
    private final Random rand;
    private final List<Piece> pieces = new ArrayList<>();
    private final Map<PieceSpec, Integer> counts = new java.util.HashMap<>();
    private final Catalogue catalogue;
    private Piece start;
    private Piece portal;

    private StrongholdLayout(long seed, int centreX, int floorY, int centreZ, Catalogue catalogue) {
        this.catalogue = catalogue;
        this.rand = new Random(seed);
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.baseFloor = floorY;
    }

    /** @param floorY y of the floor block of the main level (the start crossing). */
    public static StrongholdLayout generate(long seed, int centreX, int floorY, int centreZ) {
        return generate(seed, centreX, floorY, centreZ, Catalogue.STRONGHOLD);
    }

    /** Grows a layout from any catalogue (stronghold, temple underground). */
    public static StrongholdLayout generate(long seed, int centreX, int floorY, int centreZ, Catalogue catalogue) {
        // A growth whose branches all close early is redone with the next seed; the largest layout
        // with a portal room wins if none reaches the minimum size.
        StrongholdLayout best = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            StrongholdLayout layout = new StrongholdLayout(seed * 31L + attempt, centreX, Math.max(MIN_FLOOR_Y, floorY), centreZ,
                    catalogue);
            layout.grow();
            if (!layout.placePortalRoom()) {
                continue;
            }
            if (layout.pieces.size() >= catalogue.minPieces) {
                return layout;
            }
            if (best == null || layout.pieces.size() > best.pieces.size()) {
                best = layout;
            }
        }
        return best;
    }

    public List<Piece> pieces() {
        return Collections.unmodifiableList(pieces);
    }

    public Piece start() {
        return start;
    }

    /** The goal room: the portal room of a stronghold, the tomb of a temple. */
    public Piece goal() {
        return portal;
    }

    public Piece portalRoom() {
        return portal;
    }

    // ------------------------------------------------------------------ growth

    private static final class Open {
        final Piece piece;
        final Doorway door;

        Open(Piece piece, Doorway door) {
            this.piece = piece;
            this.door = door;
        }
    }

    private void grow() {
        PieceSpec startKind = catalogue.start;
        int half = startKind.width() / 2;
        start = new Piece(startKind, new StructureBoundingBox(centreX - half, baseFloor, centreZ - half,
                centreX + half, baseFloor + startKind.height() - 1, centreZ + half), EnumFacing.NORTH, 0, baseFloor, 0,
                startKind.length());
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            start.doors.add(new Doorway(new BlockPos(centreX + side.getXOffset() * half, baseFloor + 1,
                    centreZ + side.getZOffset() * half), side));
        }
        add(start);
        List<Open> queue = new ArrayList<>();
        for (Doorway door : start.doors) {
            queue.add(new Open(start, door));
        }
        int target = catalogue.minPieces + rand.nextInt(catalogue.maxPieces - catalogue.minPieces + 1);
        while (!queue.isEmpty() && pieces.size() < target - 1) {
            Open open = queue.remove(rand.nextInt(Math.min(3, queue.size())));
            Piece placed = attach(open, candidates(open.piece.depth + 1));
            if (placed != null) {
                for (Doorway door : placed.doors) {
                    if (!door.used) {
                        queue.add(new Open(placed, door));
                    }
                }
            }
            // The portal room takes the deepest open doorway once a third of the stronghold stands.
            if (portal == null && pieces.size() >= target / 3) {
                placePortalRoom(queue);
            }
        }
    }

    /** Weighted shuffle of the kinds allowed at {@code depth}. */
    private List<PieceSpec> candidates(int depth) {
        List<PieceSpec> bag = new ArrayList<>();
        for (PieceSpec kind : catalogue.kinds) {
            if (kind.weight() == 0 || counts.getOrDefault(kind, 0) >= kind.limit() || depth < kind.minDepth()) {
                continue;
            }
            if (depth >= catalogue.maxDepth && kind.hasExits()) {
                continue;
            }
            for (int i = 0; i < kind.weight(); i++) {
                bag.add(kind);
            }
        }
        Collections.shuffle(bag, rand);
        List<PieceSpec> order = new ArrayList<>();
        for (PieceSpec kind : bag) {
            if (!order.contains(kind)) {
                order.add(kind);
            }
        }
        return order;
    }

    private Piece attach(Open open, List<PieceSpec> kinds) {
        for (PieceSpec kind : kinds) {
            // A corridor that does not fit is tried shorter before giving up on it.
            int[] lengths = kind.isCorridor() ? new int[]{5 + rand.nextInt(10), 7, 5} : new int[]{0};
            for (int length : lengths) {
                Piece piece = build(kind, open.door, open.piece.depth + 1, length);
                if (piece != null && fits(piece.box)) {
                    open.door.used = true;
                    add(piece);
                    return piece;
                }
            }
        }
        return null;
    }

    /** Fallback after growth: any free doorway deep enough. */
    private boolean placePortalRoom() {
        if (portal != null) {
            return true;
        }
        List<Open> free = new ArrayList<>();
        for (Piece piece : pieces) {
            for (Doorway door : piece.doors) {
                if (!door.used) {
                    free.add(new Open(piece, door));
                }
            }
        }
        return placePortalRoom(free);
    }

    /** The deepest doorway of {@code free} (at least the portal's minimum depth) that takes the portal room. */
    private boolean placePortalRoom(List<Open> free) {
        free.removeIf(o -> o.door.used);
        List<Open> sorted = new ArrayList<>(free);
        sorted.removeIf(o -> o.piece.depth + 1 < catalogue.goal.minDepth());
        sorted.sort(Comparator.comparingInt((Open o) -> -o.piece.depth));
        for (Open open : sorted) {
            Piece room = build(catalogue.goal, open.door, open.piece.depth + 1, 0);
            if (room != null && fits(room.box)) {
                open.door.used = true;
                add(room);
                portal = room;
                free.remove(open);
                return true;
            }
        }
        return false;
    }

    private void add(Piece piece) {
        pieces.add(piece);
        counts.merge(piece.kind, 1, Integer::sum);
    }

    private boolean fits(StructureBoundingBox box) {
        int radius = catalogue.radius;
        if (box.minX < centreX - radius || box.maxX > centreX + radius || box.minZ < centreZ - radius
                || box.maxZ > centreZ + radius || box.minY < MIN_FLOOR_Y) {
            return false;
        }
        for (Piece other : pieces) {
            if (other.box.intersectsWith(box)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lays out a piece whose entry doorway is the block beyond {@code from}. Local frame: {@code a}
     * along the travel direction (0 = entry wall), {@code c} across (0 = left wall).
     */
    private Piece build(PieceSpec kind, Doorway from, int depth, int corridorLength) {
        EnumFacing f = from.out;
        EnumFacing right = f.rotateY();
        BlockPos entry = from.pos.offset(f);
        int floorY = entry.getY() - 1;
        int length = kind.length() > 0 ? kind.length() : corridorLength;
        int width = kind.width();
        int c0 = width / 2;
        int rise = kind.rise();
        int endFloor = floorY + rise;
        if (rise != 0 && (endFloor < MIN_FLOOR_Y || Math.abs(endFloor - baseFloor) > LEVEL_SPAN)) {
            return null;
        }
        int height = rise != 0 ? PieceKind.CORRIDOR.height : kind.height();
        BlockPos p0 = local(entry, f, right, 0, -c0);
        BlockPos p1 = local(entry, f, right, length - 1, width - 1 - c0);
        // The cistern's pool sinks two blocks below its floor: its box includes them.
        int sink = kind.sink();
        StructureBoundingBox box = new StructureBoundingBox(Math.min(p0.getX(), p1.getX()), Math.min(floorY, endFloor) - sink,
                Math.min(p0.getZ(), p1.getZ()), Math.max(p0.getX(), p1.getX()), Math.max(floorY, endFloor) + height - 1,
                Math.max(p0.getZ(), p1.getZ()));
        Piece piece = new Piece(kind, box, f, depth, floorY, rise, length);
        Doorway back = new Doorway(entry, f.getOpposite());
        back.used = true;
        piece.doors.add(back);
        String exits = kind.exits();
        if ("T".equals(exits)) {
            exits = rand.nextBoolean() ? "L" : "R";
        }
        int mid = length / 2;
        for (char exit : exits.toCharArray()) {
            switch (exit) {
                case 'F':
                    piece.doors.add(new Doorway(local(entry, f, right, length - 1, 0).up(rise), f));
                    break;
                case 'L':
                    piece.doors.add(new Doorway(local(entry, f, right, kind.isTurn() ? length / 2 : mid, -c0),
                            f.rotateYCCW()));
                    break;
                case 'R':
                    piece.doors.add(new Doorway(local(entry, f, right, kind.isTurn() ? length / 2 : mid, width - 1 - c0),
                            right));
                    break;
                default:
                    break;
            }
        }
        return piece;
    }

    private static BlockPos local(BlockPos entry, EnumFacing f, EnumFacing right, int a, int c) {
        return entry.offset(f, a).offset(right, c);
    }
}
