package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.palette.PaletteSlot;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds a walled castle around its keep: terraformed courtyard, curtain wall with walkway and
 * crenellations, corner and mid-wall towers (archers on top), twin-towered gatehouse facing
 * south, lined moat with a bridge, and courtyard buildings (barracks, smithy, storehouse,
 * stables, chapel, armoury) plus a well and a garden. The courtyard is floor 0 of the trial:
 * its spawners must all be beaten before the sealed keep door opens.
 */
final class CastleBuilder {

    private enum House { BARRACKS, SMITHY, STOREHOUSE, STABLES, CHAPEL, ARMOURY }

    private static final int CLEAR_HEIGHT = 30;
    private static final int FOOTING_DEPTH = 30;

    private final BlockWriter w;
    private final CastlePlan plan;
    private final Random rand;
    private final CitadelInstance rec;
    private final BlockPalette pal;
    private final int cx;
    private final int cz;
    private final int y0;
    private final int x0;
    private final int x1;
    private final int z0;
    private final int z1;
    private final int wallTop;
    private final List<StructureBoundingBox> occupied = new ArrayList<>();
    private final IBlockState planks = Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.SPRUCE);
    private final IBlockState post = Blocks.LOG2.getDefaultState().withProperty(BlockNewLog.VARIANT, BlockPlanks.EnumType.DARK_OAK)
            .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y);
    private final IBlockState air = Blocks.AIR.getDefaultState();
    private StructureBoundingBox keepBox;
    private int towerTier = 1;

    CastleBuilder(BlockWriter writer, CastlePlan plan, Random random, CitadelInstance record) {
        this.w = writer;
        this.plan = plan;
        this.rand = random;
        this.rec = record;
        this.pal = ModPaletteRegistry.forCitadel(plan.keep.archetype, plan.seed);
        this.cx = plan.centerX();
        this.cz = plan.centerZ();
        this.y0 = plan.baseY();
        this.x0 = cx - plan.halfX;
        this.x1 = cx + plan.halfX;
        this.z0 = cz - plan.halfZ;
        this.z1 = cz + plan.halfZ;
        this.wallTop = y0 + plan.wallHeight;
    }

    void build() {
        plan.keep.buildInto(w, rec, rand);
        keepBox = rec.getTotalBounds();
        occupied.add(new StructureBoundingBox(keepBox.minX - 3, y0, keepBox.minZ - 3, keepBox.maxX + 3, y0, keepBox.maxZ + 3));
        for (FloorData floor : rec.getFloors().values()) {
            if (floor.getIndex() >= 1) {
                floor.setArea(keepBox.minX, keepBox.minZ, keepBox.maxX, keepBox.maxZ);
            }
        }
        terraform();
        buildPath();
        buildGround();
        buildMoat();
        buildWalls();
        buildGatehouse();
        buildCornerAndMidTowers();
        buildHouses();
        buildYardFeatures();
        sealKeepDoor();
        FloorData courtyard = rec.addFloor(0, y0, wallTop + 10, 1, ConfigCache.get().rollWaveCount(rand), false);
        courtyard.setArea(x0, z0, x1, z1).setCourtyard(true);
    }

    // ------------------------------------------------------------------ ground

    /** Removes relief and vegetation above the courtyard, walls, berm and moat (at write time). */
    private void terraform() {
        int r = CastlePlan.MOAT_REACH;
        for (int x = x0 - r; x <= x1 + r; x++) {
            for (int z = z0 - r; z <= z1 + r; z++) {
                w.clearColumn(x, z, y0 + 1, y0 + CLEAR_HEIGHT);
            }
        }
    }

    private void buildPath() {
        IBlockState path = pal.get(PaletteSlot.FLOOR);
        for (int z = keepBox.maxZ + 1; z <= z1 - 3; z++) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                if (!w.isPlanned(x, y0, z)) {
                    w.set(x, y0, z, rand.nextInt(6) == 0 ? Blocks.GRAVEL.getDefaultState() : path);
                    w.fillDown(x, z, y0 - 1, Blocks.COBBLESTONE.getDefaultState(), FOOTING_DEPTH);
                }
            }
        }
        occupied.add(new StructureBoundingBox(cx - 2, y0, keepBox.maxZ, cx + 2, y0, z1));
    }

    private void buildGround() {
        int r = 2;
        for (int x = x0 - r; x <= x1 + r; x++) {
            for (int z = z0 - r; z <= z1 + r; z++) {
                if (w.isPlanned(x, y0, z)) {
                    continue;
                }
                w.set(x, y0, z, Blocks.GRASS.getDefaultState());
                w.fillDown(x, z, y0 - 1, Blocks.DIRT.getDefaultState(), FOOTING_DEPTH);
            }
        }
    }

    /** Lined moat: inner lining, four blocks of water, outer lining; a plank bridge at the gate. */
    private void buildMoat() {
        IBlockState lining = pal.get(PaletteSlot.WALL);
        IBlockState water = Blocks.WATER.getDefaultState();
        int r = CastlePlan.MOAT_REACH;
        for (int x = x0 - r; x <= x1 + r; x++) {
            for (int z = z0 - r; z <= z1 + r; z++) {
                int d = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
                if (d < 3) {
                    continue;
                }
                boolean bridge = z > z1 && x >= cx - 2 && x <= cx + 2;
                if (d == 3 || d == r) {
                    for (int y = y0 - 4; y <= y0; y++) {
                        w.set(x, y, z, lining);
                    }
                } else {
                    w.set(x, y0 - 4, z, lining);
                    for (int y = y0 - 3; y <= y0 - 1; y++) {
                        w.set(x, y, z, water);
                    }
                    w.set(x, y0, z, bridge ? planks : air);
                }
                w.fillDown(x, z, y0 - 5, lining, FOOTING_DEPTH);
                if (bridge && (x == cx - 2 || x == cx + 2)) {
                    w.set(x, y0 + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ walls

    private void buildWalls() {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int d = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
                if (d >= 3) {
                    continue;
                }
                boolean northSouth = Math.min(z - z0, z1 - z) == d;
                int along = northSouth ? x - x0 : z - z0;
                w.fillDown(x, z, y0 - 1, pal.get(PaletteSlot.WALL, rand), FOOTING_DEPTH);
                for (int y = y0; y < wallTop; y++) {
                    w.set(x, y, z, d == 0 && along % 8 == 0 ? pal.get(PaletteSlot.PILLAR) : pal.get(PaletteSlot.WALL, rand));
                }
                w.set(x, wallTop, z, pal.get(PaletteSlot.FLOOR, rand));
                if (d == 0 && along % 2 == 0) {
                    w.set(x, wallTop + 1, z, pal.get(PaletteSlot.WALL, rand));
                }
                if (d == 2 && along % 12 == 6) {
                    w.set(x, wallTop + 1, z, pal.get(PaletteSlot.LIGHT));
                }
            }
        }
        rec.addPart(new StructureBoundingBox(x0, y0 - FOOTING_DEPTH, z0, x1, wallTop + 1, z0 + 2));
        rec.addPart(new StructureBoundingBox(x0, y0 - FOOTING_DEPTH, z1 - 2, x1, wallTop + 1, z1));
        rec.addPart(new StructureBoundingBox(x0, y0 - FOOTING_DEPTH, z0, x0 + 2, wallTop + 1, z1));
        rec.addPart(new StructureBoundingBox(x1 - 2, y0 - FOOTING_DEPTH, z0, x1, wallTop + 1, z1));
    }

    private void buildGatehouse() {
        // Passage through the south wall, with a half-raised portcullis.
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = z1 - 2; z <= z1; z++) {
                w.set(x, y0, z, pal.get(PaletteSlot.FLOOR, rand));
                for (int y = y0 + 1; y <= y0 + 5; y++) {
                    w.set(x, y, z, air);
                }
                w.set(x, y0 + 6, z, pal.get(PaletteSlot.ACCENT, rand));
            }
            w.set(x, y0 + 5, z1 - 1, Blocks.IRON_BARS.getDefaultState());
        }
        int height = plan.wallHeight + 7;
        wallTower(cx - 9, z1 - 4, 7, height, EnumFacing.NORTH, new EnumFacing[]{EnumFacing.WEST, EnumFacing.EAST}, true);
        wallTower(cx + 3, z1 - 4, 7, height, EnumFacing.NORTH, new EnumFacing[]{EnumFacing.WEST, EnumFacing.EAST}, true);
        // Gate hall over the passage between the two towers.
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = z1 - 4; z <= z1 + 2; z++) {
                boolean edge = z == z1 - 4 || z == z1 + 2;
                w.set(x, wallTop, z, pal.get(PaletteSlot.FLOOR, rand));
                for (int y = wallTop + 1; y <= wallTop + 4; y++) {
                    w.set(x, y, z, edge ? (y == wallTop + 2 && x % 2 == 0 ? Blocks.IRON_BARS.getDefaultState()
                            : pal.get(PaletteSlot.WALL, rand)) : air);
                }
                w.set(x, wallTop + 5, z, pal.get(PaletteSlot.WALL, rand));
            }
        }
        w.set(cx, wallTop + 4, z1 - 1, pal.get(PaletteSlot.LIGHT));
        StructurePieces.chest(w, rec, new BlockPos(cx, wallTop + 1, z1 - 3), EnumFacing.SOUTH,
                ModLootTables.forFloor(2), rand.nextLong());
        rec.addPart(new StructureBoundingBox(cx - 9, y0 - FOOTING_DEPTH, z1 - 4, cx + 9, wallTop + 8, z1 + 2));
    }

    private void buildCornerAndMidTowers() {
        int height = plan.wallHeight + 8;
        towerTier = 2;
        wallTower(x0 - 2, z0 - 2, 9, height, EnumFacing.SOUTH, new EnumFacing[]{EnumFacing.EAST, EnumFacing.SOUTH}, true);
        wallTower(x1 - 6, z0 - 2, 9, height, EnumFacing.SOUTH, new EnumFacing[]{EnumFacing.WEST, EnumFacing.SOUTH}, true);
        wallTower(x0 - 2, z1 - 6, 9, height, EnumFacing.NORTH, new EnumFacing[]{EnumFacing.EAST, EnumFacing.NORTH}, true);
        wallTower(x1 - 6, z1 - 6, 9, height, EnumFacing.NORTH, new EnumFacing[]{EnumFacing.WEST, EnumFacing.NORTH}, true);
        towerTier = 1;
        int midHeight = plan.wallHeight + 5;
        wallTower(cx - 3, z0 - 2, 7, midHeight, EnumFacing.SOUTH, new EnumFacing[]{EnumFacing.WEST, EnumFacing.EAST}, false);
        wallTower(x0 - 2, cz - 3, 7, midHeight, EnumFacing.EAST, new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH}, false);
        wallTower(x1 - 4, cz - 3, 7, midHeight, EnumFacing.WEST, new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH}, false);
    }

    /**
     * A hollow wall tower: ground room opening to the courtyard, a floor at walkway level with doors
     * onto the wall, a crenellated top; a ladder links the three. Archer towers get a spawner on top.
     */
    private void wallTower(int tx, int tz, int size, int height, EnumFacing yard, EnumFacing[] walkway, boolean archers) {
        int max = size - 1;
        int top = y0 + height;
        int mid = size / 2;
        for (int dx = 0; dx <= max; dx++) {
            for (int dz = 0; dz <= max; dz++) {
                int x = tx + dx;
                int z = tz + dz;
                boolean edge = dx == 0 || dz == 0 || dx == max || dz == max;
                boolean corner = (dx == 0 || dx == max) && (dz == 0 || dz == max);
                w.fillDown(x, z, y0 - 1, pal.get(PaletteSlot.WALL, rand), FOOTING_DEPTH);
                w.set(x, y0, z, pal.get(PaletteSlot.FLOOR, rand));
                for (int y = y0 + 1; y < top; y++) {
                    if (edge) {
                        boolean slit = (dx == mid || dz == mid) && (y - y0) % 5 == 3 && !corner;
                        w.set(x, y, z, corner ? pal.get(PaletteSlot.PILLAR) : slit ? Blocks.IRON_BARS.getDefaultState()
                                : pal.get(PaletteSlot.WALL, rand));
                    } else {
                        w.set(x, y, z, y == wallTop ? pal.get(PaletteSlot.FLOOR, rand) : air);
                    }
                }
                w.set(x, top, z, edge ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                if (edge && (dx + dz) % 2 == 0) {
                    w.set(x, top + 1, z, pal.get(PaletteSlot.WALL, rand));
                }
            }
        }
        int ccx = tx + mid;
        int ccz = tz + mid;
        // Ground door to the courtyard.
        int gx = ccx + yard.getXOffset() * mid;
        int gz = ccz + yard.getZOffset() * mid;
        w.set(gx, y0 + 1, gz, air);
        w.set(gx, y0 + 2, gz, air);
        // Doors onto the wall walkway.
        for (EnumFacing side : walkway) {
            int dxw = ccx + side.getXOffset() * mid;
            int dzw = ccz + side.getZOffset() * mid;
            w.set(dxw, wallTop + 1, dzw, air);
            w.set(dxw, wallTop + 2, dzw, air);
        }
        // Ladder against the wall opposite the courtyard door, through both floors.
        EnumFacing back = yard.getOpposite();
        int lx = ccx + back.getXOffset() * (mid - 1);
        int lz = ccz + back.getZOffset() * (mid - 1);
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, yard);
        for (int y = y0 + 1; y <= top; y++) {
            w.set(lx, y, lz, ladder);
        }
        w.set(ccx, top - 1, ccz, pal.get(PaletteSlot.LIGHT));
        w.set(ccx, wallTop - 1, ccz, pal.get(PaletteSlot.LIGHT));
        // Ground room: a vase and sometimes a chest.
        placeVase(ccx + yard.getZOffset(), y0 + 1, ccz + yard.getXOffset());
        if (rand.nextBoolean()) {
            StructurePieces.chest(w, rec, new BlockPos(ccx - yard.getZOffset(), y0 + 1, ccz - yard.getXOffset()), yard,
                    ModLootTables.forFloor(towerTier), rand.nextLong());
        }
        if (archers) {
            spawner(new BlockPos(ccx + yard.getXOffset(), top + 1, ccz + yard.getZOffset()), RoomTag.GUARD_ROOM);
        }
        StructureBoundingBox box = new StructureBoundingBox(tx, y0 - FOOTING_DEPTH, tz, tx + max, top + 1, tz + max);
        rec.addPart(box);
        occupied.add(new StructureBoundingBox(tx - 1, y0, tz - 1, tx + max + 1, y0, tz + max + 1));
    }

    // ------------------------------------------------------------------ courtyard buildings

    private void buildHouses() {
        List<House> pool = new ArrayList<>(Arrays.asList(House.values()));
        pool.addAll(Arrays.asList(House.BARRACKS, House.STOREHOUSE, House.SMITHY, House.STABLES));
        Collections.shuffle(pool, rand);
        int[] next = {0};
        // North, west and east walls along their whole length, the south wall beside the gatehouse.
        row(EnumFacing.SOUTH, z0 + 3, x0 + 8, x1 - 8, pool, next);
        row(EnumFacing.EAST, x0 + 3, z0 + 8, z1 - 8, pool, next);
        row(EnumFacing.WEST, x1 - 3, z0 + 8, z1 - 8, pool, next);
        row(EnumFacing.NORTH, z1 - 3, x0 + 8, x1 - 8, pool, next);
    }

    /**
     * Places buildings against one wall. {@code inward} points from the wall into the courtyard,
     * {@code face} is the courtyard row next to the wall, [{@code from}, {@code to}] the free span.
     */
    private void row(EnumFacing inward, int face, int from, int to, List<House> pool, int[] next) {
        int a = from;
        while (a <= to) {
            int length = 11 + rand.nextInt(4) * 2;
            int depth = 9 + rand.nextInt(2) * 2;
            if (a + length - 1 > to) {
                return;
            }
            StructureBoundingBox box = houseBox(inward, face, a, length, depth);
            if (isFree(box)) {
                House house = pool.get(next[0]++ % pool.size());
                buildHouse(box, inward, house);
                occupied.add(new StructureBoundingBox(box.minX - 1, y0, box.minZ - 1, box.maxX + 1, y0, box.maxZ + 1));
                a += length + 2 + rand.nextInt(3);
            } else {
                a += 3;
            }
        }
    }

    private static StructureBoundingBox houseBox(EnumFacing inward, int face, int a, int length, int depth) {
        int b = face + inward.getXOffset() * (depth - 1) + inward.getZOffset() * (depth - 1);
        int minDepth = Math.min(face, b);
        int maxDepth = Math.max(face, b);
        if (inward.getAxis() == EnumFacing.Axis.Z) {
            return new StructureBoundingBox(a, 0, minDepth, a + length - 1, 0, maxDepth);
        }
        return new StructureBoundingBox(minDepth, 0, a, maxDepth, 0, a + length - 1);
    }

    private boolean isFree(StructureBoundingBox box) {
        for (StructureBoundingBox other : occupied) {
            if (other.intersectsWith(box.minX, box.minZ, box.maxX, box.maxZ)) {
                return false;
            }
        }
        return box.minX > x0 + 2 && box.maxX < x1 - 2 && box.minZ > z0 + 2 && box.maxZ < z1 - 2;
    }

    /** Timber-framed house on a stone course, gable roof, door towards the courtyard. */
    private void buildHouse(StructureBoundingBox box, EnumFacing inward, House house) {
        int minX = box.minX;
        int maxX = box.maxX;
        int minZ = box.minZ;
        int maxZ = box.maxZ;
        int wallH = 5;
        boolean alongX = inward.getAxis() == EnumFacing.Axis.Z;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                w.fillDown(x, z, y0 - 1, Blocks.COBBLESTONE.getDefaultState(), FOOTING_DEPTH);
                w.set(x, y0, z, edge ? Blocks.COBBLESTONE.getDefaultState() : planks);
                for (int y = y0 + 1; y <= y0 + wallH; y++) {
                    if (!edge) {
                        w.set(x, y, z, air);
                        continue;
                    }
                    int along = alongX ? x - minX : z - minZ;
                    boolean longSide = alongX ? (z == minZ || z == maxZ) : (x == minX || x == maxX);
                    if (corner || (longSide && along % 4 == 0)) {
                        w.set(x, y, z, post);
                    } else if (y == y0 + 1) {
                        w.set(x, y, z, pal.get(PaletteSlot.WALL, rand));
                    } else if (longSide && along % 4 == 2 && (y == y0 + 2 || y == y0 + 3)) {
                        w.set(x, y, z, Blocks.GLASS_PANE.getDefaultState());
                    } else {
                        w.set(x, y, z, planks);
                    }
                }
            }
        }
        // Door in the middle of the courtyard-facing wall.
        int doorA = alongX ? (minX + maxX) / 2 : (minZ + maxZ) / 2;
        int frontRow = inward == EnumFacing.SOUTH ? maxZ : inward == EnumFacing.NORTH ? minZ
                : inward == EnumFacing.EAST ? maxX : minX;
        for (int y = y0 + 1; y <= y0 + 2; y++) {
            if (alongX) {
                w.set(doorA, y, frontRow, air);
            } else {
                w.set(frontRow, y, doorA, air);
            }
        }
        buildGableRoof(minX, minZ, maxX, maxZ, y0 + wallH + 1, alongX);
        int ix0 = minX + 1;
        int iz0 = minZ + 1;
        int ix1 = maxX - 1;
        int iz1 = maxZ - 1;
        w.set((ix0 + ix1) / 2, y0, (iz0 + iz1) / 2, pal.get(PaletteSlot.LIGHT));
        furnishHouse(house, ix0, iz0, ix1, iz1, inward, alongX, doorA, frontRow);
        rec.addPart(new StructureBoundingBox(minX, y0 - FOOTING_DEPTH, minZ, maxX, y0 + wallH + 8, maxZ));
    }

    private void buildGableRoof(int minX, int minZ, int maxX, int maxZ, int baseY, boolean ridgeAlongX) {
        int span = ridgeAlongX ? maxZ - minZ : maxX - minX;
        for (int s = 0; s <= span / 2; s++) {
            int y = baseY + s;
            for (int a = (ridgeAlongX ? minX : minZ) - 1; a <= (ridgeAlongX ? maxX : maxZ) + 1; a++) {
                int low = (ridgeAlongX ? minZ : minX) - 1 + s;
                int high = (ridgeAlongX ? maxZ : maxX) + 1 - s;
                if (low > high) {
                    continue;
                }
                if (low == high) {
                    setRoof(ridgeAlongX, a, y, low, planks);
                    continue;
                }
                setRoof(ridgeAlongX, a, y, low, Blocks.SPRUCE_STAIRS.getDefaultState()
                        .withProperty(BlockStairs.FACING, ridgeAlongX ? EnumFacing.SOUTH : EnumFacing.EAST));
                setRoof(ridgeAlongX, a, y, high, Blocks.SPRUCE_STAIRS.getDefaultState()
                        .withProperty(BlockStairs.FACING, ridgeAlongX ? EnumFacing.NORTH : EnumFacing.WEST));
                boolean gable = a == (ridgeAlongX ? minX : minZ) || a == (ridgeAlongX ? maxX : maxZ);
                if (gable) {
                    for (int b = low + 1; b < high; b++) {
                        setRoof(ridgeAlongX, a, y, b, planks);
                    }
                }
            }
        }
    }

    private void setRoof(boolean ridgeAlongX, int a, int y, int b, IBlockState state) {
        if (ridgeAlongX) {
            w.set(a, y, b, state);
        } else {
            w.set(b, y, a, state);
        }
    }

    private void furnishHouse(House house, int ix0, int iz0, int ix1, int iz1, EnumFacing inward, boolean alongX,
                              int doorA, int frontRow) {
        RoomFurnisher furnisher = new RoomFurnisher(site(ix0, iz0, ix1, iz1), rand, pal,
                CitadelBuilder.vaseVariant(plan.keep.archetype));
        // Keep the doorway free inside.
        int insideX = alongX ? doorA : frontRow - inward.getXOffset();
        int insideZ = alongX ? frontRow - inward.getZOffset() : doorA;
        w.set(insideX, y0 + 1, insideZ, air);
        reserve.add(Blueprint.pack(insideX, y0 + 1, insideZ));
        RoomTag tag;
        switch (house) {
            case BARRACKS:
                tag = RoomTag.BARRACKS;
                furnisher.furnish(RoomTag.BEDROOM, ix0, iz0, ix1, iz1, y0);
                break;
            case SMITHY:
                tag = RoomTag.SMELTERY;
                furnisher.furnish(RoomTag.SMELTERY, ix0, iz0, ix1, iz1, y0);
                break;
            case STOREHOUSE:
                tag = RoomTag.PANTRY;
                furnisher.furnish(RoomTag.PANTRY, ix0, iz0, ix1, iz1, y0);
                break;
            case STABLES:
                tag = RoomTag.GUARD_ROOM;
                stables(ix0, iz0, ix1, iz1, alongX, inward);
                break;
            case CHAPEL:
                tag = RoomTag.LIBRARY;
                chapel(ix0, iz0, ix1, iz1, alongX, inward);
                break;
            default:
                tag = RoomTag.TREASURY;
                armoury(ix0, iz0, ix1, iz1);
                break;
        }
        rec.addRoom(new StructureBoundingBox(ix0, y0, iz0, ix1, y0 + 5, iz1), tag, 0);
        BlockPos centre = new BlockPos((ix0 + ix1) / 2, y0 + 1, (iz0 + iz1) / 2);
        if (w.get(centre.getX(), centre.getY(), centre.getZ()).getMaterial() != Material.AIR) {
            centre = new BlockPos(insideX - inward.getOpposite().getXOffset() * 2, y0 + 1,
                    insideZ - inward.getOpposite().getZOffset() * 2);
        }
        spawner(centre, tag);
        furnisher.vases(ix0, iz0, ix1, iz1, y0, 1 + rand.nextInt(2));
        furnisher.abandon(ix0, iz0, ix1, iz1, y0, 5);
        reserve.clear();
    }

    private void stables(int ix0, int iz0, int ix1, int iz1, boolean alongX, EnumFacing inward) {
        // Stalls against the back wall: fence partitions, hay, a water trough.
        int back = alongX ? (inward == EnumFacing.SOUTH ? iz0 : iz1) : (inward == EnumFacing.EAST ? ix0 : ix1);
        int stallDepth = 3;
        for (int a = alongX ? ix0 : iz0; a <= (alongX ? ix1 : iz1); a++) {
            for (int d = 0; d < stallDepth; d++) {
                int x = alongX ? a : back + inward.getXOffset() * d;
                int z = alongX ? back + inward.getZOffset() * d : a;
                boolean partition = ((a - (alongX ? ix0 : iz0)) % 4 == 0);
                if (partition) {
                    putIfFree(x, y0 + 1, z, Blocks.OAK_FENCE.getDefaultState());
                } else if (d == 0 && rand.nextInt(3) > 0) {
                    putIfFree(x, y0 + 1, z, Blocks.HAY_BLOCK.getDefaultState());
                } else if (d == stallDepth - 1 && (a - (alongX ? ix0 : iz0)) % 4 == 2) {
                    putIfFree(x, y0 + 1, z, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 3));
                }
            }
        }
        BlockPos chest = new BlockPos(alongX ? ix1 : back + inward.getXOffset() * stallDepth, y0 + 1,
                alongX ? back + inward.getZOffset() * stallDepth : iz1);
        if (w.get(chest.getX(), chest.getY(), chest.getZ()).getMaterial() == Material.AIR) {
            StructurePieces.chest(w, rec, chest, inward, ModLootTables.PANTRY, rand.nextLong());
        }
    }

    private void chapel(int ix0, int iz0, int ix1, int iz1, boolean alongX, EnumFacing inward) {
        // Altar at the back, pews facing it.
        int back = alongX ? (inward == EnumFacing.SOUTH ? iz0 : iz1) : (inward == EnumFacing.EAST ? ix0 : ix1);
        int midA = alongX ? (ix0 + ix1) / 2 : (iz0 + iz1) / 2;
        for (int a = midA - 1; a <= midA + 1; a++) {
            int x = alongX ? a : back;
            int z = alongX ? back : a;
            putIfFree(x, y0 + 1, z, pal.get(PaletteSlot.ACCENT, rand));
        }
        int ax = alongX ? midA : back;
        int az = alongX ? back : midA;
        putIfFree(ax, y0 + 2, az, pal.get(PaletteSlot.LIGHT));
        StructurePieces.chest(w, rec, new BlockPos(alongX ? midA + 2 : back, y0 + 1, alongX ? back : midA + 2),
                inward, ModLootTables.BEDROOM, rand.nextLong());
        EnumFacing towardsAltar = inward.getOpposite();
        for (int d = 2; d <= 6; d += 2) {
            for (int a = (alongX ? ix0 : iz0) + 1; a <= (alongX ? ix1 : iz1) - 1; a++) {
                if (Math.abs(a - midA) <= 0) {
                    continue; // aisle
                }
                int x = alongX ? a : back + inward.getXOffset() * d;
                int z = alongX ? back + inward.getZOffset() * d : a;
                putIfFree(x, y0 + 1, z, Blocks.SPRUCE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, towardsAltar));
            }
        }
        for (int a = alongX ? ix0 : iz0; a <= (alongX ? ix1 : iz1); a += 3) {
            int x = alongX ? a : back;
            int z = alongX ? back : a;
            if (Math.abs(a - midA) > 2) {
                putIfFree(x, y0 + 1, z, Blocks.BOOKSHELF.getDefaultState());
                putIfFree(x, y0 + 2, z, Blocks.BOOKSHELF.getDefaultState());
            }
        }
    }

    private void armoury(int ix0, int iz0, int ix1, int iz1) {
        StructurePieces.chest(w, rec, new BlockPos(ix0, y0 + 1, iz0), EnumFacing.SOUTH, ModLootTables.forFloor(3), rand.nextLong());
        StructurePieces.chest(w, rec, new BlockPos(ix1, y0 + 1, iz1), EnumFacing.NORTH, ModLootTables.forFloor(3), rand.nextLong());
        putIfFree(ix1, y0 + 1, iz0, Blocks.ANVIL.getDefaultState());
        putIfFree(ix0, y0 + 1, iz1, Blocks.CRAFTING_TABLE.getDefaultState());
        IBlockState rack = ModPaletteRegistry.resolveDecor("cfm:*weapon*", "minecraft:bookshelf");
        for (int x = ix0 + 2; x <= ix1 - 2; x += 3) {
            putIfFree(x, y0 + 1, iz0, rack);
        }
    }

    // ------------------------------------------------------------------ yard

    private void buildYardFeatures() {
        // Well west of the path, garden east of it, guards in the open yard.
        int wellX = cx - 8;
        int wellZ = (keepBox.maxZ + z1) / 2;
        StructureBoundingBox well = new StructureBoundingBox(wellX - 2, y0, wellZ - 2, wellX + 2, y0, wellZ + 2);
        if (isFree(well)) {
            buildWell(wellX, wellZ);
            occupied.add(new StructureBoundingBox(well.minX - 1, y0, well.minZ - 1, well.maxX + 1, y0, well.maxZ + 1));
        }
        StructureBoundingBox garden = new StructureBoundingBox(cx + 5, y0, wellZ - 3, cx + 13, y0, wellZ + 3);
        if (isFree(garden)) {
            buildGarden(garden);
            occupied.add(garden);
        }
        int guards = 2 + rand.nextInt(2);
        for (int attempt = 0; attempt < 40 && guards > 0; attempt++) {
            int x = x0 + 5 + rand.nextInt(x1 - x0 - 9);
            int z = z0 + 5 + rand.nextInt(z1 - z0 - 9);
            StructureBoundingBox spot = new StructureBoundingBox(x - 1, y0, z - 1, x + 1, y0, z + 1);
            if (isFree(spot)) {
                w.set(x, y0, z, pal.get(PaletteSlot.ACCENT, rand));
                spawner(new BlockPos(x, y0 + 1, z), RoomTag.GUARD_ROOM);
                occupied.add(spot);
                guards--;
            }
        }
    }

    private void buildWell(int wx, int wz) {
        for (int x = wx - 2; x <= wx + 2; x++) {
            for (int z = wz - 2; z <= wz + 2; z++) {
                boolean rim = Math.abs(x - wx) == 2 || Math.abs(z - wz) == 2;
                if (rim) {
                    w.set(x, y0 + 1, z, Blocks.COBBLESTONE.getDefaultState());
                } else {
                    for (int y = y0 - 5; y <= y0; y++) {
                        w.set(x, y, z, Blocks.WATER.getDefaultState());
                    }
                    w.set(x, y0 - 6, z, Blocks.COBBLESTONE.getDefaultState());
                }
            }
        }
        for (int[] c : new int[][]{{wx - 2, wz - 2}, {wx + 2, wz - 2}, {wx - 2, wz + 2}, {wx + 2, wz + 2}}) {
            w.set(c[0], y0 + 2, c[1], Blocks.OAK_FENCE.getDefaultState());
            w.set(c[0], y0 + 3, c[1], Blocks.OAK_FENCE.getDefaultState());
        }
        for (int x = wx - 2; x <= wx + 2; x++) {
            for (int z = wz - 2; z <= wz + 2; z++) {
                w.set(x, y0 + 4, z, Blocks.WOODEN_SLAB.getDefaultState());
            }
        }
    }

    private void buildGarden(StructureBoundingBox box) {
        IBlockState[] crops = {Blocks.WHEAT.getDefaultState(), Blocks.CARROTS.getDefaultState(), Blocks.POTATOES.getDefaultState()};
        int midX = (box.minX + box.maxX) / 2;
        for (int x = box.minX; x <= box.maxX; x++) {
            for (int z = box.minZ; z <= box.maxZ; z++) {
                boolean edge = x == box.minX || x == box.maxX || z == box.minZ || z == box.maxZ;
                if (edge) {
                    w.set(x, y0, z, Blocks.LOG.getDefaultState());
                    continue;
                }
                if (x == midX && z == (box.minZ + box.maxZ) / 2) {
                    w.set(x, y0, z, Blocks.WATER.getDefaultState());
                    continue;
                }
                w.set(x, y0, z, Blocks.FARMLAND.getDefaultState());
                IBlockState crop = crops[(x - box.minX) % crops.length];
                w.set(x, y0 + 1, z, crop.withProperty(BlockCrops.AGE, rand.nextInt(8)));
            }
        }
    }

    private void sealKeepDoor() {
        if (!ConfigCache.get().isLockFloorsUntilCleared()) {
            return;
        }
        int dz = plan.keep.maxZ();
        int dx = plan.keep.centerX();
        for (int x = dx - 1; x <= dx + 1; x++) {
            for (int y = y0 + 1; y <= y0 + 3; y++) {
                IBlockState replaced = w.get(x, y, dz);
                if (w.set(x, y, dz, ModBlocks.SEALED_GATE.getDefaultState())) {
                    rec.addGate(new BlockPos(x, y, dz), 0, Block.getStateId(replaced));
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet reserve = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    private void spawner(BlockPos pos, RoomTag tag) {
        StructurePieces.spawner(w, rec, pos, new SpawnerContext(SpawnerSource.CITADEL, rec.getCitadelId(), 0,
                1 + (rand.nextInt(3) == 0 ? 1 : 0), tag));
    }

    private void placeVase(int x, int y, int z) {
        if (w.get(x, y, z).getMaterial() == Material.AIR) {
            BlockVase.Variant variant = rand.nextInt(8) == 0 ? BlockVase.Variant.GILDED : CitadelBuilder.vaseVariant(plan.keep.archetype);
            w.set(x, y, z, ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT, variant));
        }
    }

    private void putIfFree(int x, int y, int z, IBlockState state) {
        if (state != null && w.get(x, y, z).getMaterial() == Material.AIR && !reserve.contains(Blueprint.pack(x, y, z))) {
            w.set(x, y, z, state);
        }
    }

    private RoomFurnisher.Site site(int minX, int minZ, int maxX, int maxZ) {
        return new RoomFurnisher.Site() {
            @Override
            public boolean free(int x, int y, int z) {
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ && w.get(x, y, z).getMaterial() == Material.AIR
                        && !reserve.contains(Blueprint.pack(x, y, z));
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
                if (free(pos.getX(), pos.getY(), pos.getZ())) {
                    StructurePieces.chest(w, rec, pos, facing, lootTable, rand.nextLong());
                }
            }

            @Override
            public void afterPlace(BlockPos pos, Blueprint.TileAction action) {
                w.afterPlace(pos, action);
            }
        };
    }
}
