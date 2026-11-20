package com.shatteredcitadels.worldgen.catacomb;

import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.RoomTag;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePieces;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes catacombs into a blueprint: a spiral stair from the structure down to level 0, then
 * 1-3 levels of corridors and halls on a 5-block cell grid (see {@link CatacombLayout}), each
 * joined to the next by a spiral that stays sealed until the level above is cleared.
 *
 * <p>Cell (ci, cj) covers x0 + 5ci .. x0 + 5ci + 4; its walls are offsets 0 and 4, the corridor
 * the 3 blocks between. A level's floor is at yL, the corridor yL+1..yL+4, the ceiling yL+5.
 */
public class CatacombBuilder {

    public static final int CELL = 5;
    public static final int CELLS = 19;
    public static final int LEVEL_STEP = 10;
    public static final int CORRIDOR_HEIGHT = 4;
    private static final int MIN_FLOOR_Y = BlockWriter.MIN_Y + 1;

    /** Ring around the 1x1 core of a spiral, walked clockwise from the north-west corner. */
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
    private static final EnumFacing[] DIRS = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    protected final BlockWriter w;
    protected final CitadelInstance rec;
    protected final BlockPalette surface;
    protected final Random rand;
    protected final int baseTier;
    protected final CatacombPalette cat = new CatacombPalette();
    protected final IBlockState air = Blocks.AIR.getDefaultState();

    /** Grid origin of the levels being built. */
    protected int x0;
    protected int z0;
    protected int levelCount;
    protected final List<CatacombLayout> layouts = new ArrayList<>();
    private boolean sanctumEnabled;

    /** Floor index of the mage's sanctum. */
    public static final int SANCTUM_FLOOR = -10;
    /** The sanctum's floor lies this far below its level's floor. */
    public static final int SANCTUM_DEPTH = 6;
    /** Sanctum ceiling height above its level's floor. */
    private static final int SANCTUM_TOP = 8;

    public CatacombBuilder(BlockWriter w, CitadelInstance rec, BlockPalette surface, Random rand, int baseTier) {
        this.w = w;
        this.rec = rec;
        this.surface = surface;
        this.rand = rand;
        this.baseTier = baseTier;
    }

    /** Half the side of a full catacomb plus its shell and the alignment shift, for planners. */
    public static int footprintRadius() {
        return CELL * CELLS / 2 + 2;
    }

    /** @return y of level k's floor. */
    public static int levelY(int topLevelY, int k) {
        return topLevelY - k * LEVEL_STEP;
    }

    /** @return how many of {@code levels} fit with every floor at y 6 or above. */
    public static int fittingLevels(int topLevelY, int levels) {
        int count = 0;
        while (count < levels && levelY(topLevelY, count) >= MIN_FLOOR_Y) {
            count++;
        }
        return count;
    }

    /**
     * @param entranceTopY y of the structure floor the spiral starts in (its first step replaces it)
     * @return levels built; 0 (and nothing registered) when not even one fits
     */
    /** The deepest level gets the guardian mage's sanctum. */
    public CatacombBuilder withSanctum() {
        this.sanctumEnabled = true;
        return this;
    }

    public List<CatacombLayout> layouts() {
        return java.util.Collections.unmodifiableList(layouts);
    }

    /** Interior of a hall (corridor height) on the level whose floor is {@code yL}. */
    public StructureBoundingBox hallBox(CatacombLayout.Hall hall, int yL) {
        return new StructureBoundingBox(cellX(hall.ci0) + 1, yL + 1, cellZ(hall.cj0) + 1,
                cellX(hall.ci1) + 3, yL + CORRIDOR_HEIGHT, cellZ(hall.cj1) + 3);
    }

