package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.PaletteSlot;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.material.Material;
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
 * Wings that branch out of a tower at different heights so it no longer reads as a pillar.
 * A wing is 1-3 floors tall, shares the tower's floor heights (its rooms, spawners and chests
 * belong to the tower floor at the same height, so sealed stairwells still wait for them) and
 * carries annexes: a corner turret with a ladder to its lookout (sometimes with a balcony on
 * top of that) and a small roof chapel. Elevated wings stand on piers down to the ground.
 */
final class TowerWings {

    /** What the tower exposes to its wings. */
    interface Tower {
        boolean reserved(int floor, int x, int z);

        void reserve(int floor, int minX, int minZ, int maxX, int maxZ);

        int difficulty(int floor);

        int tier(int floor);

        int recordFloor(int floor);
    }

    /** Wings up to this floor stand on piers to the ground; higher ones hang on corbels. */
    private static final int MAX_PIER_FLOOR = 3;

    private static final RoomTag[] WING_ROOMS = {
            RoomTag.BEDROOM, RoomTag.LIBRARY, RoomTag.PANTRY, RoomTag.GUARD_ROOM, RoomTag.SMELTERY, RoomTag.TREASURY
    };

    private final BlockWriter w;
    private final CitadelPlan p;
    private final BlockPalette pal;
    private final Random rand;
    private final CitadelInstance rec;
    private final SpawnerSource source;
    private final Tower tower;
    private final BlockVase.Variant vase;
    private final IBlockState window;
    private final List<StructureBoundingBox> taken = new ArrayList<>();

    TowerWings(BlockWriter writer, CitadelPlan plan, BlockPalette palette, Random random, CitadelInstance record,
               SpawnerSource source, Tower tower, BlockVase.Variant vase, IBlockState window) {
        this.w = writer;
        this.p = plan;
        this.pal = palette;
        this.rand = random;
        this.rec = record;
        this.source = source;
        this.tower = tower;
        this.vase = vase;
        this.window = window;
    }

    /** Grows wings in bands of 2-3 floors below the boss arena. */
    void build() {
        double chance = ConfigCache.get().getTowerWingChance();
        int top = p.topFloorIndex();
        int f = 0;
        while (f < top) {
            int band = 2 + rand.nextInt(2);
            int wings = rand.nextDouble() < chance ? 1 + (rand.nextInt(3) == 0 ? 1 : 0) : 0;
            for (int i = 0; i < wings; i++) {
                int height = 1 + rand.nextInt(Math.min(3, top - f));
                EnumFacing side = EnumFacing.byHorizontalIndex(rand.nextInt(4));
                if (f == 0 && side == EnumFacing.SOUTH) {
                    side = EnumFacing.NORTH; // the entrance stays free
                }
                tryWing(f, height, side);
            }
            f += band;
        }
    }

    // ------------------------------------------------------------------ wing

