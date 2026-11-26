package com.shatteredcitadels.worldgen.hut;

import net.minecraft.util.EnumFacing;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The cellar under a witch hut: rooms on a 3x3 grid of cells, the entry room in the middle cell
 * (under the hut), the others grown from it as a random tree and joined by short corridors. The
 * deepest room is the ritual room with the final chest. Pure: no world, no blocks.
 */
public final class CellarLayout {

    /** Distance between the centres of neighbouring rooms. */
    public static final int PITCH = 12;
    /** Clear height of a room; its shell adds a floor and a ceiling. */
    public static final int ROOM_HEIGHT = 4;
    /** Clear height of a corridor. */
    public static final int CORRIDOR_HEIGHT = 2;
    public static final int MIN_ROOMS = 4;
    public static final int MAX_ROOMS = 7;
    /** Splash potions of a trap: poison, slowness, weakness. */
    public static final int POTIONS = 3;
    private static final long SALT = 0x51A7C0FFEE5EEDL;
    private static final EnumFacing[] SIDES = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};

    public enum RoomKind { ENTRY, BREWING_LAB, HERB_STORE, CELLS, RITUAL }

    public static final class Room {
        public final int index;
        public final int gridX;
        public final int gridZ;
        /** Half of the outer size: the walls stand at centre +- half. */
        public final int half;
        /** Rooms between this one and the entry. */
        public final int depth;
        public final int centreX;
        public final int floorY;
        public final int centreZ;
        private RoomKind kind = RoomKind.ENTRY;
        private final boolean[] doors = new boolean[4];

        Room(int index, int gridX, int gridZ, int half, int depth, int originX, int floorY, int originZ) {
            this.index = index;
            this.gridX = gridX;
            this.gridZ = gridZ;
            this.half = half;
            this.depth = depth;
            this.centreX = originX + gridX * PITCH;
            this.floorY = floorY;
            this.centreZ = originZ + gridZ * PITCH;
        }

        public RoomKind kind() {
            return kind;
        }

        /** @return true if a corridor enters through the wall on that side. */
        public boolean hasDoor(EnumFacing side) {
            return doors[side.getHorizontalIndex()];
        }

        /** The whole shell: floor, walls and ceiling. */
        public StructureBoundingBox box() {
            return new StructureBoundingBox(centreX - half, floorY, centreZ - half,
                    centreX + half, floorY + ROOM_HEIGHT + 1, centreZ + half);
        }
    }

    /** A corridor between two neighbouring rooms. */
    public static final class Link {
        public final Room from;
        public final Room to;
        private boolean trapped;
        /** Which splash potion the trap throws, 0..{@link #POTIONS}-1. */
        public final int potion;
        /** The trap's dispenser sits on this side of the corridor (+1 or -1 across it). */
        public final int trapSide;

        Link(Room from, Room to, boolean trapped, int potion, int trapSide) {
            this.from = from;
            this.to = to;
            this.trapped = trapped;
            this.potion = potion;
            this.trapSide = trapSide;
        }

        public boolean trapped() {
            return trapped;
        }

        public boolean alongX() {
            return from.gridZ == to.gridZ;
        }

        private Room low() {
            return (alongX() ? from.gridX < to.gridX : from.gridZ < to.gridZ) ? from : to;
        }

        private Room high() {
            return low() == from ? to : from;
        }

        /** First coordinate along the corridor between the two room walls. */
        public int start() {
            Room low = low();
            return (alongX() ? low.centreX : low.centreZ) + low.half + 1;
        }

        /** Last coordinate along the corridor between the two room walls. */
        public int end() {
            Room high = high();
            return (alongX() ? high.centreX : high.centreZ) - high.half - 1;
        }

        /** The corridor's fixed coordinate across its direction. */
        public int line() {
            return alongX() ? from.centreZ : from.centreX;
        }

        /** Coordinate of the pressure plate along the corridor. */
        public int trapAt() {
            return (start() + end()) / 2;
        }

        /** The shell between the two rooms (their walls not included). */
        public StructureBoundingBox box() {
            int y = from.floorY;
            return alongX()
                    ? new StructureBoundingBox(start(), y, line() - 1, end(), y + CORRIDOR_HEIGHT + 1, line() + 1)
                    : new StructureBoundingBox(line() - 1, y, start(), line() + 1, y + CORRIDOR_HEIGHT + 1, end());
        }
    }

    public final List<Room> rooms;
    public final List<Link> links;

    private CellarLayout(List<Room> rooms, List<Link> links) {
        this.rooms = Collections.unmodifiableList(rooms);
        this.links = Collections.unmodifiableList(links);
    }

    public Room entry() {
        return rooms.get(0);
    }

    public Room ritual() {
        for (Room room : rooms) {
            if (room.kind == RoomKind.RITUAL) {
                return room;
            }
        }
        throw new IllegalStateException("no ritual room");
    }

    /** @return the box around every room and corridor. */
    public StructureBoundingBox bounds() {
        StructureBoundingBox all = new StructureBoundingBox(entry().box());
        for (Room room : rooms) {
            all.expandTo(room.box());
        }
        return all;
    }

    /**
     * @param originX centre of the entry room (the hut's hatch)
     * @param floorY  the floor block layer of every room
     */
    public static CellarLayout generate(long seed, int originX, int floorY, int originZ) {
        Random random = new Random(mix(seed ^ SALT));
        int count = MIN_ROOMS + random.nextInt(MAX_ROOMS - MIN_ROOMS + 1);
        List<Room> rooms = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        boolean[][] taken = new boolean[3][3];
        rooms.add(new Room(0, 0, 0, 3, 0, originX, floorY, originZ));
        taken[1][1] = true;
        while (rooms.size() < count) {
            // Mostly from the newest room, so the cellar winds away instead of forming a star.
            Room parent = random.nextInt(5) < 3 ? rooms.get(rooms.size() - 1) : rooms.get(random.nextInt(rooms.size()));
            EnumFacing side = freeSide(parent, taken, random);
            for (int i = 0; side == null && i < rooms.size(); i++) {
                parent = rooms.get(i);
                side = freeSide(parent, taken, random);
            }
            if (side == null) {
                break;
            }
            Room room = new Room(rooms.size(), parent.gridX + side.getXOffset(), parent.gridZ + side.getZOffset(),
                    3 + random.nextInt(2), parent.depth + 1, originX, floorY, originZ);
            taken[room.gridX + 1][room.gridZ + 1] = true;
            parent.doors[side.getHorizontalIndex()] = true;
            room.doors[side.getOpposite().getHorizontalIndex()] = true;
            rooms.add(room);
            links.add(new Link(parent, room, random.nextInt(5) < 3, random.nextInt(POTIONS), random.nextBoolean() ? 1 : -1));
        }
        Room ritual = rooms.get(rooms.size() - 1);
        for (Room room : rooms) {
            if (room.depth >= ritual.depth) {
                ritual = room;
            }
        }
        RoomKind[] others = {RoomKind.BREWING_LAB, RoomKind.HERB_STORE, RoomKind.CELLS};
        for (int i = others.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            RoomKind swap = others[i];
            others[i] = others[j];
            others[j] = swap;
        }
        int next = 0;
        for (Room room : rooms) {
            if (room == ritual) {
                room.kind = RoomKind.RITUAL;
            } else if (room.index > 0) {
                room.kind = others[next++ % others.length];
            }
        }
        boolean anyTrap = false;
        for (Link link : links) {
            anyTrap |= link.trapped;
        }
        if (!anyTrap) {
            // The way to the final chest is never free.
            for (Link link : links) {
                if (link.to == ritual) {
                    link.trapped = true;
                }
            }
        }
        return new CellarLayout(rooms, links);
    }

    /** Spreads the bits of a seed: the seeds of neighbouring sites differ in a few low bits only. */
    public static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static EnumFacing freeSide(Room room, boolean[][] taken, Random random) {
        int first = random.nextInt(SIDES.length);
        for (int i = 0; i < SIDES.length; i++) {
            EnumFacing side = SIDES[(first + i) % SIDES.length];
            int gx = room.gridX + side.getXOffset();
            int gz = room.gridZ + side.getZOffset();
            if (Math.abs(gx) <= 1 && Math.abs(gz) <= 1 && !taken[gx + 1][gz + 1]) {
                return side;
            }
        }
        return null;
    }
}