    public int build(int centreX, int centreZ, int topLevelY, int levels, int entranceX, int entranceZ, int entranceTopY) {
        int count = fittingLevels(topLevelY, levels);
        while (sanctumEnabled && count > 0 && levelY(topLevelY, count - 1) - SANCTUM_DEPTH < MIN_FLOOR_Y) {
            count--;
        }
        if (count == 0) {
            return 0;
        }
        levelCount = count;
        int si = clamp(Math.round((entranceX - (centreX - CELL * CELLS / 2.0F)) / CELL - 0.5F), 1, CELLS - 2);
        int sj = clamp(Math.round((entranceZ - (centreZ - CELL * CELLS / 2.0F)) / CELL - 0.5F), 1, CELLS - 2);
        // Align the grid so the entrance cell's centre is exactly the spiral's core.
        x0 = entranceX - 2 - si * CELL;
        z0 = entranceZ - 2 - sj * CELL;

        int stairI = si;
        int stairJ = sj;
        for (int k = 0; k < count; k++) {
            boolean last = k == count - 1;
            CatacombLayout layout = CatacombLayout.generate(rand.nextLong(), CELLS, stairI, stairJ, hasSanctum(last), !last);
            layouts.add(layout);
            if (layout.downStair() != null) {
                stairI = layout.downStair().ci0;
                stairJ = layout.downStair().cj0;
            }
        }

        for (int k = 0; k < count; k++) {
            int yL = levelY(topLevelY, k);
            CatacombLayout layout = layouts.get(k);
            registerLevel(k, yL);
            shellLevel(layout, yL);
            carveLevel(layout, yL);
            for (CatacombLayout.Hall hall : layout.halls()) {
                if (hall.kind() != CatacombLayout.HallKind.STAIR) {
                    buildHall(hall, k, yL);
                }
            }
            dressCorridors(layout, k, yL);
        }
        // Stairs last: the level shells (ceilings, walls) must not cover them.
        spiral(entranceX, entranceZ, entranceTopY, levelY(topLevelY, 0));
        for (int k = 0; k < count; k++) {
            CatacombLayout.Hall down = layouts.get(k).downStair();
            if (down != null) {
                int yL = levelY(topLevelY, k);
                int cx = cellX(down.ci0) + 2;
                int cz = cellZ(down.cj0) + 2;
                spiral(cx, cz, yL, levelY(topLevelY, k + 1));
                sealSpiralTop(cx, cz, yL, -(k + 1));
            }
        }
        // Protected: the levels (up to level 0's ceiling, plus the spider caves' raised ceilings) and
        // the entrance shaft. The ground above stays diggable.
        int bottom = levelY(topLevelY, count - 1) - extraDepth();
        int levelsTop = levelY(topLevelY, 0) + CORRIDOR_HEIGHT + 4;
        rec.addPart(new StructureBoundingBox(x0 - 1, bottom - 1, z0 - 1, x0 + CELL * CELLS, levelsTop, z0 + CELL * CELLS));
        rec.addPart(new StructureBoundingBox(entranceX - 2, levelsTop, entranceZ - 2, entranceX + 2, entranceTopY, entranceZ + 2));
        return count;
    }

    /** The deepest level holds the mage's sanctum when enabled. */
    protected boolean hasSanctum(boolean deepest) {
        return sanctumEnabled && deepest;
    }

    /** Extra depth dug below the deepest level (the sanctum). */
    protected int extraDepth() {
        return sanctumEnabled ? SANCTUM_DEPTH : 0;
    }

    // ------------------------------------------------------------------ geometry

    protected int cellX(int ci) {
        return x0 + ci * CELL;
    }

    protected int cellZ(int cj) {
        return z0 + cj * CELL;
    }

    private void registerLevel(int k, int yL) {
        int tier = FloorData.clampDifficulty(baseTier + 1 + k);
        FloorData floor = rec.addFloor(-(k + 1), yL, yL + LEVEL_STEP - 1, tier, ConfigCache.get().rollWaveCount(rand), false)
                .setArea(x0, z0, x0 + CELL * CELLS - 1, z0 + CELL * CELLS - 1)
                .setZoneKey("catacombs_level_" + (k + 1));
        if (k > 0) {
            floor.setRequiresFloor(-k);
        }
    }

    /** Solid masonry box for every cell: floor, walls, ceiling (carving comes after). */
    private void shellLevel(CatacombLayout layout, int yL) {
        for (int ci = 0; ci < layout.cells(); ci++) {
            for (int cj = 0; cj < layout.cells(); cj++) {
                if (!layout.open(ci, cj)) {
                    continue;
                }
                int bx = cellX(ci);
                int bz = cellZ(cj);
                for (int dx = 0; dx < CELL; dx++) {
                    for (int dz = 0; dz < CELL; dz++) {
                        int x = bx + dx;
                        int z = bz + dz;
                        w.set(x, yL, z, cat.floor(rand));
                        for (int y = yL + 1; y <= yL + CORRIDOR_HEIGHT; y++) {
                            w.set(x, y, z, cat.wall(rand));
                        }
                        w.set(x, yL + CORRIDOR_HEIGHT + 1, z, cat.ceiling());
                    }
                }
            }
        }
    }

    /** Corridors: the 3x3 middle of each cell plus the gap towards every linked neighbour; halls whole. */
    private void carveLevel(CatacombLayout layout, int yL) {
        for (int ci = 0; ci < layout.cells(); ci++) {
            for (int cj = 0; cj < layout.cells(); cj++) {
                if (!layout.open(ci, cj)) {
                    continue;
                }
                int bx = cellX(ci);
                int bz = cellZ(cj);
                carve(bx + 1, bz + 1, bx + 3, bz + 3, yL);
                if (layout.link(ci, cj, EnumFacing.EAST)) {
                    carve(bx + 4, bz + 1, bx + 5, bz + 3, yL);
                }
                if (layout.link(ci, cj, EnumFacing.SOUTH)) {
                    carve(bx + 1, bz + 4, bx + 3, bz + 5, yL);
                }
                CatacombLayout.Hall hall = layout.hallAt(ci, cj);
                if (hall != null && hall.kind() != CatacombLayout.HallKind.STAIR) {
                    // Halls are one room: also remove the pillars between linked cells.
                    if (ci < hall.ci1 && cj < hall.cj1) {
                        carve(bx + 4, bz + 4, bx + 5, bz + 5, yL);
                    }
                }
            }
        }
    }