    private void tryWing(int floor, int height, EnumFacing side) {
        int length = 9 + rand.nextInt(4) * 2;
        int width = 9 + rand.nextInt(3) * 2;
        int sideMin = (side.getAxis() == EnumFacing.Axis.X ? p.minZ : p.minX) + 3;
        int sideMax = (side.getAxis() == EnumFacing.Axis.X ? p.maxZ() : p.maxX()) - 3;
        if (sideMax - sideMin + 1 < width) {
            return;
        }
        int start = sideMin + rand.nextInt(sideMax - sideMin - width + 2);
        // Box of the wing (walls included), excluding the shared tower wall.
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        switch (side) {
            case NORTH:
                minX = start; maxX = start + width - 1; maxZ = p.minZ - 1; minZ = maxZ - length + 1;
                break;
            case SOUTH:
                minX = start; maxX = start + width - 1; minZ = p.maxZ() + 1; maxZ = minZ + length - 1;
                break;
            case WEST:
                minZ = start; maxZ = start + width - 1; maxX = p.minX - 1; minX = maxX - length + 1;
                break;
            default:
                minZ = start; maxZ = start + width - 1; minX = p.maxX() + 1; maxX = minX + length - 1;
                break;
        }
        int y0 = p.floorY(floor);
        int roofY = p.floorY(floor + height);
        StructureBoundingBox box = new StructureBoundingBox(minX, y0, minZ, maxX, roofY + 2, maxZ);
        for (StructureBoundingBox other : taken) {
            if (other.intersectsWith(box.minX - 1, box.minZ - 1, box.maxX + 1, box.maxZ + 1)
                    && other.minY <= box.maxY && other.maxY >= box.minY) {
                return;
            }
        }
        // Every wing floor needs a doorway into the tower that does not land on a staircase.
        int[] doors = new int[height];
        for (int g = 0; g < height; g++) {
            doors[g] = findDoor(floor + g, side, start, width);
            if (doors[g] == Integer.MIN_VALUE) {
                return;
            }
        }
        taken.add(box);
        for (int g = 0; g < height; g++) {
            buildWingFloor(floor + g, side, minX, minZ, maxX, maxZ);
        }
        buildWingRoof(minX, minZ, maxX, maxZ, roofY);
        for (int g = 0; g < height; g++) {
            cutDoor(floor + g, side, doors[g]);
        }
        if (floor > 0 && floor <= MAX_PIER_FLOOR) {
            buildPiers(minX, minZ, maxX, maxZ, y0 - 1);
        } else if (floor > MAX_PIER_FLOOR) {
            buildCorbel(side, minX, minZ, maxX, maxZ, y0 - 1);
        } else {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    w.fillDown(x, z, y0 - 1, pal.get(PaletteSlot.WALL, rand), CitadelBuilder.FOUNDATION_MAX_DEPTH);
                }
            }
        }
        int partBottom = floor == 0 ? y0 - 1 : floor <= MAX_PIER_FLOOR ? BlockWriter.MIN_Y : y0 - length / 2 - 1;
        rec.addPart(new StructureBoundingBox(minX, partBottom, minZ, maxX, roofY + 2, maxZ));
        for (int g = 0; g < height; g++) {
            furnishWingFloor(floor + g, minX, minZ, maxX, maxZ);
        }
        if (rand.nextFloat() < 0.65F) {
            buildTurret(side, floor, height, minX, minZ, maxX, maxZ, roofY);
        }
        if (rand.nextFloat() < 0.4F) {
            buildRoofChapel(floor + height - 1, minX, minZ, maxX, maxZ, roofY);
        }
    }

    /** @return the coordinate along the tower side of a 2-wide doorway, or MIN_VALUE if none fits. */
    private int findDoor(int floor, EnumFacing side, int start, int width) {
        int centre = start + width / 2;
        for (int offset = 0; offset <= width / 2 - 2; offset++) {
            for (int sign : new int[]{1, -1}) {
                int a = centre + sign * offset;
                if (doorFree(floor, side, a) && doorFree(floor, side, a + 1)) {
                    return a;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The cell just inside the tower wall must be open floor, not a staircase or an inner wall. */
    private boolean doorFree(int floor, EnumFacing side, int along) {
        int[] inside = insideCell(side, along);
        int y = p.floorY(floor);
        return !tower.reserved(floor, inside[0], inside[1]) && inside[0] != p.centerX() && inside[1] != p.centerZ()
                && w.get(inside[0], y + 1, inside[1]).getMaterial() == Material.AIR
                && w.get(inside[0], y + 2, inside[1]).getMaterial() == Material.AIR;
    }

    private int[] insideCell(EnumFacing side, int along) {
        switch (side) {
            case NORTH:
                return new int[]{along, p.minZ + 1};
            case SOUTH:
                return new int[]{along, p.maxZ() - 1};
            case WEST:
                return new int[]{p.minX + 1, along};
            default:
                return new int[]{p.maxX() - 1, along};
        }
    }

    private void cutDoor(int floor, EnumFacing side, int along) {
        int y = p.floorY(floor);
        int wallX = side == EnumFacing.WEST ? p.minX : side == EnumFacing.EAST ? p.maxX() : 0;
        int wallZ = side == EnumFacing.NORTH ? p.minZ : side == EnumFacing.SOUTH ? p.maxZ() : 0;
        for (int a = along; a <= along + 1; a++) {
            int x = side.getAxis() == EnumFacing.Axis.X ? wallX : a;
            int z = side.getAxis() == EnumFacing.Axis.X ? a : wallZ;
            // Through the tower wall and the wing's own wall behind it.
            for (int step = 0; step <= 1; step++) {
                int sx = x + side.getXOffset() * step;
                int sz = z + side.getZOffset() * step;
                w.clear(sx, y + 1, sz, sx, y + 3, sz);
                w.set(sx, y, sz, pal.get(PaletteSlot.FLOOR, rand));
            }
            int[] inside = insideCell(side, a);
            tower.reserve(floor, inside[0], inside[1], inside[0], inside[1]);
        }
        // Lintel over the doorway.
        for (int a = along - 1; a <= along + 2; a++) {
            int x = side.getAxis() == EnumFacing.Axis.X ? wallX : a;
            int z = side.getAxis() == EnumFacing.Axis.X ? a : wallZ;
            w.set(x, y + 4, z, pal.get(PaletteSlot.ACCENT, rand));
        }
    }

    private void buildWingFloor(int floor, EnumFacing side, int minX, int minZ, int maxX, int maxZ) {
        int y0 = p.floorY(floor);
        int top = y0 + CitadelPlan.FLOOR_HEIGHT - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                w.set(x, y0, z, edge ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                for (int y = y0 + 1; y <= top; y++) {
                    if (!edge) {
                        w.set(x, y, z, Blocks.AIR.getDefaultState());
                        continue;
                    }
                    boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                    int along = side.getAxis() == EnumFacing.Axis.X ? z - minZ : x - minX;
                    int out = side.getAxis() == EnumFacing.Axis.X ? x - minX : z - minZ;
                    boolean outerFace = side.getAxis() == EnumFacing.Axis.X ? (x == minX || x == maxX) : (z == minZ || z == maxZ);
                    int pos = outerFace ? along : out;
                    if (corner || pos % 4 == 0) {
                        w.set(x, y, z, pal.get(PaletteSlot.PILLAR));
                    } else if (y == top) {
                        w.set(x, y, z, pal.get(PaletteSlot.ACCENT, rand));
                    } else if (pos % 4 == 2 && (y == y0 + 3 || y == y0 + 4)) {
                        w.set(x, y, z, window);
                    } else {
                        w.set(x, y, z, pal.get(PaletteSlot.WALL, rand));
                    }
                }
            }
        }
        // Ceiling light in the middle of the wing.
        w.set((minX + maxX) / 2, y0 + CitadelPlan.FLOOR_HEIGHT, (minZ + maxZ) / 2, pal.get(PaletteSlot.LIGHT));
    }

    private void buildWingRoof(int minX, int minZ, int maxX, int maxZ, int roofY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                w.set(x, roofY, z, edge ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                if (edge) {
                    w.set(x, roofY + 1, z, pal.get(PaletteSlot.WALL, rand));
                    if (((x - minX) + (z - minZ)) % 2 == 0) {
                        w.set(x, roofY + 2, z, pal.get(PaletteSlot.WALL, rand));
                    }
                } else {
                    w.set(x, roofY + 1, z, Blocks.AIR.getDefaultState());
                    w.set(x, roofY + 2, z, Blocks.AIR.getDefaultState());
                }
            }
        }
        w.set(minX + 2, roofY, minZ + 2, pal.get(PaletteSlot.LIGHT));
        w.set(maxX - 2, roofY, maxZ - 2, pal.get(PaletteSlot.LIGHT));
    }

    /** Buttress piers under an elevated wing, every 4 blocks along its outline, down to the ground. */
    private void buildPiers(int minX, int minZ, int maxX, int maxZ, int topY) {
        IBlockState pier = pal.get(PaletteSlot.PILLAR);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!edge) {
                    continue;
                }
                boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                if (corner || ((x - minX) % 4 == 0 && (z == minZ || z == maxZ)) || ((z - minZ) % 4 == 0 && (x == minX || x == maxX))) {
                    w.set(x, topY, z, pier);
                    w.fillDown(x, z, topY - 1, pier, topY - BlockWriter.MIN_Y);
                } else {
                    // Corbel course under the slab between the piers.
                    w.set(x, topY, z, pal.stairs(inwardFacing(x, z, minX, minZ, maxX, maxZ), true));
                }
            }
        }
    }

    /**
     * Under a high wing: an inverted stepped bracket that narrows towards the tower wall, so the wing
     * reads as cantilevered from the tower instead of standing on stilts a hundred blocks long.
     */
    private void buildCorbel(EnumFacing side, int minX, int minZ, int maxX, int maxZ, int topY) {
        int length = side.getAxis() == EnumFacing.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        for (int k = 0; ; k++) {
            int reach = length - 2 * k;
            if (reach <= 0) {
                return;
            }
            int y = topY - k;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int out = outward(side, x, z, minX, minZ, maxX, maxZ);
                    if (out >= reach) {
                        continue;
                    }
                    boolean front = out == reach - 1;
                    w.set(x, y, z, front ? pal.stairs(side.getOpposite(), true) : pal.get(PaletteSlot.WALL, rand));
                }
            }
        }
    }

    /** @return distance of the cell from the tower wall (0 = touching it). */
    private static int outward(EnumFacing side, int x, int z, int minX, int minZ, int maxX, int maxZ) {
        switch (side) {
            case NORTH:
                return maxZ - z;
            case SOUTH:
                return z - minZ;
            case WEST:
                return maxX - x;
            default:
                return x - minX;
        }
    }

    private static EnumFacing inwardFacing(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        if (x == minX) {
            return EnumFacing.WEST;
        }
        if (x == maxX) {
            return EnumFacing.EAST;
        }
        return z == minZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
    }

    private void furnishWingFloor(int floor, int minX, int minZ, int maxX, int maxZ) {
        int y0 = p.floorY(floor);
        RoomTag tag = WING_ROOMS[rand.nextInt(WING_ROOMS.length)];
        int recordFloor = tower.recordFloor(floor);
        rec.addRoom(new StructureBoundingBox(minX + 1, y0, minZ + 1, maxX - 1, y0 + CitadelPlan.FLOOR_HEIGHT - 1, maxZ - 1),
                tag, recordFloor);
        RoomFurnisher furnisher = new RoomFurnisher(site(minX + 1, minZ + 1, maxX - 1, maxZ - 1), rand, pal, vase);
        // Guardian of the wing: part of this floor's waves.
        BlockPos spawner = new BlockPos((minX + maxX) / 2 + (rand.nextBoolean() ? 1 : -1), y0 + 1, (minZ + maxZ) / 2);
        StructurePieces.spawner(w, rec, spawner, new SpawnerContext(source, rec.getCitadelId(), recordFloor,
                tower.tier(floor), tag));
        if (RoomFurnisher.isLivedIn(tag)) {
            furnisher.furnish(tag, minX + 1, minZ + 1, maxX - 1, maxZ - 1, y0);
        } else if (tag == RoomTag.LIBRARY) {
            for (int x = minX + 1; x <= maxX - 1; x += 2) {
                for (int y = y0 + 1; y <= y0 + 3; y++) {
                    placeIfAir(x, y, minZ + 1, Blocks.BOOKSHELF.getDefaultState());
                    placeIfAir(x, y, maxZ - 1, Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
        if (rand.nextFloat() < 0.55F || tag == RoomTag.TREASURY) {
            BlockPos chest = new BlockPos(minX + 1 + rand.nextInt(maxX - minX - 1), y0 + 1, rand.nextBoolean() ? minZ + 1 : maxZ - 1);
            if (w.get(chest.getX(), chest.getY(), chest.getZ()).getMaterial() == Material.AIR) {
                StructurePieces.chest(w, rec, chest, chest.getZ() == minZ + 1 ? EnumFacing.SOUTH : EnumFacing.NORTH,
                        ModLootTables.forFloor(tower.difficulty(floor)), rand.nextLong());
            }
        }
        furnisher.vases(minX + 1, minZ + 1, maxX - 1, maxZ - 1, y0, 1 + rand.nextInt(3));
        furnisher.abandon(minX + 1, minZ + 1, maxX - 1, maxZ - 1, y0, CitadelPlan.FLOOR_HEIGHT);
    }

    // ------------------------------------------------------------------ annexes

    /**
     * A slender turret against the far end of the wing. Only its top floor opens into it (solid
     * below), so it never links two wing floors and cannot bypass a sealed stairwell; a ladder
     * climbs to a crenellated lookout with a reward.
     */
    private void buildTurret(EnumFacing side, int floor, int height, int minX, int minZ, int maxX, int maxZ, int roofY) {
        boolean first = rand.nextBoolean();
        int tx;
        int tz;
        switch (side) {
            case NORTH:
                tx = first ? minX : maxX - 4; tz = minZ - 5;
                break;
            case SOUTH:
                tx = first ? minX : maxX - 4; tz = maxZ + 1;
                break;
            case WEST:
                tx = minX - 5; tz = first ? minZ : maxZ - 4;
                break;
            default:
                tx = maxX + 1; tz = first ? minZ : maxZ - 4;
                break;
        }
        int bottom = p.floorY(floor);
        int doorY = p.floorY(floor + height - 1);
        int top = roofY + 6 + rand.nextInt(4);
        int cx = tx + 2;
        int cz = tz + 2;
        for (int x = tx; x <= tx + 4; x++) {
            for (int z = tz; z <= tz + 4; z++) {
                boolean edge = x == tx || x == tx + 4 || z == tz || z == tz + 4;
                boolean corner = (x == tx || x == tx + 4) && (z == tz || z == tz + 4);
                for (int y = bottom; y < top; y++) {
                    if (edge) {
                        w.set(x, y, z, corner ? pal.get(PaletteSlot.PILLAR)
                                : (y - bottom) % 6 == 3 && (x == cx || z == cz) && y > doorY ? window : pal.get(PaletteSlot.WALL, rand));
                    } else {
                        // Solid base up to the wing's top floor, hollow above it.
                        w.set(x, y, z, y <= doorY ? pal.get(PaletteSlot.WALL, rand) : Blocks.AIR.getDefaultState());
                    }
                }
                w.set(x, top, z, edge ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                if (edge && (x + z) % 2 == 0) {
                    w.set(x, top + 1, z, pal.get(PaletteSlot.WALL, rand));
                }
                if (floor <= MAX_PIER_FLOOR) {
                    w.fillDown(x, z, bottom - 1, corner ? pal.get(PaletteSlot.PILLAR) : pal.get(PaletteSlot.WALL, rand),
                            floor > 0 ? bottom - BlockWriter.MIN_Y : CitadelBuilder.FOUNDATION_MAX_DEPTH);
                } else {
                    // Tapering cap under a high turret.
                    int depth = Math.min(Math.min(x - tx, tx + 4 - x), Math.min(z - tz, tz + 4 - z));
                    for (int d = 1; d <= depth + 1; d++) {
                        w.set(x, bottom - d, z, pal.get(PaletteSlot.WALL, rand));
                    }
                }
            }
        }
        // Doorway through the wing's end wall and the turret wall, on the wing's top floor only.
        EnumFacing out = side;
        int doorX = side.getAxis() == EnumFacing.Axis.Z ? cx : (side == EnumFacing.WEST ? minX : maxX);
        int doorZ = side.getAxis() == EnumFacing.Axis.Z ? (side == EnumFacing.NORTH ? minZ : maxZ) : cz;
        for (int step = 0; step <= 1; step++) {
            int x = doorX + out.getXOffset() * step;
            int z = doorZ + out.getZOffset() * step;
            w.set(x, doorY + 1, z, Blocks.AIR.getDefaultState());
            w.set(x, doorY + 2, z, Blocks.AIR.getDefaultState());
            if (step == 1) {
                // The lookout can be reached from outside: the way in waits like the stairwell does.
                sealFromOutside(x, doorY + 1, z, floor + height - 1);
                sealFromOutside(x, doorY + 2, z, floor + height - 1);
            }
        }
        // Ladder on the far inner wall, from the doorway up to the lookout.
        int lx = cx + out.getXOffset();
        int lz = cz + out.getZOffset();
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, out.getOpposite());
        for (int y = doorY + 1; y <= top; y++) {
            w.set(lx, y, lz, ladder);
        }
        w.set(cx - out.getXOffset(), top - 1, cz - out.getZOffset(), pal.get(PaletteSlot.LIGHT));
        // Reward on the lookout.
        if (rand.nextFloat() < 0.5F) {
            StructurePieces.chest(w, rec, new BlockPos(cx - out.getXOffset(), top + 1, cz - out.getZOffset()), out,
                    ModLootTables.forFloor(Math.min(6, tower.difficulty(floor) + 1)), rand.nextLong());
        } else {
            w.set(cx - out.getXOffset(), top + 1, cz - out.getZOffset(),
                    ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT, BlockVase.Variant.GILDED));
        }
        rec.addPart(new StructureBoundingBox(tx, floor == 0 ? bottom : floor <= MAX_PIER_FLOOR ? BlockWriter.MIN_Y : bottom - 3,
                tz, tx + 4, top + 1, tz + 4));
        if (rand.nextFloat() < 0.45F) {
            buildBalcony(side, tx, tz, top - 3);
        }
    }

    /** Annex of the annex: a small balcony hanging off the turret near its top. */
    private void buildBalcony(EnumFacing side, int tx, int tz, int y) {
        int bx = tx + 2 + side.getXOffset() * 3;
        int bz = tz + 2 + side.getZOffset() * 3;
        for (int a = -1; a <= 1; a++) {
            int x = side.getAxis() == EnumFacing.Axis.Z ? bx + a : bx;
            int z = side.getAxis() == EnumFacing.Axis.Z ? bz : bz + a;
            w.set(x, y, z, pal.get(PaletteSlot.FLOOR, rand));
            w.set(x, y - 1, z, pal.stairs(side, true));
            w.set(x, y + 1, z, Blocks.IRON_BARS.getDefaultState());
        }
        // Door from the turret onto the balcony.
        int dx = tx + 2 + side.getXOffset() * 2;
        int dz = tz + 2 + side.getZOffset() * 2;
        w.set(dx, y + 1, dz, Blocks.AIR.getDefaultState());
        w.set(dx, y + 2, dz, Blocks.AIR.getDefaultState());
        w.set(dx, y, dz, pal.get(PaletteSlot.FLOOR, rand));
    }

    /** A small chapel on the wing roof, reached by a ladder through a hatch from the top floor. */
    private void buildRoofChapel(int topFloor, int minX, int minZ, int maxX, int maxZ, int roofY) {
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        int x0 = cx - 2;
        int x1 = cx + 2;
        int z0 = cz - 3;
        int z1 = cz + 3;
        if (x0 <= minX || x1 >= maxX || z0 <= minZ || z1 >= maxZ) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = roofY + 1; y <= roofY + 4; y++) {
                    w.set(x, y, z, edge ? (y == roofY + 3 && z == cz ? window : pal.get(PaletteSlot.WALL, rand))
                            : Blocks.AIR.getDefaultState());
                }
            }
        }
        // Stepped gable roof.
        for (int step = 0; step <= 2; step++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                w.set(x0 - 1 + step, roofY + 5 + step, z, pal.stairs(EnumFacing.EAST, false));
                w.set(x1 + 1 - step, roofY + 5 + step, z, pal.stairs(EnumFacing.WEST, false));
            }
        }
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            w.set(cx, roofY + 7, z, pal.get(PaletteSlot.WALL, rand));
        }
        // Hatch with ladder from the wing's top floor, altar-like lectern spot with loot.
        int hz = z0 + 1;
        int floorY = p.floorY(topFloor);
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.SOUTH);
        for (int y = floorY + 1; y <= roofY; y++) {
            // A pillar in the room carries the ladder up through the hatch.
            if (y < roofY) {
                w.set(cx, y, hz - 1, pal.get(PaletteSlot.PILLAR));
            }
            w.set(cx, y, hz, ladder);
        }
        sealFromOutside(cx, roofY, hz, topFloor);
        w.set(x0, roofY + 1, cz, Blocks.AIR.getDefaultState());
        w.set(x0, roofY + 2, cz, Blocks.AIR.getDefaultState());
        StructurePieces.chest(w, rec, new BlockPos(cx, roofY + 1, z1 - 1), EnumFacing.NORTH,
                ModLootTables.BEDROOM, rand.nextLong());
        w.set(cx + 1, roofY + 1, z1 - 1, pal.get(PaletteSlot.LIGHT));
        rec.addPart(new StructureBoundingBox(x0 - 1, roofY, z0 - 1, x1 + 1, roofY + 7, z1 + 1));
    }

    /**
     * Closes an opening that leads from the outside (roof, lookout) into wing floor {@code floor}
     * with a sealed gate that opens with the gate into that floor, i.e. when the floor below it is
     * cleared. The ground floor of a plain tower is open anyway.
     */
    private void sealFromOutside(int x, int y, int z, int floor) {
        int recordFloor = tower.recordFloor(floor);
        if (recordFloor < 1 || !ConfigCache.get().isLockFloorsUntilCleared()) {
            return;
        }
        IBlockState replaced = w.get(x, y, z);
        if (w.set(x, y, z, ModBlocks.SEALED_GATE.getDefaultState())) {
            rec.addGate(new BlockPos(x, y, z), recordFloor - 1, net.minecraft.block.Block.getStateId(replaced));
        }
    }

    private RoomFurnisher.Site site(int minX, int minZ, int maxX, int maxZ) {
        return new RoomFurnisher.Site() {
            @Override
            public boolean free(int x, int y, int z) {
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ && w.inside(x, y, z)
                        && w.get(x, y, z).getMaterial() == Material.AIR;
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

    private void placeIfAir(int x, int y, int z, IBlockState state) {
        if (w.get(x, y, z).getMaterial() == Material.AIR) {
            w.set(x, y, z, state);
        }
    }
}
