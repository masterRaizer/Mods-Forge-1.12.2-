package com.shatteredcitadels.worldgen.dims.fortress;

import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The plan of the Nether fortress: rooms and platforms on a square lattice around the keep,
 * joined by bridges and covered corridors. Pure: the same seed gives the same fortress.
 */
public final class FortressLayout {

    /** Distance between the centres of neighbouring nodes. */
    public static final int PITCH = 22;
    /** Nodes lie within this many lattice cells of the keep. */
    public static final int RADIUS = 4;
    public static final int NODE_HALF = 4;
    public static final int KEEP_HALF = 8;
    /** Bridges and corridors are five blocks wide. */
    public static final int EDGE_HALF = 2;
    public static final int NODE_HEIGHT = 7;
    public static final int KEEP_HEIGHT = 12;
    public static final int EDGE_HEIGHT = 5;
    /** Horizontal distance from the keep's centre nothing of the fortress goes beyond. */
    public static final int REACH = RADIUS * PITCH + KEEP_HALF;
    public static final int MAX_BLAZE = 4;

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    public enum Kind { KEEP, CROSSING, BLAZE, WART, TURRET }

    public static final class Node {
        public final int cellX;
        public final int cellZ;
        public final int x;
        public final int z;
        /** Neighbours joined by an edge, by direction: north, east, south, west. */
        public final boolean[] doors = new boolean[4];
        Kind kind = Kind.CROSSING;

        Node(int cellX, int cellZ, int x, int z) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.x = x;
            this.z = z;
        }

        public Kind kind() {
            return kind;
        }

        public int half() {
            return kind == Kind.KEEP ? KEEP_HALF : NODE_HALF;
        }

        public int degree() {
            int n = 0;
            for (boolean door : doors) {
                n += door ? 1 : 0;
            }
            return n;
        }
    }

    public static final class Edge {
        public final Node from;
        public final Node to;
        /** A roofed corridor instead of an open bridge. */
        public final boolean covered;

        Edge(Node from, Node to, boolean covered) {
            this.from = from;
            this.to = to;
            this.covered = covered;
        }

        /** @return true if the edge runs along the X axis */
        public boolean alongX() {
            return from.cellZ == to.cellZ;
        }
    }

    public final int originX;
    public final int deckY;
    public final int originZ;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private FortressLayout(int originX, int deckY, int originZ) {
        this.originX = originX;
        this.deckY = deckY;
        this.originZ = originZ;
    }

    public List<Node> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    public Node keep() {
        return nodes.get(0);
    }

    /** @param blaze blaze spawner platforms, clamped to 0..{@link #MAX_BLAZE} */
    public static FortressLayout generate(long seed, int originX, int deckY, int originZ, int blaze) {
        Random random = new Random(seed);
        FortressLayout layout = new FortressLayout(originX, deckY, originZ);
        int side = 2 * RADIUS + 1;
        Node[] grid = new Node[side * side];
        Node keep = layout.addNode(grid, 0, 0);
        keep.kind = Kind.KEEP;
        int target = 18 + random.nextInt(13);
        for (int attempt = 0; attempt < 600 && layout.nodes.size() < target; attempt++) {
            Node from = layout.nodes.get(random.nextInt(layout.nodes.size()));
            int dir = random.nextInt(4);
            int cx = from.cellX + DIRS[dir][0];
            int cz = from.cellZ + DIRS[dir][1];
            if (Math.abs(cx) > RADIUS || Math.abs(cz) > RADIUS || grid[index(cx, cz)] != null
                    || from.degree() >= (from == keep ? 4 : 3)) {
                continue;
            }
            layout.join(from, layout.addNode(grid, cx, cz), dir, random.nextInt(5) < 2);
        }
        // A few loops, so the fortress is not a tree of dead ends.
        for (int attempt = 0, loops = 0; attempt < 60 && loops < 4; attempt++) {
            Node from = layout.nodes.get(random.nextInt(layout.nodes.size()));
            int dir = random.nextInt(4);
            int cx = from.cellX + DIRS[dir][0];
            int cz = from.cellZ + DIRS[dir][1];
            if (Math.abs(cx) > RADIUS || Math.abs(cz) > RADIUS) {
                continue;
            }
            Node to = grid[index(cx, cz)];
            if (to != null && !from.doors[dir]) {
                layout.join(from, to, dir, random.nextInt(5) < 2);
                loops++;
            }
        }
        layout.assignKinds(random, Math.max(0, Math.min(MAX_BLAZE, blaze)));
        return layout;
    }

    private static int index(int cellX, int cellZ) {
        return (cellX + RADIUS) * (2 * RADIUS + 1) + cellZ + RADIUS;
    }

    private Node addNode(Node[] grid, int cellX, int cellZ) {
        Node node = new Node(cellX, cellZ, originX + cellX * PITCH, originZ + cellZ * PITCH);
        grid[index(cellX, cellZ)] = node;
        nodes.add(node);
        return node;
    }

    private void join(Node from, Node to, int dir, boolean covered) {
        from.doors[dir] = true;
        to.doors[(dir + 2) % 4] = true;
        edges.add(new Edge(from, to, covered));
    }

    /** Dead ends first: blaze platforms, then wart rooms, then turrets; the rest are crossings. */
    private void assignKinds(Random random, int blaze) {
        List<Node> order = new ArrayList<>(nodes.subList(1, nodes.size()));
        Collections.shuffle(order, random);
        order.sort((a, b) -> Integer.compare(a.degree(), b.degree()));
        int warts = 1 + random.nextInt(2);
        for (int i = 0; i < order.size(); i++) {
            Node node = order.get(i);
            if (i < blaze) {
                node.kind = Kind.BLAZE;
            } else if (i < blaze + warts) {
                node.kind = Kind.WART;
            } else if (node.degree() == 1) {
                node.kind = Kind.TURRET;
            }
        }
    }

    public int count(Kind kind) {
        int n = 0;
        for (Node node : nodes) {
            n += node.kind == kind ? 1 : 0;
        }
        return n;
    }

    /** @return the volume of a node from the block under its deck to its roof */
    public StructureBoundingBox nodeBox(Node node) {
        int half = node.half();
        return new StructureBoundingBox(node.x - half, deckY - 1, node.z - half, node.x + half,
                deckY + (node.kind == Kind.KEEP ? KEEP_HEIGHT : NODE_HEIGHT), node.z + half);
    }

    /** @return the volume of a bridge or corridor between the walls of its two nodes */
    public StructureBoundingBox edgeBox(Edge edge) {
        Node a = edge.from;
        Node b = edge.to;
        if (edge.alongX()) {
            Node low = a.x < b.x ? a : b;
            Node high = low == a ? b : a;
            return new StructureBoundingBox(low.x + low.half() + 1, deckY - 1, a.z - EDGE_HALF, high.x - high.half() - 1,
                    deckY + EDGE_HEIGHT, a.z + EDGE_HALF);
        }
        Node low = a.z < b.z ? a : b;
        Node high = low == a ? b : a;
        return new StructureBoundingBox(a.x - EDGE_HALF, deckY - 1, low.z + low.half() + 1, a.x + EDGE_HALF,
                deckY + EDGE_HEIGHT, high.z - high.half() - 1);
    }

    /** @return every node and edge volume: the space the fortress rules (mob spawns) apply to */
    public List<StructureBoundingBox> boxes() {
        List<StructureBoundingBox> boxes = new ArrayList<>();
        for (Node node : nodes) {
            boxes.add(nodeBox(node));
        }
        for (Edge edge : edges) {
            boxes.add(edgeBox(edge));
        }
        return boxes;
    }
}