    private void carve(int xa, int za, int xb, int zb, int yL) {
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                for (int y = yL + 1; y <= yL + CORRIDOR_HEIGHT; y++) {
                    w.set(x, y, z, air);
                }
            }
        }
    }

    // ------------------------------------------------------------------ halls

    /** Furnishes a carved hall according to its kind. */
    protected void buildHall(CatacombLayout.Hall hall, int level, int yL) {
        StructureBoundingBox box = hallBox(hall, yL);
        switch (hall.kind()) {
            case CRYPT:
                cryptHall(box, level, yL);
                break;
            case SPIDER_CAVE:
                spiderCave(box, level, yL);
                break;
            case SKELETON_HALL:
                skeletonHall(box, level, yL);
                break;
            case STOREROOM:
            case GUARD_POST:
                livingRoom(box, level, yL, hall.kind() == CatacombLayout.HallKind.STOREROOM ? RoomTag.PANTRY : RoomTag.BEDROOM);
                break;
            case SANCTUM:
                sanctum(hall, box, level, yL);
                break;
            default:
                break;
        }
    }

    private IBlockState slab() {
        return Blocks.STONE_SLAB.getDefaultState().withProperty(net.minecraft.block.BlockStoneSlab.VARIANT,
                net.minecraft.block.BlockStoneSlab.EnumType.SMOOTHBRICK);
    }

    /** Two-block stone coffin with a slab lid, along z. */
    private void sarcophagus(int x, int z, int yL) {
        for (int dz = 0; dz <= 1; dz++) {
            w.set(x, yL + 1, z + dz, cat.pillar());
            w.set(x, yL + 2, z + dz, slab());
        }
    }

    /** True if the wall block at (x, z) is masonry (not a doorway). */
    private boolean solidWall(int x, int y, int z) {
        return w.isSolid(x, y, z);
    }

    private void cryptHall(StructureBoundingBox box, int level, int yL) {
        int midX = (box.minX + box.maxX) / 2;
        for (int x = box.minX + 1; x <= box.maxX - 1; x += 3) {
            if (Math.abs(x - midX) <= 1) {
                continue;
            }
            for (int z = box.minZ + 1; z + 1 <= box.maxZ - 1; z += 4) {
                sarcophagus(x, z, yL);
            }
        }
        // Wall tombs in the two long walls.
        for (int z = box.minZ + 1; z <= box.maxZ - 1; z += 3) {
            for (int x : new int[]{box.minX - 1, box.maxX + 1}) {
                if (solidWall(x, yL + 1, z) && solidWall(x, yL + 3, z)) {
                    w.set(x, yL + 1, z, cat.bone());
                    w.set(x, yL + 2, z, rand.nextBoolean()
                            ? Blocks.SKULL.getDefaultState().withProperty(BlockSkull.FACING, EnumFacing.UP) : air);
                }
            }
        }
        spawner(new BlockPos(midX, yL + 1, (box.minZ + box.maxZ) / 2), level, RoomTag.OSSUARY);
        vases(box.minX, box.minZ, box.maxX, box.maxZ, yL, 2 + rand.nextInt(3));
        if (rand.nextBoolean()) {
            chest(new BlockPos(box.minX, yL + 1, box.maxZ), EnumFacing.EAST, level);
        }
    }

    private void spiderCave(StructureBoundingBox box, int level, int yL) {
        IBlockState coarse = Blocks.DIRT.getDefaultState().withProperty(net.minecraft.block.BlockDirt.VARIANT,
                net.minecraft.block.BlockDirt.DirtType.COARSE_DIRT);
        for (int x = box.minX; x <= box.maxX; x++) {
            for (int z = box.minZ; z <= box.maxZ; z++) {
                int r = rand.nextInt(4);
                w.set(x, yL, z, r == 0 ? coarse : r == 1 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : cat.floor(rand));
                // A cave ceiling: higher and uneven.
                int top = yL + CORRIDOR_HEIGHT + rand.nextInt(3);
                for (int y = yL + CORRIDOR_HEIGHT + 1; y <= top; y++) {
                    w.set(x, y, z, air);
                }
                w.set(x, top + 1, z, rand.nextBoolean() ? Blocks.STONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
                if (rand.nextInt(4) == 0) {
                    w.set(x, top, z, Blocks.WEB.getDefaultState());
                }
                boolean edge = x == box.minX || x == box.maxX || z == box.minZ || z == box.maxZ;
                if (edge && rand.nextInt(5) == 0 && w.get(x, yL + 1, z).getBlock() == Blocks.AIR) {
                    w.set(x, yL + 1, z, Blocks.WEB.getDefaultState());
                }
            }
        }
        // Egg clutches: clay vases in little groups.
        if (ModBlocks.VASE != null) {
            for (int g = 0; g < 2; g++) {
                int cx = box.minX + 1 + rand.nextInt(Math.max(1, box.maxX - box.minX - 1));
                int cz = box.minZ + 1 + rand.nextInt(Math.max(1, box.maxZ - box.minZ - 1));
                for (int i = 0; i < 3; i++) {
                    int x = cx + rand.nextInt(3) - 1;
                    int z = cz + rand.nextInt(3) - 1;
                    if (box.isVecInside(new BlockPos(x, yL + 1, z)) && w.get(x, yL + 1, z).getBlock() == Blocks.AIR) {
                        w.set(x, yL + 1, z, ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT, BlockVase.Variant.CLAY));
                    }
                }
            }
        }
        spawner(new BlockPos((box.minX + box.maxX) / 2, yL + 1, (box.minZ + box.maxZ) / 2), level, RoomTag.UNDERGROUND_LAIR);
        if (box.maxX - box.minX >= 8 || box.maxZ - box.minZ >= 8) {
            spawner(new BlockPos(box.minX + 2, yL + 1, box.maxZ - 2), level, RoomTag.UNDERGROUND_LAIR);
        }
    }

    private void skeletonHall(StructureBoundingBox box, int level, int yL) {
        boolean alongX = box.maxX - box.minX >= box.maxZ - box.minZ;
        int lo = alongX ? box.minZ : box.minX;
        int hi = alongX ? box.maxZ : box.maxX;
        int lineA = lo + (hi - lo) / 3;
        int lineB = hi - (hi - lo) / 3;
        int from = alongX ? box.minX + 1 : box.minZ + 1;
        int to = alongX ? box.maxX - 1 : box.maxZ - 1;
        for (int t = from; t <= to; t += 3) {
            for (int line : new int[]{lineA, lineB}) {
                int x = alongX ? t : line;
                int z = alongX ? line : t;
                for (int y = yL + 1; y <= yL + CORRIDOR_HEIGHT; y++) {
                    w.set(x, y, z, cat.pillar());
                }
            }
        }
        // Ossuary: one long wall faced with bone and skulls.
        int wall = rand.nextBoolean() ? lo - 1 : hi + 1;
        for (int t = (alongX ? box.minX : box.minZ); t <= (alongX ? box.maxX : box.maxZ); t++) {
            int x = alongX ? t : wall;
            int z = alongX ? wall : t;
            for (int y = yL + 1; y <= yL + CORRIDOR_HEIGHT; y++) {
                if (solidWall(x, y, z)) {
                    w.set(x, y, z, cat.bone());
                }
            }
            int fx = alongX ? t : (wall < lo ? lo : hi);
            int fz = alongX ? (wall < lo ? lo : hi) : t;
            if (rand.nextInt(3) == 0 && w.get(fx, yL + 1, fz).getBlock() == Blocks.AIR) {
                w.set(fx, yL + 1, fz, Blocks.SKULL.getDefaultState().withProperty(BlockSkull.FACING, EnumFacing.UP));
            }
        }
        // Tomb rows between the colonnades.
        for (int t = from + 1; t + 1 <= to; t += 4) {
            int mid = (lineA + lineB) / 2;
            if (alongX) {
                w.set(t, yL + 1, mid, cat.pillar());
                w.set(t + 1, yL + 1, mid, cat.pillar());
                w.set(t, yL + 2, mid, slab());
                w.set(t + 1, yL + 2, mid, slab());
            } else {
                sarcophagus(mid, t, yL);
            }
        }
        spawner(new BlockPos(alongX ? (box.minX + box.maxX) / 2 : lineA + 1, yL + 1, alongX ? lineA + 1 : (box.minZ + box.maxZ) / 2),
                level, RoomTag.OSSUARY);
        vases(box.minX, box.minZ, box.maxX, box.maxZ, yL, 1 + rand.nextInt(3));
    }

    private void livingRoom(StructureBoundingBox box, int level, int yL, RoomTag tag) {
        com.shatteredcitadels.worldgen.RoomFurnisher furnisher = new com.shatteredcitadels.worldgen.RoomFurnisher(
                new com.shatteredcitadels.worldgen.RoomFurnisher.Site() {
                    @Override
                    public boolean free(int x, int y, int z) {
                        return box.isVecInside(new BlockPos(x, box.minY, z)) && w.get(x, y, z).getBlock() == Blocks.AIR;
                    }

                    @Override
                    public IBlockState get(int x, int y, int z) {
                        return w.get(x, y, z);
                    }

                    @Override
                    public boolean set(int x, int y, int z, IBlockState state) {
                        return w.set(x, y, z, state);
                    }

                    @Override
                    public void chest(BlockPos pos, EnumFacing facing, ResourceLocation lootTable) {
                        StructurePieces.chest(w, rec, pos, facing, lootTable, rand.nextLong());
                    }

                    @Override
                    public void afterPlace(BlockPos pos, com.shatteredcitadels.worldgen.Blueprint.TileAction action) {
                        w.afterPlace(pos, action);
                    }
                }, rand, surface, BlockVase.Variant.DARK);
        furnisher.furnish(tag, box.minX, box.minZ, box.maxX, box.maxZ, yL);
        furnisher.abandon(box.minX, box.minZ, box.maxX, box.maxZ, yL, CORRIDOR_HEIGHT);
        spawner(new BlockPos((box.minX + box.maxX) / 2, yL + 1, (box.minZ + box.maxZ) / 2), level, RoomTag.GUARD_ROOM);
    }

    // ------------------------------------------------------------------ sanctum

    /**
     * The guardian mage's library: dug {@link #SANCTUM_DEPTH} below the level, reached by one
     * sealed doorway and a flight of stairs; bookshelves, a dais with the hidden sigil and sealed
     * reward pedestals around it.
     */
    private void sanctum(CatacombLayout.Hall hall, StructureBoundingBox box, int level, int yL) {
        int fy = yL - SANCTUM_DEPTH;
        int top = yL + SANCTUM_TOP;
        int a = box.minX;
        int b = box.maxX;
        int c = box.minZ;
        int d = box.maxZ;
        // Shell: floor, walls and ceiling around the taller, deeper room.
        for (int x = a - 1; x <= b + 1; x++) {
            for (int z = c - 1; z <= d + 1; z++) {
                boolean edge = x == a - 1 || x == b + 1 || z == c - 1 || z == d + 1;
                w.set(x, fy, z, cat.floor(rand));
                w.set(x, top, z, cat.ceiling());
                for (int y = fy + 1; y < top; y++) {
                    if (edge) {
                        if (y <= yL || y > yL + CORRIDOR_HEIGHT || solidWall(x, y, z)) {
                            w.set(x, y, z, cat.wall(rand));
                        }
                    } else {
                        w.set(x, y, z, air);
                    }
                }
            }
        }
        int[] door = sanctumDoor(hall);
        EnumFacing out = EnumFacing.byIndex(door[2]);
        EnumFacing in = out.getOpposite();
        // The doorway plane on the sanctum side and its three lanes.
        int planeX = out == EnumFacing.WEST ? a - 1 : out == EnumFacing.EAST ? b + 1 : 0;
        int planeZ = out == EnumFacing.NORTH ? c - 1 : out == EnumFacing.SOUTH ? d + 1 : 0;
        List<int[]> lanes = new ArrayList<>();
        for (int t = 1; t <= 3; t++) {
            if (out.getAxis() == EnumFacing.Axis.X) {
                lanes.add(new int[]{planeX, cellZ(door[1]) + t});
            } else {
                lanes.add(new int[]{cellX(door[0]) + t, planeZ});
            }
        }
        // Landing and a flight down to the sanctum floor.
        for (int[] lane : lanes) {
            for (int y = yL + 1; y <= yL + CORRIDOR_HEIGHT; y++) {
                w.set(lane[0], y, lane[1], air);
            }
            for (int step = 1; step <= SANCTUM_DEPTH + 1; step++) {
                int x = lane[0] + in.getXOffset() * step;
                int z = lane[1] + in.getZOffset() * step;
                int surfaceY = yL - (step - 1);
                for (int y = fy + 1; y < surfaceY; y++) {
                    w.set(x, y, z, cat.wall(rand));
                }
                w.set(x, surfaceY, z, step == 1 ? cat.floor(rand) : stair(out));
            }
        }
        // Sealed doorway: opens when the deepest level is cleared.
        if (ConfigCache.get().isLockFloorsUntilCleared() && ModBlocks.SEALED_GATE != null) {
            for (int[] lane : lanes) {
                for (int y = yL + 1; y <= yL + CORRIDOR_HEIGHT; y++) {
                    seal(lane[0], y, lane[1], -levelCount);
                }
            }
        }

        int cx = (a + b) / 2;
        int cz = (c + d) / 2;
        // Bookshelf walls, except around the stairs.
        for (int x = a; x <= b; x++) {
            for (int z = c; z <= d; z++) {
                boolean edge = x == a || x == b || z == c || z == d;
                if (!edge || nearLanes(lanes, x, z, in, 3)) {
                    continue;
                }
                for (int y = fy + 1; y <= fy + 5; y++) {
                    w.set(x, y, z, Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
        // Four great pillars with lights.
        int q = (b - a) / 4;
        for (int[] p : new int[][]{{a + q, c + q}, {b - q, c + q}, {a + q, d - q}, {b - q, d - q}}) {
            for (int y = fy + 1; y < top; y++) {
                w.set(p[0], y, p[1], y == fy + 6 ? surface.get(com.shatteredcitadels.palette.PaletteSlot.LIGHT) : cat.pillar());
            }
        }
        // Freestanding stacks, each with a teleport anchor on its inner side.
        List<BlockPos> anchors = new ArrayList<>();
        int r = Math.max(5, (b - a) / 2 - 3);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8 + Math.PI / 8;
            int sx = cx + (int) Math.round(Math.cos(angle) * r);
            int sz = cz + (int) Math.round(Math.sin(angle) * r);
            if (nearLanes(lanes, sx, sz, in, 4)) {
                continue;
            }
            for (int y = fy + 1; y <= fy + 3; y++) {
                w.set(sx, y, sz, Blocks.BOOKSHELF.getDefaultState());
            }
            int ax = sx + Integer.signum(cx - sx);
            int az = sz + Integer.signum(cz - sz);
            if (w.get(ax, fy + 1, az).getBlock() == Blocks.AIR) {
                anchors.add(new BlockPos(ax, fy + 1, az));
            }
        }
        // Dais with the sigil hidden under its centre.
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                w.set(x, fy + 1, z, Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3 ? cat.accent() : Blocks.QUARTZ_BLOCK.getDefaultState());
            }
        }
        w.set(cx - 3, fy + 2, cz - 3, Blocks.ENCHANTING_TABLE.getDefaultState());
        w.set(cx + 3, fy + 2, cz + 3, Blocks.ENCHANTING_TABLE.getDefaultState());
        w.set(cx + 3, fy + 2, cz - 3, Blocks.BREWING_STAND.getDefaultState());
        w.set(cx - 3, fy + 2, cz + 3, Blocks.BREWING_STAND.getDefaultState());
        w.set(a + 1, fy + 1, c + 1, Blocks.CAULDRON.getDefaultState());
        w.set(b - 1, fy + 1, d - 1, Blocks.CAULDRON.getDefaultState());
        for (int y = top - 2; y < top; y++) {
            w.set(cx, y, cz, Blocks.IRON_BARS.getDefaultState());
        }
        w.set(cx, top - 3, cz, surface.get(com.shatteredcitadels.palette.PaletteSlot.LIGHT));
        // Pedestals: a pillar, a chest and a ring of sealed gates that wait for the mage's death.
        int[][] spots = {{0, -5}, {0, 5}, {-5, 0}, {5, 0}, {-5, -5}, {5, 5}};
        int pedestals = 4 + rand.nextInt(3);
        for (int i = 0; i < pedestals; i++) {
            int px = cx + spots[i][0];
            int pz = cz + spots[i][1];
            w.set(px, fy + 1, pz, Blocks.QUARTZ_BLOCK.getDefaultState().withProperty(net.minecraft.block.BlockQuartz.VARIANT,
                    net.minecraft.block.BlockQuartz.EnumType.LINES_Y));
            EnumFacing facing = EnumFacing.getFacingFromVector(cx - px, 0, cz - pz);
            BlockPos chestPos = new BlockPos(px, fy + 2, pz);
            StructurePieces.chest(w, rec, chestPos, facing, i == 0 ? ModLootTables.MAGE_PEDESTAL_FOLIO : ModLootTables.MAGE_PEDESTAL,
                    rand.nextLong());
            if (ModBlocks.SEALED_GATE != null) {
                for (EnumFacing side : EnumFacing.HORIZONTALS) {
                    seal(px + side.getXOffset(), fy + 2, pz + side.getZOffset(), SANCTUM_FLOOR);
                }
                seal(px, fy + 3, pz, SANCTUM_FLOOR);
            }
        }
        StructureBoundingBox area = new StructureBoundingBox(a, fy, c, b, top, d);
        if (ModBlocks.MAGE_SIGIL != null) {
            BlockPos sigil = new BlockPos(cx, fy, cz);
            w.set(sigil, ModBlocks.MAGE_SIGIL.getDefaultState());
            List<BlockPos> anchorCopy = new ArrayList<>(anchors);
            java.util.UUID id = rec.getCitadelId();
            w.afterPlace(sigil, (world, at) -> {
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(at);
                if (tile instanceof com.shatteredcitadels.tile.TileEntityMageSigil) {
                    ((com.shatteredcitadels.tile.TileEntityMageSigil) tile).configure(id, SANCTUM_FLOOR, area, anchorCopy);
                }
            });
        }
        rec.addFloor(SANCTUM_FLOOR, fy, top, 5, 0, true)
                .setSideBoss(true)
                .setArea(a, c, b, d)
                .setZoneKey("mage_sanctum")
                .setRequiresFloor(-levelCount);
    }

    private static boolean nearLanes(List<int[]> lanes, int x, int z, EnumFacing in, int depth) {
        for (int[] lane : lanes) {
            for (int step = 0; step <= depth; step++) {
                int lx = lane[0] + in.getXOffset() * step;
                int lz = lane[1] + in.getZOffset() * step;
                if (Math.abs(lx - x) <= 1 && Math.abs(lz - z) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return {ci, cj, facing index} of the sanctum's single doorway (towards the corridor). */
    private int[] sanctumDoor(CatacombLayout.Hall hall) {
        CatacombLayout layout = layouts.get(layouts.size() - 1);
        for (int i = hall.ci0; i <= hall.ci1; i++) {
            for (int j = hall.cj0; j <= hall.cj1; j++) {
                for (EnumFacing dir : DIRS) {
                    int ni = i + dir.getXOffset();
                    int nj = j + dir.getZOffset();
                    if (!hall.contains(ni, nj) && layout.link(i, j, dir)) {
                        return new int[]{i, j, dir.getIndex()};
                    }
                }
            }
        }
        return new int[]{hall.ci0, hall.cj0, EnumFacing.NORTH.getIndex()};
    }

    // ------------------------------------------------------------------ corridors

    private void dressCorridors(CatacombLayout layout, int level, int yL) {
        int corridorCells = 0;
        for (int ci = 0; ci < layout.cells(); ci++) {
            for (int cj = 0; cj < layout.cells(); cj++) {
                if (!layout.open(ci, cj) || layout.hallAt(ci, cj) != null) {
                    continue;
                }
                corridorCells++;
                int bx = cellX(ci);
                int bz = cellZ(cj);
                int links = 0;
                for (EnumFacing d : DIRS) {
                    if (layout.link(ci, cj, d)) {
                        links++;
                    }
                }
                for (EnumFacing d : DIRS) {
                    boolean internal = layout.open(ci + d.getXOffset(), cj + d.getZOffset());
                    if (!layout.link(ci, cj, d) && internal && rand.nextInt(3) == 0) {
                        boneNiche(bx, bz, d, yL);
                    }
                }
                int roll = rand.nextInt(12);
                if (roll == 0) {
                    caveIn(bx, bz, yL);
                } else if (roll <= 2) {
                    w.set(bx + 1 + rand.nextInt(3), yL + CORRIDOR_HEIGHT, bz + 1 + rand.nextInt(3), Blocks.WEB.getDefaultState());
                }
                if (rand.nextInt(6) == 0) {
                    torch(layout, ci, cj, yL);
                }
                if (links == 1 && rand.nextInt(4) == 0) {
                    // Dead end: something left behind.
                    if (rand.nextBoolean()) {
                        chest(new BlockPos(bx + 2, yL + 1, bz + 2), EnumFacing.SOUTH, level);
                    } else {
                        vases(bx + 1, bz + 1, bx + 3, bz + 3, yL, 2);
                    }
                }
                if (rand.nextInt(20) == 0) {
                    spawner(new BlockPos(bx + 2, yL + 1, bz + 2), level, RoomTag.UNDERGROUND_LAIR);
                }
            }
        }
        // At least a couple of corridor spawners per level, so every level has waves.
        for (int i = 0; i < 2 + corridorCells / 60; i++) {
            int ci = rand.nextInt(layout.cells());
            int cj = rand.nextInt(layout.cells());
            if (layout.hallAt(ci, cj) == null) {
                spawner(new BlockPos(cellX(ci) + 2, yL + 1, cellZ(cj) + 2), level, RoomTag.UNDERGROUND_LAIR);
            }
        }
    }

    /** A recess in the wall facing {@code d}, with a skull or bones in it. */
    private void boneNiche(int bx, int bz, EnumFacing d, int yL) {
        int along = 1 + rand.nextInt(3);
        int x = d == EnumFacing.WEST ? bx : d == EnumFacing.EAST ? bx + 4 : bx + along;
        int z = d == EnumFacing.NORTH ? bz : d == EnumFacing.SOUTH ? bz + 4 : bz + along;
        w.set(x, yL + 1, z, cat.bone());
        w.set(x, yL + 2, z, rand.nextInt(3) == 0
                ? Blocks.SKULL.getDefaultState().withProperty(BlockSkull.FACING, EnumFacing.UP) : air);
        w.set(x, yL + 3, z, air);
    }

    /** Fallen stones along one side; the middle stays walkable. */
    private void caveIn(int bx, int bz, int yL) {
        boolean alongX = rand.nextBoolean();
        int side = rand.nextBoolean() ? 1 : 3;
        for (int t = 1; t <= 3; t++) {
            if (rand.nextInt(3) == 0) {
                continue;
            }
            int x = alongX ? bx + t : bx + side;
            int z = alongX ? bz + side : bz + t;
            w.set(x, yL + 1, z, rand.nextBoolean() ? Blocks.GRAVEL.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
        }
    }

    private void torch(CatacombLayout layout, int ci, int cj, int yL) {
        for (EnumFacing d : DIRS) {
            if (layout.link(ci, cj, d)) {
                continue;
            }
            // Torch on the inner face of the wall on side d, pointing back into the corridor.
            int x = cellX(ci) + 2 + d.getXOffset();
            int z = cellZ(cj) + 2 + d.getZOffset();
            if (w.get(x, yL + 3, z).getBlock() == Blocks.AIR) {
                w.set(x, yL + 3, z, Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, d.getOpposite()));
            }
            return;
        }
    }

    // ------------------------------------------------------------------ spirals

    /**
     * Steps of a spiral whose first step is at {@code topY} and whose last stands one block above
     * {@code bottomFloorY}: {dx, y, dz, facing index of the climb}.
     */
    public static List<int[]> spiralPath(int topY, int bottomFloorY) {
        List<int[]> steps = new ArrayList<>();
        int s = 0;
        for (int y = topY; y > bottomFloorY; y--, s++) {
            int[] at = RING[s % RING.length];
            int[] up = RING[(s + RING.length - 1) % RING.length];
            EnumFacing climb = EnumFacing.getFacingFromVector(up[0] - at[0], 0, up[1] - at[1]);
            steps.add(new int[]{at[0], y, at[1], climb.getIndex()});
        }
        return steps;
    }

    /** Shaft (5x5 walls, 1x1 core) from {@code topY} down to the level whose floor is {@code bottomFloorY}. */
    protected void spiral(int cx, int cz, int topY, int bottomFloorY) {
        int shaftBottom = bottomFloorY + CORRIDOR_HEIGHT + 1;
        for (int y = shaftBottom; y < topY; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    w.set(cx + dx, y, cz + dz, ring ? cat.wall(rand) : air);
                }
            }
        }
        for (int y = bottomFloorY + 1; y <= topY; y++) {
            for (int[] r : RING) {
                if (y >= shaftBottom || y == topY) {
                    w.set(cx + r[0], y, cz + r[1], air);
                }
            }
            boolean lamp = (topY - y) % 7 == 3;
            w.set(cx, y, cz, lamp ? Blocks.GLOWSTONE.getDefaultState() : cat.pillar());
        }
        for (int[] step : spiralPath(topY, bottomFloorY)) {
            w.set(cx + step[0], step[1], cz + step[2], stair(EnumFacing.byIndex(step[3])));
        }
    }

    private IBlockState stair(EnumFacing climb) {
        return Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, climb)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM);
    }

    /** Sealed gates over the mouth of a spiral until {@code requiredFloor} is cleared. */
    private void sealSpiralTop(int cx, int cz, int y, int requiredFloor) {
        if (!ConfigCache.get().isLockFloorsUntilCleared() || ModBlocks.SEALED_GATE == null) {
            return;
        }
        for (int[] r : RING) {
            seal(cx + r[0], y, cz + r[1], requiredFloor);
        }
    }

    protected void seal(int x, int y, int z, int requiredFloor) {
        IBlockState replaced = w.get(x, y, z);
        if (w.set(x, y, z, ModBlocks.SEALED_GATE.getDefaultState())) {
            rec.addGate(new BlockPos(x, y, z), requiredFloor, Block.getStateId(replaced));
        }
    }

    // ------------------------------------------------------------------ contents

    protected void spawner(BlockPos pos, int level, RoomTag tag) {
        if (w.get(pos.getX(), pos.getY(), pos.getZ()).getBlock() != Blocks.AIR) {
            return;
        }
        StructurePieces.spawner(w, rec, pos, new SpawnerContext(SpawnerSource.CRYPT, rec.getCitadelId(), -(level + 1),
                FloorData.clampDifficulty(baseTier + 1 + level), tag));
    }

    protected void chest(BlockPos pos, EnumFacing facing, int level) {
        if (w.get(pos.getX(), pos.getY(), pos.getZ()).getBlock() != Blocks.AIR) {
            return;
        }
        StructurePieces.chest(w, rec, pos, facing, lootFor(level), rand.nextLong());
    }

    protected ResourceLocation lootFor(int level) {
        return level == levelCount - 1 && levelCount > 1 ? ModLootTables.CATACOMB_DEEP : ModLootTables.UNDERGROUND_CRYPT;
    }

    protected void vases(int xa, int za, int xb, int zb, int yL, int count) {
        if (ModBlocks.VASE == null) {
            return;
        }
        for (int attempt = 0; attempt < count * 4 && count > 0; attempt++) {
            int x = xa + rand.nextInt(xb - xa + 1);
            int z = za + rand.nextInt(zb - za + 1);
            boolean edge = x == xa || x == xb || z == za || z == zb;
            if (edge && w.get(x, yL + 1, z).getBlock() == Blocks.AIR) {
                BlockVase.Variant variant = rand.nextInt(8) == 0 ? BlockVase.Variant.GILDED
                        : rand.nextBoolean() ? BlockVase.Variant.DARK : BlockVase.Variant.CLAY;
                w.set(x, yL + 1, z, ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT, variant));
                count--;
            }
        }
    }

    protected static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
