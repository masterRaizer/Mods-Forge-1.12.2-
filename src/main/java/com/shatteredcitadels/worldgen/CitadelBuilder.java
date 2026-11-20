package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.PaletteSlot;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.spawner.WaveSpawnerHooks;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.RoomTag;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a citadel floor by floor ({@link CitadelPlan#FLOOR_HEIGHT} = 8): foundation, outer
 * shell, inner walls with doors, rotating staircases, rooms with decor, chest and spawner
 * points, the boss arena on the top floor, battlements and, for the Citadel Above the Clouds,
 * the spire. Writes only through {@link BlockWriter}.
 */
public final class CitadelBuilder {

    public static final int FOUNDATION_MAX_DEPTH = 24;
    private static final int STAIR_STEPS = 7;

    private static final int NW = 0;
    private static final int NE = 1;
    private static final int SE = 2;
    private static final int SW = 3;

    private static final RoomTag[] ROOM_ROTATION = {
            RoomTag.GUARD_ROOM, RoomTag.PANTRY, RoomTag.LIBRARY, RoomTag.BEDROOM,
            RoomTag.BARRACKS, RoomTag.SMELTERY, RoomTag.TREASURY
    };

    private final BlockWriter w;
    private final CitadelPlan p;
    private final BlockPalette pal;
    private final Random rand;
    private final CitadelInstance rec;
    private final SpawnerSource source;
    private final IBlockState air = Blocks.AIR.getDefaultState();
    private final IBlockState window;

    private final int x0;
    private final int z0;
    private final int x1;
    private final int z1;
    private final int cx;
    private final int cz;
    private final int mid;

    /** {floor, minX, minZ, maxX, maxZ} areas decor must not touch (stairs, holes, shafts). */
    private final List<int[]> reserved = new ArrayList<>();
    private final RoomFurnisher furnisher;

    public CitadelBuilder(BlockWriter writer, CitadelPlan plan, BlockPalette palette, Random random,
                          CitadelInstance record) {
        this.w = writer;
        this.p = plan;
        this.pal = palette;
        this.rand = random;
        this.rec = record;
        this.source = plan.sky ? SpawnerSource.SKY_CITADEL : SpawnerSource.CITADEL;
        this.window = plan.archetype == CitadelArchetype.SKY_CITADEL || plan.archetype == CitadelArchetype.TECHNO_ARCANE_SPIRE
                ? Blocks.GLASS_PANE.getDefaultState() : Blocks.IRON_BARS.getDefaultState();
        this.x0 = plan.minX;
        this.z0 = plan.minZ;
        this.x1 = plan.maxX();
        this.z1 = plan.maxZ();
        this.cx = plan.centerX();
        this.cz = plan.centerZ();
        this.mid = plan.width / 2;
        this.furnisher = new RoomFurnisher(new RoomFurnisher.Site() {
            @Override
            public boolean free(int x, int y, int z) {
                return placeable(Math.floorDiv(y - p.floorY(0), CitadelPlan.FLOOR_HEIGHT), x, y, z);
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
                if (placeable(Math.floorDiv(pos.getY() - p.floorY(0), CitadelPlan.FLOOR_HEIGHT), pos.getX(), pos.getY(), pos.getZ())) {
                    placeChest(pos, facing, lootTable);
                }
            }

            @Override
            public World world() {
                return w.getWorld();
            }
        }, random, palette, vaseVariant(plan.archetype));
    }

    /** Clay in stone keeps, dark glaze in nether and techno halls, gilded (rare) elsewhere. */
    static BlockVase.Variant vaseVariant(CitadelArchetype archetype) {
        if (archetype == CitadelArchetype.OBSIDIAN_BASTILLE || archetype == CitadelArchetype.TECHNO_ARCANE_SPIRE) {
            return BlockVase.Variant.DARK;
        }
        return archetype == CitadelArchetype.SKY_CITADEL ? BlockVase.Variant.GILDED : BlockVase.Variant.CLAY;
    }

    /** X/Z of the ladder shaft that leads from the ground floor into the crypt. */
    public static int shaftX(CitadelPlan plan) {
        return plan.maxX() - 3;
    }

    public static int shaftZ(CitadelPlan plan) {
        return plan.maxZ() - 3;
    }

    public void build() {
        buildFoundation();
        for (int f = 0; f < p.floors; f++) {
            buildShell(f);
        }
        buildRoof();
        for (int f = 0; f < p.topFloorIndex(); f++) {
            buildInnerWalls(f);
        }
        for (int f = 0; f < p.topFloorIndex(); f++) {
            buildStairs(f);
        }
        buildEntrance();
        if (p.cryptLevels > 0) {
            cutCryptHatch();
        }
        for (int f = 0; f < p.topFloorIndex(); f++) {
            decorateFloor(f);
        }
        buildBossArena();
        if (p.sky) {
            buildSpire();
        }
        ConfigCache.Snapshot waves = ConfigCache.get();
        for (int f = 0; f < p.floors; f++) {
            int y0 = p.floorY(f);
            int height = f == p.topFloorIndex() ? p.arenaHeight : CitadelPlan.FLOOR_HEIGHT;
            boolean boss = f == p.topFloorIndex();
            rec.addFloor(f, y0, y0 + height - 1, difficultyForFloor(f), boss ? 1 : waves.rollWaveCount(rand), boss);
        }
    }

    // ------------------------------------------------------------------ structure

    private void buildFoundation() {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int d = 1; d <= FOUNDATION_MAX_DEPTH; d++) {
                    int y = p.baseY - d;
                    if (y < BlockWriter.MIN_Y || (d > 1 && w.isSolid(x, y, z))) {
                        break;
                    }
                    w.set(x, y, z, edge ? pal.get(PaletteSlot.PILLAR) : pal.get(PaletteSlot.WALL, rand));
                }
            }
        }
    }

    private void buildShell(int f) {
        int y0 = p.floorY(f);
        boolean arena = f == p.topFloorIndex();
        int height = arena ? p.arenaHeight : CitadelPlan.FLOOR_HEIGHT;
        // Slab (doubles as the ceiling of the floor below).
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (isPerimeter(x, z)) {
                    w.set(x, y0, z, pal.get(PaletteSlot.WALL, rand));
                } else if (x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1) {
                    w.set(x, y0, z, pal.get(PaletteSlot.ACCENT, rand));
                } else {
                    w.set(x, y0, z, pal.get(PaletteSlot.FLOOR, rand));
                }
            }
        }
        // Interior volume: removes terrain and trees inside the footprint.
        w.clear(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + height - 1, z1 - 1);
        // Outer walls with pilasters, string course and windows.
        for (int y = y0 + 1; y <= y0 + height - 1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (!isPerimeter(x, z)) {
                        continue;
                    }
                    int along = alongWall(x, z);
                    if (isCorner(x, z) || along % 6 == 0) {
                        w.set(x, y, z, pal.get(PaletteSlot.PILLAR));
                    } else if (!arena && y == y0 + height - 1) {
                        w.set(x, y, z, pal.get(PaletteSlot.ACCENT, rand));
                    } else if (!arena && along % 6 == 3 && (y == y0 + 3 || y == y0 + 4)) {
                        w.set(x, y, z, window);
                    } else if (!arena && along % 6 == 3 && y == y0 + 6) {
                        w.set(x, y, z, pal.get(PaletteSlot.LIGHT));
                    } else {
                        w.set(x, y, z, pal.get(PaletteSlot.WALL, rand));
                    }
                }
            }
        }
    }

    private void buildRoof() {
        int yr = p.roofY();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean perimeter = isPerimeter(x, z);
                w.set(x, yr, z, perimeter ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                if (perimeter) {
                    w.set(x, yr + 1, z, pal.get(PaletteSlot.WALL, rand));
                    if (alongWall(x, z) % 2 == 0) {
                        w.set(x, yr + 2, z, pal.get(PaletteSlot.WALL, rand));
                    }
                }
            }
        }
        int[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        for (int[] c : corners) {
            w.fill(c[0], yr + 1, c[1], c[0], yr + 3, c[1], pal.get(PaletteSlot.PILLAR));
            w.set(c[0], yr + 4, c[1], pal.get(PaletteSlot.LIGHT));
        }
        // Roof lights so the top stays lit at night.
        for (int x = x0 + 3; x <= x1 - 3; x += 5) {
            for (int z = z0 + 3; z <= z1 - 3; z += 5) {
                w.set(x, yr, z, pal.get(PaletteSlot.LIGHT));
            }
        }
    }

    private void buildInnerWalls(int f) {
        int y0 = p.floorY(f);
        int top = y0 + CitadelPlan.FLOOR_HEIGHT - 1;
        boolean ground = f == 0;
        // North-south wall; on the ground floor only the north half (open entrance hall).
        int zEnd = ground ? cz : z1 - 1;
        for (int z = z0 + 1; z <= zEnd; z++) {
            for (int y = y0 + 1; y <= top; y++) {
                w.set(cx, y, z, z == cz ? pal.get(PaletteSlot.PILLAR) : pal.get(PaletteSlot.WALL, rand));
            }
        }
        // West-east wall.
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            for (int y = y0 + 1; y <= top; y++) {
                w.set(x, y, cz, x == cx ? pal.get(PaletteSlot.PILLAR) : pal.get(PaletteSlot.WALL, rand));
            }
        }
        // Doorways (2 wide, 3 high) in the middle of each wall segment.
        int northDoor = (z0 + 1 + cz - 1) / 2;
        int southDoor = (cz + 1 + z1 - 1) / 2;
        int westDoor = (x0 + 1 + cx - 1) / 2;
        int eastDoor = (cx + 1 + x1 - 1) / 2;
        w.clear(cx, y0 + 1, northDoor, cx, y0 + 3, northDoor + 1);
        if (!ground) {
            w.clear(cx, y0 + 1, southDoor, cx, y0 + 3, southDoor + 1);
        }
        w.clear(westDoor, y0 + 1, cz, westDoor + 1, y0 + 3, cz);
        w.clear(eastDoor, y0 + 1, cz, eastDoor + 1, y0 + 3, cz);
    }

    /** Straight 2-wide staircase from floor f to f+1; the quadrant rotates every floor. */
    private void buildStairs(int f) {
        int y0 = p.floorY(f);
        int quadrant = f % 4;
        int sx;
        int sz;
        int dx;
        int dz;
        int lx;
        int lz;
        EnumFacing facing;
        switch (quadrant) {
            case NW:
                sx = x0 + 2; sz = z0 + 1; dx = 1; dz = 0; lx = 0; lz = 1; facing = EnumFacing.EAST;
                break;
            case NE:
                sx = x1 - 1; sz = z0 + 2; dx = 0; dz = 1; lx = -1; lz = 0; facing = EnumFacing.SOUTH;
                break;
            case SE:
                sx = x1 - 2; sz = z1 - 1; dx = -1; dz = 0; lx = 0; lz = -1; facing = EnumFacing.WEST;
                break;
            default:
                sx = x0 + 1; sz = z1 - 2; dx = 0; dz = -1; lx = 1; lz = 0; facing = EnumFacing.NORTH;
                break;
        }
        IBlockState stair = pal.stairs(facing, false);
        for (int k = 0; k < STAIR_STEPS; k++) {
            int y = y0 + 1 + k;
            for (int l = 0; l < 2; l++) {
                int x = sx + dx * k + lx * l;
                int z = sz + dz * k + lz * l;
                if (k > 0) {
                    w.fill(x, y0 + 1, z, x, y - 1, z, pal.get(PaletteSlot.WALL, rand));
                }
                w.set(x, y, z, stair);
                // Headroom of 3; cuts the hole through the slab of the next floor for k >= 4.
                w.clear(x, y + 1, z, x, y + 3, z);
            }
        }
        int minX = Math.min(sx, sx + dx * STAIR_STEPS + lx) - 1;
        int maxX = Math.max(sx, sx + dx * STAIR_STEPS + lx) + 1;
        int minZ = Math.min(sz, sz + dz * STAIR_STEPS + lz) - 1;
        int maxZ = Math.max(sz, sz + dz * STAIR_STEPS + lz) + 1;
        reserve(f, minX, minZ, maxX, maxZ);
        reserve(f + 1, minX, minZ, maxX, maxZ);
        if (ConfigCache.get().isLockFloorsUntilCleared()) {
            sealStairwell(f, sx, sz, dx, dz, lx, lz);
        }
    }

    /**
     * Seals the opening the staircase cuts into the slab of floor f+1 (steps 4-6) and its top step
     * (step 7, flush with the slab) with sealed gates that open once floor f is cleared.
     */
    private void sealStairwell(int f, int sx, int sz, int dx, int dz, int lx, int lz) {
        int y = p.floorY(f + 1);
        IBlockState gate = ModBlocks.SEALED_GATE.getDefaultState();
        for (int k = 4; k < STAIR_STEPS + 1; k++) {
            for (int l = 0; l < 2; l++) {
                int x = sx + dx * k + lx * l;
                int z = sz + dz * k + lz * l;
                IBlockState replaced = w.get(x, y, z);
                if (w.set(x, y, z, gate)) {
                    rec.addGate(new BlockPos(x, y, z), f, Block.getStateId(replaced));
                }
            }
        }
    }

    private void buildEntrance() {
        int y0 = p.floorY(0);
        w.clear(cx - 1, y0 + 1, z1, cx + 1, y0 + 3, z1);
        w.set(cx - 2, y0 + 4, z1, pal.get(PaletteSlot.ACCENT, rand));
        w.fill(cx - 1, y0 + 4, z1, cx + 1, y0 + 4, z1, pal.get(PaletteSlot.ACCENT, rand));
        w.set(cx + 2, y0 + 4, z1, pal.get(PaletteSlot.ACCENT, rand));
        // Porch outside the door (still inside the populated area: footprint ends at +30).
        for (int x = cx - 2; x <= cx + 2; x++) {
            w.set(x, y0, z1 + 1, pal.get(PaletteSlot.FLOOR, rand));
            w.clear(x, y0 + 1, z1 + 1, x, y0 + 3, z1 + 1);
        }
        w.set(cx - 2, y0 + 1, z1 + 1, pal.get(PaletteSlot.LIGHT));
        w.set(cx + 2, y0 + 1, z1 + 1, pal.get(PaletteSlot.LIGHT));
        reserve(0, cx - 2, z1 - 2, cx + 2, z1);
        rec.addRoom(new StructureBoundingBox(x0 + 1, y0, cz + 1, x1 - 1, y0 + CitadelPlan.FLOOR_HEIGHT - 1, z1 - 1),
                RoomTag.ENTRANCE_HALL, 0);
    }

    private void cutCryptHatch() {
        int sx = shaftX(p);
        int sz = shaftZ(p);
        int y0 = p.floorY(0);
        // Rim around the hatch; the ladder shaft itself is built by DungeonCryptBuilder.
        for (int x = sx - 1; x <= sx + 1; x++) {
            for (int z = sz - 1; z <= sz + 1; z++) {
                if (x != sx || z != sz) {
                    w.set(x, y0, z, pal.get(PaletteSlot.ACCENT, rand));
                }
            }
        }
        w.set(sx, y0, sz, air);
        reserve(0, sx - 2, sz - 2, sx + 2, sz + 2);
    }

    // ------------------------------------------------------------------ rooms & decor

    private void decorateFloor(int f) {
        int y0 = p.floorY(f);
        int stairsQuadrant = f % 4;
        int tier = tierForFloor(f);
        int[][] quads = quadrants();
        int chestQuadrant = (stairsQuadrant + 1 + rand.nextInt(3)) % 4;
        int spawners = f == 0 ? 1 : 2;

        for (int q = 0; q < 4; q++) {
            int[] b = quads[q];
            boolean hall = f == 0 && (q == SE || q == SW);
            RoomTag tag = hall ? RoomTag.ENTRANCE_HALL : ROOM_ROTATION[(f + q) % ROOM_ROTATION.length];
            if (!hall) {
                rec.addRoom(new StructureBoundingBox(b[0], y0, b[1], b[2], y0 + CitadelPlan.FLOOR_HEIGHT - 1, b[3]), tag, f);
            }
            int qx = (b[0] + b[2]) / 2;
            int qz = (b[1] + b[3]) / 2;

            // Ceiling light, embedded in the slab of the floor above.
            if (!isReserved(f + 1, qx, qz)) {
                w.set(qx, y0 + CitadelPlan.FLOOR_HEIGHT, qz, pal.get(PaletteSlot.LIGHT));
            }

            // Furniture in the room corners (lived-in rooms are furnished below instead).
            int[][] corners = {{b[0], b[1]}, {b[2], b[1]}, {b[0], b[3]}, {b[2], b[3]}};
            for (int[] c : corners) {
                if (!RoomFurnisher.isLivedIn(tag) && rand.nextFloat() < 0.6F && placeable(f, c[0], y0 + 1, c[1])) {
                    w.set(c[0], y0 + 1, c[1], pal.get(PaletteSlot.FURNITURE, rand));
                }
            }

            if (tag == RoomTag.LIBRARY) {
                buildBookshelves(f, b, y0);
            }

            boolean chestHere = q == chestQuadrant || tag == RoomTag.TREASURY;
            if (chestHere) {
                placeChestAgainstWall(f, b, y0, ModLootTables.forFloor(difficultyForFloor(f)));
            }

            boolean spawnerHere = q == (stairsQuadrant + 2) % 4 || (spawners > 1 && q == (stairsQuadrant + 3) % 4);
            if (spawnerHere) {
                int sx = qx + (rand.nextBoolean() ? 1 : -1);
                int sz = qz + (rand.nextBoolean() ? 1 : -1);
                if (placeable(f, sx, y0 + 1, sz)) {
                    placeSpawner(new BlockPos(sx, y0 + 1, sz), f, tier, tag);
                }
            }

            if (RoomFurnisher.isLivedIn(tag)) {
                furnisher.furnish(tag, b[0], b[1], b[2], b[3], y0);
            }
            furnisher.vases(b[0], b[1], b[2], b[3], y0, 1 + rand.nextInt(hall ? 2 : 3));
            furnisher.abandon(b[0], b[1], b[2], b[3], y0, CitadelPlan.FLOOR_HEIGHT);
        }
    }

    private void buildBookshelves(int f, int[] b, int y0) {
        // Along the room side that touches the outer wall in Z.
        int z = b[1] == z0 + 1 ? b[1] : b[3];
        for (int x = b[0] + 1; x <= b[2] - 1; x++) {
            if (placeable(f, x, y0 + 1, z) && placeable(f, x, y0 + 2, z)) {
                w.set(x, y0 + 1, z, Blocks.BOOKSHELF.getDefaultState());
                w.set(x, y0 + 2, z, Blocks.BOOKSHELF.getDefaultState());
            }
        }
    }

    private void placeChestAgainstWall(int f, int[] b, int y0, ResourceLocation lootTable) {
        int qx = (b[0] + b[2]) / 2;
        boolean north = b[1] == z0 + 1;
        int z = north ? b[1] : b[3];
        EnumFacing facing = north ? EnumFacing.SOUTH : EnumFacing.NORTH;
        for (int offset = 0; offset <= 3; offset++) {
            int x = qx + (offset % 2 == 0 ? offset / 2 : -(offset / 2 + 1));
            if (placeable(f, x, y0 + 1, z)) {
                placeChest(new BlockPos(x, y0 + 1, z), facing, lootTable);
                return;
            }
        }
    }

    /** Loot is set as a table (never pre-filled), which is what makes the chest Lootr-compatible. */
    private void placeChest(BlockPos pos, EnumFacing facing, ResourceLocation lootTable) {
        if (!w.set(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing))) {
            return;
        }
        TileEntity tile = w.getWorld().getTileEntity(pos);
        if (tile instanceof TileEntityLockableLoot) {
            ((TileEntityLockableLoot) tile).setLootTable(lootTable, rand.nextLong());
        }
        rec.addChestPoint(pos);
    }

    private void placeSpawner(BlockPos pos, int floor, int tier, RoomTag tag) {
        if (!w.inside(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        SpawnerContext context = new SpawnerContext(source, rec.getCitadelId(), floor, tier, tag);
        if (WaveSpawnerHooks.place(w.getWorld(), pos, context)) {
            rec.addSpawnerPoint(pos, floor, tier);
        }
    }

    // ------------------------------------------------------------------ boss arena

    private void buildBossArena() {
        int f = p.topFloorIndex();
        int y0 = p.floorY(f);
        int h = p.arenaHeight;
        // Panoramic openings between wall pilasters (every 4 blocks), 6 blocks tall.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!isPerimeter(x, z) || isCorner(x, z)) {
                    continue;
                }
                int along = alongWall(x, z);
                boolean pilaster = along % 4 == 0;
                for (int y = y0 + 1; y < y0 + h; y++) {
                    if (pilaster) {
                        w.set(x, y, z, pal.get(PaletteSlot.PILLAR));
                    } else if (along >= 2 && y >= y0 + 3 && y <= y0 + 8) {
                        w.set(x, y, z, window);
                    }
                }
            }
        }
        // Accent cross on the arena floor.
        for (int i = x0 + 2; i <= x1 - 2; i++) {
            if (!isReserved(f, i, cz)) {
                w.set(i, y0, cz, pal.get(PaletteSlot.ACCENT, rand));
            }
        }
        for (int i = z0 + 2; i <= z1 - 2; i++) {
            if (!isReserved(f, cx, i)) {
                w.set(cx, y0, i, pal.get(PaletteSlot.ACCENT, rand));
            }
        }
        // Protective columns: 2x2 on the diagonals, 1x1 on the axes.
        int r = Math.max(4, mid / 2);
        int[][] diagonal = {{cx - r - 1, cz - r - 1}, {cx + r, cz - r - 1}, {cx - r - 1, cz + r}, {cx + r, cz + r}};
        for (int[] d : diagonal) {
            column(f, d[0], d[1], 2, y0 + 1, y0 + h - 1);
        }
        int[][] axis = {{cx - r, cz}, {cx + r, cz}, {cx, cz - r}, {cx, cz + r}};
        for (int[] a : axis) {
            column(f, a[0], a[1], 1, y0 + 1, y0 + h - 1);
        }
        // Central altar where the boss spawns.
        w.fill(cx - 2, y0 + 1, cz - 2, cx + 2, y0 + 1, cz + 2, pal.get(PaletteSlot.ACCENT));
        w.fill(cx - 1, y0 + 2, cz - 1, cx + 1, y0 + 2, cz + 1, pal.get(PaletteSlot.PILLAR));
        w.set(cx - 2, y0 + 2, cz - 2, pal.get(PaletteSlot.LIGHT));
        w.set(cx + 2, y0 + 2, cz - 2, pal.get(PaletteSlot.LIGHT));
        w.set(cx - 2, y0 + 2, cz + 2, pal.get(PaletteSlot.LIGHT));
        w.set(cx + 2, y0 + 2, cz + 2, pal.get(PaletteSlot.LIGHT));
        w.clear(cx - 1, y0 + 3, cz - 1, cx + 1, y0 + 5, cz + 1);
        placeBossAltar(w, new BlockPos(cx, y0 + 2, cz), rec);
        // Ceiling light grid.
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                w.set(x, y0 + h, z, pal.get(PaletteSlot.LIGHT));
            }
        }
        BlockPos altar = new BlockPos(cx, y0 + 3, cz);
        StructureBoundingBox arena = new StructureBoundingBox(x0, y0, z0, x1, y0 + h, z1);
        rec.setBossArena(arena, altar);
        rec.addRoom(arena, RoomTag.BOSS_ARENA, f);
    }

    /** Boss altar block (the boss stands on top of it); configured in memory, no markDirty(). */
    static void placeBossAltar(BlockWriter writer, BlockPos pos, CitadelInstance record) {
        if (ModBlocks.BOSS_ALTAR == null || !writer.set(pos, ModBlocks.BOSS_ALTAR.getDefaultState())) {
            return;
        }
        TileEntity tile = writer.getWorld().getTileEntity(pos);
        if (tile instanceof TileEntityBossAltar) {
            ((TileEntityBossAltar) tile).configure(record.getCitadelId());
        }
    }

    private void column(int floor, int x, int z, int size, int minY, int maxY) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                if (isReserved(floor, x + dx, z + dz)) {
                    return;
                }
            }
        }
        w.fill(x, minY, z, x + size - 1, maxY, z + size - 1, pal.get(PaletteSlot.PILLAR));
        w.set(x, minY, z, pal.get(PaletteSlot.ACCENT));
        w.set(x, maxY - 1, z, pal.get(PaletteSlot.LIGHT));
    }

    // ------------------------------------------------------------------ spire

    private void buildSpire() {
        int base = p.roofY() + 1;
        int top = Math.min(BlockWriter.MAX_Y, p.spireTop);
        if (top <= base + 2) {
            w.set(cx, base, cz, pal.get(PaletteSlot.LIGHT));
            return;
        }
        int[] sizes = {7, 5, 3, 1};
        int total = top - base;
        int tierHeight = Math.max(2, total / sizes.length);
        int y = base;
        for (int t = 0; t < sizes.length && y < top; t++) {
            int half = sizes[t] / 2;
            int tierTop = t == sizes.length - 1 ? top - 1 : Math.min(top - 1, y + tierHeight - 1);
            for (int yy = y; yy <= tierTop; yy++) {
                for (int x = cx - half; x <= cx + half; x++) {
                    for (int z = cz - half; z <= cz + half; z++) {
                        boolean edge = Math.abs(x - cx) == half && Math.abs(z - cz) == half;
                        w.set(x, yy, z, edge ? pal.get(PaletteSlot.PILLAR)
                                : (yy == tierTop ? pal.get(PaletteSlot.ACCENT) : pal.get(PaletteSlot.WALL, rand)));
                    }
                }
            }
            y = tierTop + 1;
        }
        w.set(cx, top, cz, pal.get(PaletteSlot.LIGHT));
    }

    // ------------------------------------------------------------------ helpers

    /** @return {minX, minZ, maxX, maxZ} of the four quadrants, indexed NW, NE, SE, SW. */
    private int[][] quadrants() {
        return new int[][]{
                {x0 + 1, z0 + 1, cx - 1, cz - 1},
                {cx + 1, z0 + 1, x1 - 1, cz - 1},
                {cx + 1, cz + 1, x1 - 1, z1 - 1},
                {x0 + 1, cz + 1, cx - 1, z1 - 1}
        };
    }

    /** Floor difficulty 1-6: regular citadels span 1..5, the Citadel Above the Clouds 2..6. */
    private int difficultyForFloor(int floor) {
        int min = p.sky ? 2 : 1;
        int max = p.sky ? FloorData.MAX_DIFFICULTY : FloorData.MAX_DIFFICULTY - 1;
        int span = Math.max(1, p.floors - 1);
        return FloorData.clampDifficulty(min + Math.round(floor * (max - min) / (float) span));
    }

    private int tierForFloor(int floor) {
        int maxTier = p.sky ? MobTiers.MAX_TIER : MobTiers.MAX_TIER - 1;
        int span = Math.max(1, p.floors - 2);
        return MobTiers.clamp(1 + Math.round(floor * (maxTier - 1) / (float) span));
    }

    private boolean placeable(int floor, int x, int y, int z) {
        return !isReserved(floor, x, z) && !isPerimeter(x, z) && x != cx && z != cz
                && w.inside(x, y, z) && w.get(x, y, z).getMaterial() == Material.AIR;
    }

    private void reserve(int floor, int minX, int minZ, int maxX, int maxZ) {
        reserved.add(new int[]{floor, minX, minZ, maxX, maxZ});
    }

    private boolean isReserved(int floor, int x, int z) {
        for (int[] r : reserved) {
            if (r[0] == floor && x >= r[1] && x <= r[3] && z >= r[2] && z <= r[4]) {
                return true;
            }
        }
        return false;
    }

    private boolean isPerimeter(int x, int z) {
        return x == x0 || x == x1 || z == z0 || z == z1;
    }

    private boolean isCorner(int x, int z) {
        return (x == x0 || x == x1) && (z == z0 || z == z1);
    }

    /** @return distance from the nearest corner along the wall the position lies on. */
    private int alongWall(int x, int z) {
        if (z == z0 || z == z1) {
            return Math.min(x - x0, x1 - x);
        }
        return Math.min(z - z0, z1 - z);
    }
}
