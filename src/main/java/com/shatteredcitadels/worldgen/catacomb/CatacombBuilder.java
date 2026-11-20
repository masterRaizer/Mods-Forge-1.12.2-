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
    public int build(int centreX, int centreZ, int topLevelY, int levels, int entranceX, int entranceZ, int entranceTopY) {
        int count = fittingLevels(topLevelY, levels);
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
        int bottom = levelY(topLevelY, count - 1) - extraDepth();
        rec.addPart(new StructureBoundingBox(x0 - 1, bottom - 1, z0 - 1, x0 + CELL * CELLS, entranceTopY, z0 + CELL * CELLS));
        return count;
    }

    /** Task 4 hook: the deepest level holds the mage's sanctum. */
    protected boolean hasSanctum(boolean deepest) {
        return false;
    }

    /** Extra depth dug below the deepest level (the sanctum); 0 here. */
    protected int extraDepth() {
        return 0;
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

    /** Hook for themed halls (Task 4 fills them); here a few vases and a spawner. */
    protected void buildHall(CatacombLayout.Hall hall, int level, int yL) {
        int xa = cellX(hall.ci0) + 1;
        int za = cellZ(hall.cj0) + 1;
        int xb = cellX(hall.ci1) + 3;
        int zb = cellZ(hall.cj1) + 3;
        spawner(new BlockPos((xa + xb) / 2, yL + 1, (za + zb) / 2), level, RoomTag.OSSUARY);
        vases(xa, za, xb, zb, yL, 2 + rand.nextInt(3));
        if (rand.nextBoolean()) {
            chest(new BlockPos(xa, yL + 1, za), EnumFacing.SOUTH, level);
        }
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
        return ModLootTables.UNDERGROUND_CRYPT;
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
