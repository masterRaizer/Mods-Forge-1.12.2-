package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.palette.PaletteSlot;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLeaf;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A gothic manor of about 110 x 140 blocks, explored zone by zone (each zone is a floor index
 * with a horizontal area): forecourt, great hall, library, servants' wing (with wine cellar),
 * inner garden, guest wing (with ballroom), gallery and finally the throne room with the boss.
 * Each zone's way in is sealed until the previous zone is cleared, and its spawners stay asleep
 * until then. Three one-way shortcuts (iron door, lever on the far side) lead back to the hub.
 *
 * <p>Local plan (x east, z south, door to the south): servants' wing x 0-22, library x 23-43,
 * great hall x 44-64, gallery x 65-85, guest wing x 86-108; throne room z 0-24, inner garden
 * z 25-54, main block z 55-85, forecourt z 86-115; wing-end towers beyond z 0 and z 115.
 */
final class ManorBuilder {

    static final int WIDTH = 109;
    static final int DEPTH = 116;
    static final int TOWER_REACH = 11;
    private static final int FH = 7;

    // zone indices, in route order
    static final int FORECOURT = 0;
    static final int GREAT_HALL = 1;
    static final int LIBRARY = 2;
    static final int SERVANTS = 3;
    static final int GARDEN = 4;
    static final int GUESTS = 5;
    static final int GALLERY = 6;
    static final int THRONE = 7;
    private static final String[] ZONE_KEYS = {"forecourt", "great_hall", "library", "servants_wing", "garden",
            "guest_wing", "gallery", "throne_room"};
    private static final int[] ZONE_DIFFICULTY = {1, 2, 2, 3, 3, 4, 4, 5};

    private enum Theme { KITCHEN, DINING, LAUNDRY, SERVANT_ROOM, STORAGE, WINE, BEDROOM, BATH, MUSIC, STUDY, GALLERY, CHAPEL, ARMOURY }

    /** A rectangular building volume in local coordinates (walls included). */
    private static final class Box {
        final int x0;
        final int z0;
        final int x1;
        final int z1;

        Box(int x0, int z0, int x1, int z1) {
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
        }

        boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    private final BlockWriter w;
    private final ManorPlan plan;
    private final Random rand;
    private final CitadelInstance rec;
    private final BlockPalette pal;
    private final int ox;
    private final int oz;
    private final int y0;
    private final IBlockState air = Blocks.AIR.getDefaultState();
    private final IBlockState darkPlanks = Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.DARK_OAK);
    private final IBlockState hedge = Blocks.LEAVES2.getDefaultState().withProperty(BlockNewLeaf.VARIANT, BlockPlanks.EnumType.DARK_OAK)
            .withProperty(BlockLeaves.DECAYABLE, false).withProperty(BlockLeaves.CHECK_DECAY, false);
    private final IBlockState deadLog = Blocks.LOG2.getDefaultState().withProperty(BlockNewLog.VARIANT, BlockPlanks.EnumType.DARK_OAK)
            .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y);
    private final List<Box> buildings = new ArrayList<>();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet reserved = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final RoomFurnisher furnisher;
    private IBlockState window;

    // building volumes
    private final Box servants = new Box(0, 0, 22, DEPTH - 1);
    private final Box guests = new Box(86, 0, 108, DEPTH - 1);
    private final Box library = new Box(23, 55, 43, 85);
    private final Box hall = new Box(44, 55, 64, 85);
    private final Box gallery = new Box(65, 55, 85, 85);
    private final Box throne = new Box(23, 0, 85, 24);

    ManorBuilder(BlockWriter writer, ManorPlan plan, Random random, CitadelInstance record) {
        this.w = writer;
        this.plan = plan;
        this.rand = random;
        this.rec = record;
        this.pal = ModPaletteRegistry.forCitadel(CitadelArchetype.GOTHIC_MANOR, plan.seed);
        this.ox = plan.originX;
        this.oz = plan.originZ;
        this.y0 = plan.baseY;
        this.furnisher = new RoomFurnisher(new RoomFurnisher.Site() {
            @Override
            public boolean free(int x, int y, int z) {
                return w.get(x, y, z).getMaterial() == Material.AIR && !reserved.contains(Blueprint.pack(x, y, z));
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
        }, random, pal, BlockVase.Variant.DARK);
        buildings.add(servants);
        buildings.add(guests);
        buildings.add(library);
        buildings.add(hall);
        buildings.add(gallery);
        buildings.add(throne);
    }

    void build() {
        terraform();
        registerZones();
        // Shells first (walls, slabs, windows, buttresses), then interiors, then grounds, seals, furniture.
        window = glass();
        shell(servants, 2, false);
        window = glass();
        shell(guests, 2, true);
        window = glass();
        shell(library, 3, true);
        shell(hall, 3, true);
        window = glass();
        shell(gallery, 3, true);
        window = glass();
        throneRoom();
        cellar();
        roofs();
        corridorFloors(servants, SERVANTS, new Theme[][]{
                {Theme.KITCHEN, Theme.DINING, Theme.LAUNDRY, Theme.SERVANT_ROOM, Theme.KITCHEN, Theme.STORAGE},
                {Theme.SERVANT_ROOM, Theme.SERVANT_ROOM, Theme.STORAGE, Theme.BATH}}, 0.25F);
        guestWing();
        corridorFloors(gallery, GALLERY, new Theme[][]{
                {Theme.GALLERY, Theme.ARMOURY, Theme.STUDY},
                {Theme.GALLERY, Theme.CHAPEL, Theme.STUDY},
                {Theme.GALLERY, Theme.STUDY, Theme.ARMOURY}}, 0.45F);
        greatHall();
        libraryHall();
        towers();
        forecourt();
        garden();
        zoneDoors();
        shortcuts();
        decorateRooms();
    }

    // ------------------------------------------------------------------ coordinates

    private int x(int lx) {
        return ox + lx;
    }

    private int z(int lz) {
        return oz + lz;
    }

    private void set(int lx, int y, int lz, IBlockState state) {
        w.set(ox + lx, y, oz + lz, state);
    }

    private IBlockState get(int lx, int y, int lz) {
        return w.get(ox + lx, y, oz + lz);
    }

    private boolean insideBuilding(int lx, int lz) {
        for (Box b : buildings) {
            if (b.contains(lx, lz)) {
                return true;
            }
        }
        return false;
    }

    private IBlockState glass() {
        EnumDyeColor[] colours = {EnumDyeColor.BLACK, EnumDyeColor.PURPLE, EnumDyeColor.RED, EnumDyeColor.GRAY, EnumDyeColor.BLUE};
        return Blocks.STAINED_GLASS_PANE.getDefaultState().withProperty(BlockStainedGlassPane.COLOR, colours[rand.nextInt(colours.length)]);
    }

    // ------------------------------------------------------------------ site & zones

    /** Levels the whole site at write time: clears relief above, fills foundations under the buildings. */
    private void terraform() {
        Box forecourt = new Box(23, 86, 85, DEPTH - 1);
        Box garden = new Box(23, 25, 85, 54);
        for (int lx = -2; lx <= WIDTH + 1; lx++) {
            for (int lz = -TOWER_REACH - 1; lz <= DEPTH + TOWER_REACH; lz++) {
                w.clearColumn(x(lx), z(lz), y0 + 1, y0 + 44);
                // Lawn around the buildings so the levelled margin is not bare stone (towers overwrite it).
                if (!insideBuilding(lx, lz) && !forecourt.contains(lx, lz) && !garden.contains(lx, lz)) {
                    set(lx, y0, lz, Blocks.GRASS.getDefaultState());
                    w.fillDown(x(lx), z(lz), y0 - 1, Blocks.DIRT.getDefaultState(), 16);
                }
            }
        }
    }

    private void registerZones() {
        int[][] areas = {
                {23, 86, 85, 115, y0, y0 + 12},
                {44, 55, 64, 85, y0, y0 + 44},
                {23, 55, 43, 85, y0, y0 + 30},
                {0, -TOWER_REACH, 22, DEPTH - 1 + TOWER_REACH, y0 - FH, y0 + 34},
                {23, 25, 85, 54, y0, y0 + 14},
                {86, -TOWER_REACH, 108, DEPTH - 1 + TOWER_REACH, y0, y0 + 34},
                {65, 55, 85, 85, y0, y0 + 30},
                {23, 0, 85, 24, y0, y0 + 24}
        };
        ConfigCache.Snapshot config = ConfigCache.get();
        for (int zone = 0; zone < areas.length; zone++) {
            int[] a = areas[zone];
            boolean boss = zone == THRONE;
            rec.addFloor(zone, a[4], a[5], ZONE_DIFFICULTY[zone], boss ? 1 : config.rollWaveCount(rand), boss)
                    .setArea(x(a[0]), z(a[1]), x(a[2]), z(a[3]))
                    .setZoneKey(ZONE_KEYS[zone])
                    .setRequiresFloor(zone == 0 ? Integer.MIN_VALUE : zone - 1);
        }
    }

    // ------------------------------------------------------------------ shells

    /** Walls with buttresses and tall stained-glass windows, floor slabs, foundation. */
    private void shell(Box b, int floors, boolean foundation) {
        int top = y0 + floors * FH;
        for (int lx = b.x0; lx <= b.x1; lx++) {
            for (int lz = b.z0; lz <= b.z1; lz++) {
                boolean edge = lx == b.x0 || lx == b.x1 || lz == b.z0 || lz == b.z1;
                boolean corner = (lx == b.x0 || lx == b.x1) && (lz == b.z0 || lz == b.z1);
                if (foundation) {
                    w.fillDown(x(lx), z(lz), y0 - 1, pal.get(PaletteSlot.WALL, rand), 16);
                }
                for (int f = 0; f <= floors; f++) {
                    int yf = y0 + f * FH;
                    set(lx, yf, lz, edge ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                }
                if (!edge) {
                    continue;
                }
                boolean alongX = lz == b.z0 || lz == b.z1;
                int along = alongX ? lx - b.x0 : lz - b.z0;
                for (int y = y0 + 1; y < top; y++) {
                    int inFloor = (y - y0) % FH;
                    IBlockState state;
                    if (corner || along % 6 == 0) {
                        state = pal.get(PaletteSlot.PILLAR);
                    } else if (inFloor == 0) {
                        state = pal.get(PaletteSlot.ACCENT, rand);
                    } else if (along % 6 == 3 && inFloor >= 2 && inFloor <= 5) {
                        state = window;
                    } else {
                        state = pal.get(PaletteSlot.WALL, rand);
                    }
                    set(lx, y, lz, state);
                }
                // Buttress outside the wall on exterior faces, capped with a stair.
                if (!corner && along % 6 == 0) {
                    int bx = lx + (lx == b.x0 ? -1 : lx == b.x1 ? 1 : 0);
                    int bz = lz + (lz == b.z0 ? -1 : lz == b.z1 ? 1 : 0);
                    if (!insideBuilding(bx, bz)) {
                        int cap = top - 3;
                        for (int y = y0; y < cap; y++) {
                            set(bx, y, bz, pal.get(PaletteSlot.PILLAR));
                        }
                        EnumFacing out = lx == b.x0 ? EnumFacing.WEST : lx == b.x1 ? EnumFacing.EAST : lz == b.z0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
                        set(bx, cap, bz, pal.stairs(out.getOpposite(), false));
                        w.fillDown(x(bx), z(bz), y0 - 1, pal.get(PaletteSlot.PILLAR), 16);
                    }
                }
            }
        }
        rec.addPart(new StructureBoundingBox(x(b.x0 - 1), y0 - 16, z(b.z0 - 1), x(b.x1 + 1), top + 16, z(b.z1 + 1)));
    }

    private void roofs() {
        gable(servants, y0 + 2 * FH, true);
        gable(guests, y0 + 2 * FH, true);
        Box main = new Box(library.x0, library.z0, gallery.x1, gallery.z1);
        gable(main, y0 + 3 * FH, false);
        gable(throne, y0 + 17, false);
        // Chimneys along the ridges.
        for (Box b : new Box[]{servants, guests}) {
            for (int lz = b.z0 + 12; lz < b.z1 - 6; lz += 26) {
                chimney((b.x0 + b.x1) / 2 + 3, lz, y0 + 2 * FH + 1, 14);
            }
        }
        chimney(library.x0 + 6, main.z0 + 6, y0 + 3 * FH + 1, 18);
        chimney(gallery.x1 - 6, main.z0 + 6, y0 + 3 * FH + 1, 18);
        belfry();
    }

    /** Steep gable roof with stepped stairs, solid gable ends and a ridge course. */
    private void gable(Box b, int baseY, boolean ridgeAlongZ) {
        int span = ridgeAlongZ ? b.x1 - b.x0 : b.z1 - b.z0;
        for (int s = 0; s <= span / 2 + 1; s++) {
            int y = baseY + s;
            int low = (ridgeAlongZ ? b.x0 : b.z0) - 1 + s;
            int high = (ridgeAlongZ ? b.x1 : b.z1) + 1 - s;
            if (low > high) {
                break;
            }
            for (int a = (ridgeAlongZ ? b.z0 : b.x0) - 1; a <= (ridgeAlongZ ? b.z1 : b.x1) + 1; a++) {
                boolean gableEnd = a == (ridgeAlongZ ? b.z0 : b.x0) || a == (ridgeAlongZ ? b.z1 : b.x1);
                if (low == high) {
                    roof(ridgeAlongZ, a, y, low, pal.get(PaletteSlot.WALL, rand));
                    continue;
                }
                roof(ridgeAlongZ, a, y, low, pal.stairs(ridgeAlongZ ? EnumFacing.EAST : EnumFacing.SOUTH, false));
                roof(ridgeAlongZ, a, y, high, pal.stairs(ridgeAlongZ ? EnumFacing.WEST : EnumFacing.NORTH, false));
                if (gableEnd) {
                    for (int c = low + 1; c < high; c++) {
                        roof(ridgeAlongZ, a, y, c, (c - low) % 4 == 2 && s > 2 ? window : pal.get(PaletteSlot.WALL, rand));
                    }
                }
            }
        }
    }

    private void roof(boolean ridgeAlongZ, int a, int y, int c, IBlockState state) {
        if (ridgeAlongZ) {
            set(c, y, a, state);
        } else {
            set(a, y, c, state);
        }
    }

    private void chimney(int lx, int lz, int fromY, int height) {
        for (int y = fromY; y < fromY + height; y++) {
            set(lx, y, lz, pal.get(PaletteSlot.WALL, rand));
            set(lx + 1, y, lz, pal.get(PaletteSlot.WALL, rand));
        }
        set(lx, fromY + height, lz, pal.get(PaletteSlot.ACCENT, rand));
        set(lx + 1, fromY + height, lz, pal.get(PaletteSlot.ACCENT, rand));
    }

    /** Belfry with a pointed spire on the main roof, reached by a ladder from the long gallery. */
    private void belfry() {
        int bx0 = 49;
        int bz0 = 65;
        int base = y0 + 3 * FH;
        int top = base + 16;
        for (int lx = bx0; lx <= bx0 + 10; lx++) {
            for (int lz = bz0; lz <= bz0 + 10; lz++) {
                boolean edge = lx == bx0 || lx == bx0 + 10 || lz == bz0 || lz == bz0 + 10;
                boolean corner = (lx == bx0 || lx == bx0 + 10) && (lz == bz0 || lz == bz0 + 10);
                for (int y = base; y <= top; y++) {
                    if (edge) {
                        boolean arch = !corner && (lx - bx0 == 5 || lz - bz0 == 5 || lx - bx0 == 4 || lx - bx0 == 6
                                || lz - bz0 == 4 || lz - bz0 == 6) && y >= top - 6 && y <= top - 2;
                        set(lx, y, lz, corner ? pal.get(PaletteSlot.PILLAR) : arch ? air : pal.get(PaletteSlot.WALL, rand));
                    } else {
                        set(lx, y, lz, y == base || y == top - 7 ? pal.get(PaletteSlot.FLOOR, rand) : air);
                    }
                }
            }
        }
        // Spire: stepped pyramid of stairs.
        for (int s = 0; s <= 5; s++) {
            int lo = bx0 - 1 + s;
            int hi = bx0 + 11 - s;
            int zlo = bz0 - 1 + s;
            int zhi = bz0 + 11 - s;
            int y = top + 1 + s * 2;
            for (int a = lo; a <= hi; a++) {
                set(a, y, zlo, pal.stairs(EnumFacing.SOUTH, false));
                set(a, y, zhi, pal.stairs(EnumFacing.NORTH, false));
                set(a, y + 1, zlo, pal.get(PaletteSlot.WALL, rand));
                set(a, y + 1, zhi, pal.get(PaletteSlot.WALL, rand));
            }
            for (int c = zlo; c <= zhi; c++) {
                set(lo, y, c, pal.stairs(EnumFacing.EAST, false));
                set(hi, y, c, pal.stairs(EnumFacing.WEST, false));
                set(lo, y + 1, c, pal.get(PaletteSlot.WALL, rand));
                set(hi, y + 1, c, pal.get(PaletteSlot.WALL, rand));
            }
        }
        for (int y = top + 13; y <= top + 18; y++) {
            set(bx0 + 5, y, bz0 + 5, pal.get(PaletteSlot.PILLAR));
        }
        set(bx0 + 5, top + 19, bz0 + 5, pal.get(PaletteSlot.LIGHT));
        // Bell chamber reward and the ladder from the long gallery through the roof.
        StructurePieces.chest(w, rec, new BlockPos(x(bx0 + 5), top - 6, z(bz0 + 5)), EnumFacing.SOUTH,
                ModLootTables.forFloor(5), rand.nextLong());
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.SOUTH);
        for (int y = y0 + 2 * FH + 1; y <= top - 7; y++) {
            if (get(bx0 + 2, y, bz0) .getMaterial() == Material.AIR || y < base) {
                set(bx0 + 2, y, bz0, pal.get(PaletteSlot.PILLAR));
            }
            set(bx0 + 2, y, bz0 + 1, ladder);
        }
        rec.addPart(new StructureBoundingBox(x(bx0), base, z(bz0), x(bx0 + 10), top + 20, z(bz0 + 10)));
    }

    // ------------------------------------------------------------------ interiors

    /**
     * Floors of a long building: a central corridor along z with rooms on both sides, a straight
     * staircase at alternating corridor ends, wooden doors into the rooms.
     */
    private void corridorFloors(Box b, int zone, Theme[][] themesPerFloor, float spawnerChance) {
        int floors = themesPerFloor.length;
        for (int f = 0; f < floors; f++) {
            corridorFloor(b, zone, f, floors, themesPerFloor[f], spawnerChance, b.z0 + 1, b.z1 - 1);
        }
    }

    private void corridorFloor(Box b, int zone, int f, int floors, Theme[] themes, float spawnerChance, int fromZ, int toZ) {
        int yf = y0 + f * FH;
        int c = (b.x0 + b.x1) / 2;
        // Corridor (5 wide) walls with a light every eight blocks in the ceiling and a worn runner.
        for (int lz = fromZ; lz <= toZ; lz++) {
            for (int y = yf + 1; y < yf + FH; y++) {
                set(c - 3, y, lz, pal.get(PaletteSlot.WALL, rand));
                set(c + 3, y, lz, pal.get(PaletteSlot.WALL, rand));
            }
            if ((lz - fromZ) % 8 == 4) {
                set(c + 1, yf + FH, lz, pal.get(PaletteSlot.LIGHT));
            }
            if (rand.nextInt(5) > 0) {
                set(c + 1, yf + 1, lz, Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, EnumDyeColor.RED));
            }
        }
        // Staircase to the next floor at one end of the corridor.
        if (f + 1 < floors) {
            stairs(c, yf, Math.floorMod(f, 2) == 0 ? fromZ : toZ, Math.floorMod(f, 2) == 0 ? 1 : -1);
        }
        // Rooms on both sides, in segments along the corridor.
        int ti = 0;
        for (int side = -1; side <= 1; side += 2) {
            int rx0 = side < 0 ? b.x0 + 1 : c + 4;
            int rx1 = side < 0 ? c - 4 : b.x1 - 1;
            int lz = fromZ;
            while (lz <= toZ) {
                int length = 7 + rand.nextInt(3) * 2;
                int end = Math.min(toZ, lz + length - 1);
                if (toZ - end < 5) {
                    end = toZ;
                }
                // Partition wall closing the segment.
                if (end < toZ) {
                    for (int px = rx0; px <= rx1; px++) {
                        for (int y = yf + 1; y < yf + FH; y++) {
                            set(px, y, end + 1, pal.get(PaletteSlot.WALL, rand));
                        }
                    }
                }
                // Door from the corridor.
                int dz = (lz + end) / 2;
                int dx = side < 0 ? c - 3 : c + 3;
                EnumFacing facing = side < 0 ? EnumFacing.EAST : EnumFacing.WEST;
                door(dx, yf + 1, dz, facing);
                Theme theme = themes[ti++ % themes.length];
                rooms.add(new int[]{rx0, lz, rx1, end, yf, zone, theme.ordinal(), (int) (spawnerChance * 100)});
                lz = end + 2;
            }
        }
    }

    /** {lx0, lz0, lx1, lz1, floorY, zone, theme, spawnerPercent} of every room, furnished last. */
    private final List<int[]> rooms = new ArrayList<>();

    private void stairs(int c, int yf, int startZ, int dir) {
        for (int k = 0; k < FH; k++) {
            int lz = startZ + dir * k;
            int y = yf + 1 + k;
            for (int lx = c - 1; lx <= c; lx++) {
                if (k > 0) {
                    for (int fy = yf + 1; fy < y; fy++) {
                        set(lx, fy, lz, pal.get(PaletteSlot.WALL, rand));
                    }
                }
                set(lx, y, lz, pal.stairs(dir > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH, false));
                for (int h = 1; h <= 3; h++) {
                    if (y + h <= yf + FH || k >= 4) {
                        set(lx, y + h, lz, air);
                    }
                }
                reserved.add(Blueprint.pack(x(lx), y + 1, z(lz)));
            }
        }
    }

    private void door(int lx, int y, int lz, EnumFacing facing) {
        IBlockState lower = Blocks.DARK_OAK_DOOR.getDefaultState().withProperty(BlockDoor.FACING, facing)
                .withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER).withProperty(BlockDoor.OPEN, rand.nextInt(3) == 0);
        set(lx, y, lz, lower);
        set(lx, y + 1, lz, lower.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER));
        for (EnumFacing side : new EnumFacing[]{facing, facing.getOpposite()}) {
            reserved.add(Blueprint.pack(x(lx + side.getXOffset()), y, z(lz + side.getZOffset())));
        }
    }

    private void cellar() {
        // Wine cellar under the servants' wing, reached by the corridor stairs from the ground floor.
        int yc = y0 - FH;
        for (int lx = servants.x0; lx <= servants.x1; lx++) {
            for (int lz = servants.z0; lz <= servants.z1; lz++) {
                boolean edge = lx == servants.x0 || lx == servants.x1 || lz == servants.z0 || lz == servants.z1;
                set(lx, yc, lz, edge ? pal.get(PaletteSlot.WALL, rand) : pal.get(PaletteSlot.FLOOR, rand));
                w.fillDown(x(lx), z(lz), yc - 1, pal.get(PaletteSlot.WALL, rand), 8);
                for (int y = yc + 1; y < y0; y++) {
                    set(lx, y, lz, edge ? pal.get(PaletteSlot.WALL, rand) : air);
                }
            }
        }
        int c = (servants.x0 + servants.x1) / 2;
        // Cellar corridor floor (f = -1): its stairs climb to the ground floor at the far end.
        int yf = yc;
        for (int lz = servants.z0 + 1; lz <= servants.z1 - 1; lz++) {
            for (int y = yf + 1; y < yf + FH; y++) {
                set(c - 3, y, lz, pal.get(PaletteSlot.WALL, rand));
                set(c + 3, y, lz, pal.get(PaletteSlot.WALL, rand));
            }
            if ((lz - servants.z0) % 10 == 5) {
                set(c, yf + FH - 1, lz, pal.get(PaletteSlot.LIGHT));
            }
        }
        stairs(c, yf, servants.z1 - 1, -1);
        for (int side = -1; side <= 1; side += 2) {
            int rx0 = side < 0 ? servants.x0 + 1 : c + 4;
            int rx1 = side < 0 ? c - 4 : servants.x1 - 1;
            int lz = servants.z0 + 1;
            while (lz <= servants.z1 - 9) {
                int end = lz + 8 + rand.nextInt(3) * 2;
                end = Math.min(end, servants.z1 - 9);
                for (int px = rx0; px <= rx1; px++) {
                    for (int y = yf + 1; y < yf + FH; y++) {
                        set(px, y, end + 1, pal.get(PaletteSlot.WALL, rand));
                    }
                }
                int dz = (lz + end) / 2;
                set(side < 0 ? c - 3 : c + 3, yf + 1, dz, air);
                set(side < 0 ? c - 3 : c + 3, yf + 2, dz, air);
                rooms.add(new int[]{rx0, lz, rx1, end, yf, SERVANTS, (rand.nextInt(3) > 0 ? Theme.WINE : Theme.STORAGE).ordinal(), 30});
                lz = end + 2;
            }
        }
    }

    /** Guest wing: a ballroom fills the southern third of the ground floor, rooms elsewhere. */
    private void guestWing() {
        int ballFrom = guests.z1 - 36;
        corridorFloor(guests, GUESTS, 0, 2, new Theme[]{Theme.MUSIC, Theme.STUDY, Theme.BATH, Theme.BEDROOM}, 0.25F,
                guests.z0 + 1, ballFrom - 2);
        corridorFloor(guests, GUESTS, 1, 2, new Theme[]{Theme.BEDROOM, Theme.BEDROOM, Theme.BATH, Theme.STUDY}, 0.25F,
                guests.z0 + 1, guests.z1 - 1);
        // Wall between the rooms and the ballroom, with a double door from the corridor.
        int c = (guests.x0 + guests.x1) / 2;
        for (int lx = guests.x0 + 1; lx <= guests.x1 - 1; lx++) {
            for (int y = y0 + 1; y < y0 + FH; y++) {
                set(lx, y, ballFrom - 1, Math.abs(lx - c) <= 1 && y <= y0 + 3 ? air : pal.get(PaletteSlot.WALL, rand));
            }
        }
        // Ballroom: checkered dance floor, columns, chandeliers, a gallery for musicians.
        for (int lx = guests.x0 + 1; lx <= guests.x1 - 1; lx++) {
            for (int lz = ballFrom; lz <= guests.z1 - 1; lz++) {
                set(lx, y0, lz, ((lx + lz) & 1) == 0 ? pal.get(PaletteSlot.FLOOR) : pal.get(PaletteSlot.ACCENT));
                boolean column = (lx == guests.x0 + 4 || lx == guests.x1 - 4) && (lz - ballFrom) % 6 == 3;
                for (int y = y0 + 1; y < y0 + FH; y++) {
                    set(lx, y, lz, column ? pal.get(PaletteSlot.PILLAR) : air);
                }
            }
        }
        for (int lz = ballFrom + 6; lz < guests.z1 - 3; lz += 12) {
            chandelier(c, y0 + FH - 1, lz, 2);
        }
        for (int i = 0; i < 3; i++) {
            spawnerAt(c + (i - 1) * 5, y0 + 1, ballFrom + 8 + i * 9, GUESTS, RoomTag.GUARD_ROOM);
        }
        StructurePieces.chest(w, rec, new BlockPos(x(c), y0 + 1, z(guests.z1 - 2)), EnumFacing.NORTH,
                ModLootTables.forFloor(4), rand.nextLong());
    }

    private void chandelier(int lx, int ceilingY, int lz, int drop) {
        for (int d = 0; d < drop; d++) {
            set(lx, ceilingY - d, lz, Blocks.DARK_OAK_FENCE.getDefaultState());
        }
        int y = ceilingY - drop;
        set(lx, y, lz, pal.get(PaletteSlot.LIGHT));
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            set(lx + side.getXOffset(), y, lz + side.getZOffset(), Blocks.DARK_OAK_FENCE.getDefaultState());
        }
    }

    /** Great hall: double height with a gallery, twin staircases, pillars, carpet, chandeliers. */
    private void greatHall() {
        doubleHall(hall);
        int c = (hall.x0 + hall.x1) / 2;
        for (int lz = hall.z0 + 2; lz <= hall.z1 - 1; lz++) {
            for (int lx = c - 1; lx <= c + 1; lx++) {
                set(lx, y0 + 1, lz, Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, EnumDyeColor.RED));
            }
        }
        for (int lz = hall.z0 + 8; lz < hall.z1 - 4; lz += 10) {
            chandelier(c, y0 + 2 * FH - 1, lz, 3);
        }
        for (int i = 0; i < 3; i++) {
            spawnerAt(c + (i == 1 ? 0 : (i == 0 ? -5 : 5)), y0 + 1, hall.z0 + 10 + i * 6, GREAT_HALL, RoomTag.ENTRANCE_HALL);
        }
        longGallery(hall, GREAT_HALL);
    }

    /** Two-storey library: rows of shelves on the floor, shelves along the gallery walls. */
    private void libraryHall() {
        doubleHall(library);
        for (int lz = library.z0 + 4; lz <= library.z1 - 4; lz += 3) {
            for (int lx = library.x0 + 5; lx <= library.x1 - 5; lx++) {
                if (Math.abs(lx - (library.x0 + library.x1) / 2) <= 1) {
                    continue; // central aisle
                }
                for (int y = y0 + 1; y <= y0 + 3; y++) {
                    set(lx, y, lz, Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
        for (int lx = library.x0 + 1; lx <= library.x1 - 1; lx++) {
            for (int y = y0 + FH + 1; y <= y0 + FH + 3; y++) {
                if (get(lx, y, library.z0 + 1).getMaterial() == Material.AIR) {
                    set(lx, y, library.z0 + 1, Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
        int c = (library.x0 + library.x1) / 2;
        set(c, y0 + 1, library.z0 + 2, Blocks.ENCHANTING_TABLE.getDefaultState());
        chandelier(c, y0 + 2 * FH - 1, (library.z0 + library.z1) / 2, 3);
        for (int i = 0; i < 2; i++) {
            spawnerAt(c, y0 + 1, library.z0 + 9 + i * 12, LIBRARY, RoomTag.LIBRARY);
        }
        StructurePieces.chest(w, rec, new BlockPos(x(c + 2), y0 + FH + 1, z(library.z0 + 2)), EnumFacing.SOUTH,
                ModLootTables.forFloor(3), rand.nextLong());
        longGallery(library, LIBRARY);
    }

    /**
     * Floors 0 and 1 of a main-block section become one hall with a 3-wide gallery at floor-1
     * height and staircases along both side walls; a straight stair climbs from the gallery to
     * floor 2.
     */
    private void doubleHall(Box b) {
        int galleryY = y0 + FH;
        for (int lx = b.x0 + 1; lx <= b.x1 - 1; lx++) {
            for (int lz = b.z0 + 1; lz <= b.z1 - 1; lz++) {
                boolean ring = lx <= b.x0 + 3 || lx >= b.x1 - 3 || lz <= b.z0 + 3 || lz >= b.z1 - 3;
                set(lx, galleryY, lz, ring ? pal.get(PaletteSlot.FLOOR, rand) : air);
                boolean railing = ring && (lx == b.x0 + 3 || lx == b.x1 - 3 || lz == b.z0 + 3 || lz == b.z1 - 3);
                if (railing) {
                    set(lx, galleryY + 1, lz, Blocks.DARK_OAK_FENCE.getDefaultState());
                }
            }
        }
        // Pillars carrying the gallery.
        for (int lx = b.x0 + 4; lx <= b.x1 - 4; lx += b.x1 - b.x0 - 8) {
            for (int lz = b.z0 + 4; lz <= b.z1 - 4; lz += 4) {
                for (int y = y0 + 1; y < galleryY; y++) {
                    set(lx, y, lz, pal.get(PaletteSlot.PILLAR));
                }
            }
        }
        // Twin staircases from the front of the hall up to the gallery, along both side walls.
        for (int side = 0; side < 2; side++) {
            int lx0 = side == 0 ? b.x0 + 1 : b.x1 - 2;
            for (int k = 0; k < FH; k++) {
                int lz = b.z1 - 5 - k;
                int y = y0 + 1 + k;
                for (int lx = lx0; lx <= lx0 + 1; lx++) {
                    for (int fy = y0 + 1; fy < y; fy++) {
                        set(lx, fy, lz, pal.get(PaletteSlot.WALL, rand));
                    }
                    set(lx, y, lz, pal.stairs(EnumFacing.NORTH, false));
                    for (int h = 1; h <= 3; h++) {
                        set(lx, y + h, lz, air);
                    }
                }
            }
        }
        // From the gallery to floor 2, along the north wall.
        for (int k = 0; k < FH; k++) {
            int lx = b.x0 + 4 + k;
            int y = galleryY + 1 + k;
            for (int lz = b.z0 + 1; lz <= b.z0 + 2; lz++) {
                for (int fy = galleryY + 1; fy < y; fy++) {
                    set(lx, fy, lz, pal.get(PaletteSlot.WALL, rand));
                }
                set(lx, y, lz, pal.stairs(EnumFacing.EAST, false));
                for (int h = 1; h <= 3; h++) {
                    set(lx, y + h, lz, air);
                }
            }
        }
    }

    /** Floor 2 of a main-block section: one long gallery hall with banners, pedestals and loot. */
    private void longGallery(Box b, int zone) {
        int yf = y0 + 2 * FH;
        int c = (b.x0 + b.x1) / 2;
        for (int lz = b.z0 + 4; lz <= b.z1 - 3; lz += 4) {
            pedestal(b.x0 + 2, yf + 1, lz);
            pedestal(b.x1 - 2, yf + 1, lz);
        }
        chandelier(c, yf + FH - 1, (b.z0 + b.z1) / 2, 2);
        spawnerAt(c, yf + 1, (b.z0 + b.z1) / 2 + 3, zone, RoomTag.TREASURY);
        StructurePieces.chest(w, rec, new BlockPos(x(c), yf + 1, z(b.z1 - 2)), EnumFacing.NORTH,
                ModLootTables.forFloor(ZONE_DIFFICULTY[zone] + 1), rand.nextLong());
    }

    private void pedestal(int lx, int y, int lz) {
        set(lx, y, lz, pal.get(PaletteSlot.ACCENT, rand));
        set(lx, y + 1, lz, ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT,
                rand.nextInt(6) == 0 ? BlockVase.Variant.GILDED : BlockVase.Variant.DARK));
    }

    // ------------------------------------------------------------------ throne room

    private void throneRoom() {
        Box b = throne;
        int top = y0 + 17;
        for (int lx = b.x0; lx <= b.x1; lx++) {
            for (int lz = b.z0; lz <= b.z1; lz++) {
                boolean edge = lx == b.x0 || lx == b.x1 || lz == b.z0 || lz == b.z1;
                boolean corner = (lx == b.x0 || lx == b.x1) && (lz == b.z0 || lz == b.z1);
                w.fillDown(x(lx), z(lz), y0 - 1, pal.get(PaletteSlot.WALL, rand), 16);
                set(lx, y0, lz, edge ? pal.get(PaletteSlot.WALL, rand) : ((lx + lz) % 4 == 0 ? pal.get(PaletteSlot.ACCENT) : pal.get(PaletteSlot.FLOOR, rand)));
                set(lx, top, lz, pal.get(PaletteSlot.WALL, rand));
                if (!edge) {
                    continue;
                }
                boolean alongX = lz == b.z0 || lz == b.z1;
                int along = alongX ? lx - b.x0 : lz - b.z0;
                for (int y = y0 + 1; y < top; y++) {
                    boolean tall = along % 6 == 3 && y >= y0 + 3 && y <= y0 + 13;
                    set(lx, y, lz, corner || along % 6 == 0 ? pal.get(PaletteSlot.PILLAR) : tall ? window : pal.get(PaletteSlot.WALL, rand));
                }
            }
        }
        int c = (b.x0 + b.x1) / 2;
        // Two rows of columns, carpet from the door to the dais.
        for (int lx = b.x0 + 6; lx <= b.x1 - 6; lx += 6) {
            for (int lz : new int[]{b.z0 + 6, b.z1 - 6}) {
                for (int y = y0 + 1; y < top; y++) {
                    set(lx, y, lz, pal.get(PaletteSlot.PILLAR));
                }
            }
        }
        for (int lz = b.z0 + 5; lz <= b.z1 - 1; lz++) {
            for (int lx = c - 1; lx <= c + 1; lx++) {
                set(lx, y0 + 1, lz, Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, EnumDyeColor.RED));
            }
        }
        // Dais and throne at the north end.
        for (int lx = c - 7; lx <= c + 7; lx++) {
            for (int lz = b.z0 + 1; lz <= b.z0 + 4; lz++) {
                set(lx, y0 + 1, lz, pal.get(PaletteSlot.FLOOR, rand));
                if (lz <= b.z0 + 2) {
                    set(lx, y0 + 2, lz, pal.get(PaletteSlot.FLOOR, rand));
                }
            }
        }
        set(c, y0 + 3, b.z0 + 1, pal.stairs(EnumFacing.NORTH, false));
        for (int y = y0 + 3; y <= y0 + 8; y++) {
            set(c - 1, y, b.z0 + 1, pal.get(PaletteSlot.PILLAR));
            set(c + 1, y, b.z0 + 1, pal.get(PaletteSlot.PILLAR));
        }
        for (int y = y0 + 4; y <= y0 + 9; y++) {
            set(c, y, b.z0 + 1, pal.get(PaletteSlot.ACCENT));
        }
        for (int lx = b.x0 + 9; lx < b.x1 - 6; lx += 12) {
            chandelier(lx, top - 1, (b.z0 + b.z1) / 2, 4);
        }
        BlockPos altar = new BlockPos(x(c), y0 + 1, z(b.z0 + 8));
        CitadelBuilder.placeBossAltar(w, altar, rec);
        StructureBoundingBox arena = new StructureBoundingBox(x(b.x0 + 1), y0, z(b.z0 + 1), x(b.x1 - 1), top - 1, z(b.z1 - 1));
        rec.setBossArena(arena, altar.up());
        rec.addRoom(arena, RoomTag.BOSS_ARENA, THRONE);
        StructurePieces.chest(w, rec, new BlockPos(x(c + 3), y0 + 3, z(b.z0 + 1)), EnumFacing.SOUTH,
                ModLootTables.BOSS_CHEST_NORMAL, rand.nextLong());
        rec.addPart(new StructureBoundingBox(x(b.x0 - 1), y0 - 16, z(b.z0 - 1), x(b.x1 + 1), top + 16, z(b.z1 + 1)));
    }

    // ------------------------------------------------------------------ towers

    private void towers() {
        tower(6, -TOWER_REACH, servants, true, SERVANTS);
        tower(6, DEPTH, servants, false, SERVANTS);
        tower(92, -TOWER_REACH, guests, true, GUESTS);
        tower(92, DEPTH, guests, false, GUESTS);
    }

    /**
     * An 11x11 tower against a wing end: spiral staircase round a 3x3 core, landings at the wing's
     * floor heights with doors through both walls, crenellated top with an archer and a reward.
     */
    private void tower(int tx, int tz, Box wing, boolean north, int zone) {
        int top = y0 + 30;
        for (int dx = 0; dx <= 10; dx++) {
            for (int dz = 0; dz <= 10; dz++) {
                int lx = tx + dx;
                int lz = tz + dz;
                int ring = Math.max(Math.abs(dx - 5), Math.abs(dz - 5));
                w.fillDown(x(lx), z(lz), y0 - 1, pal.get(PaletteSlot.WALL, rand), 16);
                set(lx, y0, lz, pal.get(PaletteSlot.FLOOR, rand));
                for (int y = y0 + 1; y < top; y++) {
                    IBlockState state;
                    if (ring == 5) {
                        boolean corner = Math.abs(dx - 5) == 5 && Math.abs(dz - 5) == 5;
                        boolean slit = !corner && (dx == 5 || dz == 5) && (y - y0) % 6 == 4;
                        state = corner ? pal.get(PaletteSlot.PILLAR) : slit ? window : pal.get(PaletteSlot.WALL, rand);
                    } else if (ring <= 1) {
                        state = pal.get(PaletteSlot.PILLAR);
                    } else {
                        boolean landing = ring >= 3 && ((y - y0) % FH == 0) && y <= y0 + FH * 2;
                        state = landing ? pal.get(PaletteSlot.FLOOR, rand) : air;
                    }
                    set(lx, y, lz, state);
                }
                set(lx, top, lz, pal.get(PaletteSlot.FLOOR, rand));
                if (ring == 5 && (dx + dz) % 2 == 0) {
                    set(lx, top + 1, lz, pal.get(PaletteSlot.WALL, rand));
                }
            }
        }
        // Spiral of stairs on the ring at distance 2 around the core.
        int[][] path = spiralPath();
        for (int i = 0; y0 + 1 + i <= top; i++) {
            int[] p = path[i % path.length];
            int[] next = path[(i + 1) % path.length];
            EnumFacing toward = EnumFacing.getFacingFromVector(next[0] - p[0], 0, next[1] - p[1]);
            set(tx + 5 + p[0], y0 + 1 + i, tz + 5 + p[1], pal.stairs(toward, false));
        }
        // Headroom through the top platform over the last steps (the final step sits in the platform).
        for (int i = top - y0 - 4; i <= top - y0 - 2; i++) {
            int[] p = path[i % path.length];
            set(tx + 5 + p[0], top, tz + 5 + p[1], air);
        }
        // Doors from the wing corridor's side lane into the tower, on each wing floor.
        int c = (wing.x0 + wing.x1) / 2;
        int wallZ = north ? wing.z0 : wing.z1;
        int towerWall = north ? tz + 10 : tz;
        for (int f = 0; f < 2; f++) {
            int yf = y0 + f * FH;
            for (int lz : new int[]{wallZ, towerWall}) {
                set(c + 1, yf + 1, lz, air);
                set(c + 1, yf + 2, lz, air);
            }
            int inner = north ? towerWall - 1 : towerWall + 1;
            set(c + 1, yf, inner, pal.get(PaletteSlot.FLOOR, rand));
        }
        spawnerAt(tx + 2, top + 1, tz + 2, zone, RoomTag.GUARD_ROOM);
        StructurePieces.chest(w, rec, new BlockPos(x(tx + 8), top + 1, z(tz + 8)), EnumFacing.NORTH,
                ModLootTables.forFloor(ZONE_DIFFICULTY[zone] + 1), rand.nextLong());
        rec.addPart(new StructureBoundingBox(x(tx), y0 - 16, z(tz), x(tx + 10), top + 1, z(tz + 10)));
    }

    /** @return the 16 cells of the square ring at distance 2 around the centre, in walking order. */
    private static int[][] spiralPath() {
        int[][] path = new int[16][];
        int n = 0;
        for (int i = -2; i < 2; i++) {
            path[n++] = new int[]{i, -2};
        }
        for (int i = -2; i < 2; i++) {
            path[n++] = new int[]{2, i};
        }
        for (int i = 2; i > -2; i--) {
            path[n++] = new int[]{i, 2};
        }
        for (int i = 2; i > -2; i--) {
            path[n++] = new int[]{-2, i};
        }
        return path;
    }

    // ------------------------------------------------------------------ grounds

    private void forecourt() {
        int c = 54;
        for (int lx = 23; lx <= 85; lx++) {
            for (int lz = 86; lz <= DEPTH - 1; lz++) {
                boolean path = Math.abs(lx - c) <= 2;
                set(lx, y0, lz, path ? pal.get(PaletteSlot.FLOOR, rand) : Blocks.GRASS.getDefaultState());
                w.fillDown(x(lx), z(lz), y0 - 1, Blocks.DIRT.getDefaultState(), 16);
                boolean border = lx == 24 || lx == 84;
                if (border && lz < DEPTH - 2) {
                    set(lx, y0 + 1, lz, hedge);
                }
            }
        }
        // Iron fence with stone posts and an open gate.
        for (int lx = 23; lx <= 85; lx++) {
            if (Math.abs(lx - c) <= 2) {
                continue;
            }
            set(lx, y0 + 1, DEPTH - 1, pal.get(PaletteSlot.WALL, rand));
            boolean post = (lx - 23) % 6 == 0;
            set(lx, y0 + 2, DEPTH - 1, post ? pal.get(PaletteSlot.PILLAR) : Blocks.IRON_BARS.getDefaultState());
            set(lx, y0 + 3, DEPTH - 1, post ? pal.get(PaletteSlot.LIGHT) : Blocks.IRON_BARS.getDefaultState());
        }
        fountain(c, 100);
        for (int lz = 90; lz < DEPTH - 3; lz += 8) {
            lampPost(c - 4, lz);
            lampPost(c + 4, lz);
            deadTree(c - 18 + rand.nextInt(6), lz + rand.nextInt(4));
            deadTree(c + 14 + rand.nextInt(6), lz + rand.nextInt(4));
        }
        spawnerAt(c - 10, y0 + 1, 95, FORECOURT, RoomTag.GUARD_ROOM);
        spawnerAt(c + 10, y0 + 1, 95, FORECOURT, RoomTag.GUARD_ROOM);
        spawnerAt(c, y0 + 1, 108, FORECOURT, RoomTag.GUARD_ROOM);
    }

    private void garden() {
        int c = 54;
        int midZ = 40;
        for (int lx = 23; lx <= 85; lx++) {
            for (int lz = 25; lz <= 54; lz++) {
                boolean path = Math.abs(lx - c) <= 1 || Math.abs(lz - midZ) <= 1;
                set(lx, y0, lz, path ? (rand.nextInt(5) == 0 ? Blocks.GRAVEL.getDefaultState() : pal.get(PaletteSlot.FLOOR, rand))
                        : Blocks.GRASS.getDefaultState());
                w.fillDown(x(lx), z(lz), y0 - 1, Blocks.DIRT.getDefaultState(), 16);
                boolean bedEdge = !path && (Math.abs(lx - c) == 2 || Math.abs(lz - midZ) == 2 || lx == 25 || lx == 83 || lz == 27 || lz == 52);
                if (bedEdge && rand.nextInt(7) > 0) {
                    set(lx, y0 + 1, lz, hedge);
                }
            }
        }
        fountain(c, midZ);
        // Graves, dead trees, a gazebo and guardians in the four beds.
        for (int[] bed : new int[][]{{38, 32}, {70, 32}, {38, 47}, {70, 47}}) {
            for (int i = 0; i < 4; i++) {
                int gx = bed[0] - 6 + rand.nextInt(12);
                int gz = bed[1] - 3 + rand.nextInt(6);
                if (get(gx, y0 + 1, gz).getMaterial() == Material.AIR) {
                    set(gx, y0 + 1, gz, rand.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : pal.get(PaletteSlot.ACCENT, rand));
                }
            }
            deadTree(bed[0] + 4, bed[1]);
            spawnerAt(bed[0], y0 + 1, bed[1], GARDEN, RoomTag.UNDERGROUND_LAIR);
        }
        gazebo(76, 28);
        StructurePieces.chest(w, rec, new BlockPos(x(78), y0 + 1, z(30)), EnumFacing.WEST, ModLootTables.forFloor(3), rand.nextLong());
    }

    private void fountain(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d == 3) {
                    set(cx + dx, y0 + 1, cz + dz, rand.nextInt(6) == 0 ? air : pal.get(PaletteSlot.WALL, rand));
                } else {
                    set(cx + dx, y0, cz + dz, Blocks.WATER.getDefaultState());
                    set(cx + dx, y0 - 1, cz + dz, pal.get(PaletteSlot.WALL, rand));
                }
            }
        }
        for (int y = y0; y <= y0 + 3; y++) {
            set(cx, y, cz, pal.get(PaletteSlot.PILLAR));
        }
        set(cx, y0 + 4, cz, pal.get(PaletteSlot.LIGHT));
    }

    private void lampPost(int lx, int lz) {
        if (get(lx, y0 + 1, lz).getMaterial() != Material.AIR) {
            return;
        }
        set(lx, y0 + 1, lz, pal.get(PaletteSlot.WALL, rand));
        set(lx, y0 + 2, lz, Blocks.DARK_OAK_FENCE.getDefaultState());
        set(lx, y0 + 3, lz, Blocks.DARK_OAK_FENCE.getDefaultState());
        set(lx, y0 + 4, lz, pal.get(PaletteSlot.LIGHT));
    }

    private void deadTree(int lx, int lz) {
        if (get(lx, y0 + 1, lz).getMaterial() != Material.AIR) {
            return;
        }
        int height = 4 + rand.nextInt(4);
        for (int y = y0 + 1; y <= y0 + height; y++) {
            set(lx, y, lz, deadLog);
        }
        EnumFacing branch = EnumFacing.byHorizontalIndex(rand.nextInt(4));
        set(lx + branch.getXOffset(), y0 + height - 1, lz + branch.getZOffset(),
                deadLog.withProperty(BlockLog.LOG_AXIS, branch.getAxis() == EnumFacing.Axis.X ? BlockLog.EnumAxis.X : BlockLog.EnumAxis.Z));
    }

    private void gazebo(int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(cx + dx, y0, cz + dz, darkPlanks);
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (corner) {
                    for (int y = y0 + 1; y <= y0 + 3; y++) {
                        set(cx + dx, y, cz + dz, Blocks.DARK_OAK_FENCE.getDefaultState());
                    }
                }
                set(cx + dx, y0 + 4, cz + dz, Blocks.WOODEN_SLAB.getDefaultState());
            }
        }
    }

    // ------------------------------------------------------------------ zone seals and shortcuts

    private void zoneDoors() {
        // Forecourt -> great hall: grand door.
        sealOpening(52, 56, y0 + 1, y0 + 5, 85, 85, FORECOURT, false);
        // Great hall -> library, library -> servants' wing (double walls: open both, seal the far one).
        openWall(43, 44, 68, 70, true);
        sealOpening(43, 43, y0 + 1, y0 + 3, 68, 70, GREAT_HALL, true);
        openWall(22, 23, 64, 66, true);
        sealOpening(22, 22, y0 + 1, y0 + 3, 64, 66, LIBRARY, true);
        // Servants' wing -> inner garden -> guest wing.
        sealOpening(22, 22, y0 + 1, y0 + 3, 38, 40, SERVANTS, true);
        sealOpening(86, 86, y0 + 1, y0 + 3, 38, 40, GARDEN, true);
        // Guest wing -> gallery.
        openWall(85, 86, 72, 74, true);
        sealOpening(85, 85, y0 + 1, y0 + 3, 72, 74, GUESTS, true);
        // Inner garden -> throne room.
        sealOpening(52, 56, y0 + 1, y0 + 5, 24, 24, GALLERY, false);
    }

    /** Clears a doorway through the walls between {@code a} and {@code b} (x columns) or rows. */
    private void openWall(int a, int b, int from, int to, boolean wallsAlongZ) {
        for (int p = a; p <= b; p++) {
            for (int q = from; q <= to; q++) {
                for (int y = y0 + 1; y <= y0 + 3; y++) {
                    if (wallsAlongZ) {
                        set(p, y, q, air);
                    } else {
                        set(q, y, p, air);
                    }
                }
            }
        }
    }

    /** Fills an opening with sealed gates that open when {@code requiredZone} is cleared. */
    private void sealOpening(int lx0, int lx1, int yLow, int yHigh, int lz0, int lz1, int requiredZone, boolean wallAlongZ) {
        boolean lock = ConfigCache.get().isLockFloorsUntilCleared();
        for (int lx = lx0; lx <= lx1; lx++) {
            for (int lz = lz0; lz <= lz1; lz++) {
                for (int y = yLow; y <= yHigh; y++) {
                    if (!lock) {
                        set(lx, y, lz, air);
                        continue;
                    }
                    set(lx, y, lz, ModBlocks.SEALED_GATE.getDefaultState());
                    rec.addGate(new BlockPos(x(lx), y, z(lz)), requiredZone, Block.getStateId(air));
                }
            }
        }
    }

    /**
     * One-way shortcuts back towards the hub: an iron door only a lever on the far side opens,
     * and a sealed gate on the near side until the far zone has been cleared.
     */
    private void shortcuts() {
        shortcut(22, y0 + 1, 100, EnumFacing.WEST, SERVANTS);
        shortcut(54, y0 + 1, 55, EnumFacing.NORTH, GARDEN);
        shortcut(65, y0 + 2 * FH + 1, 60, EnumFacing.EAST, GALLERY);
    }

    /** @param farSide direction from the door towards the zone that holds the lever */
    private void shortcut(int lx, int y, int lz, EnumFacing farSide, int farZone) {
        IBlockState lower = Blocks.IRON_DOOR.getDefaultState().withProperty(BlockDoor.FACING, farSide.getOpposite())
                .withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER);
        set(lx, y, lz, lower);
        set(lx, y + 1, lz, lower.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER));
        // Lever on the floor beside the door, far side.
        int leverX = lx + farSide.getXOffset() + (farSide.getAxis() == EnumFacing.Axis.Z ? 1 : 0);
        int leverZ = lz + farSide.getZOffset() + (farSide.getAxis() == EnumFacing.Axis.X ? 1 : 0);
        set(lx + farSide.getXOffset(), y, lz + farSide.getZOffset(), air);
        set(lx + farSide.getXOffset(), y + 1, lz + farSide.getZOffset(), air);
        set(leverX, y, leverZ, Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING,
                farSide.getAxis() == EnumFacing.Axis.X ? BlockLever.EnumOrientation.UP_Z : BlockLever.EnumOrientation.UP_X));
        reserved.add(Blueprint.pack(x(leverX), y, z(leverZ)));
        // Near side: the cell in front of the door is sealed until the far zone is cleared.
        int nx = lx - farSide.getXOffset();
        int nz = lz - farSide.getZOffset();
        if (ConfigCache.get().isLockFloorsUntilCleared()) {
            for (int h = 0; h <= 1; h++) {
                set(nx, y + h, nz, ModBlocks.SEALED_GATE.getDefaultState());
                rec.addGate(new BlockPos(x(nx), y + h, z(nz)), farZone, Block.getStateId(air));
            }
        } else {
            set(nx, y, nz, air);
            set(nx, y + 1, nz, air);
        }
    }

    // ------------------------------------------------------------------ rooms

    private void decorateRooms() {
        for (int[] r : rooms) {
            Theme theme = Theme.values()[r[6]];
            int lx0 = r[0];
            int lz0 = r[1];
            int lx1 = r[2];
            int lz1 = r[3];
            int yf = r[4];
            int zone = r[5];
            int wx0 = x(lx0);
            int wz0 = z(lz0);
            int wx1 = x(lx1);
            int wz1 = z(lz1);
            RoomTag tag = furnish(theme, wx0, wz0, wx1, wz1, yf);
            rec.addRoom(new StructureBoundingBox(wx0, yf, wz0, wx1, yf + FH - 1, wz1), tag, zone);
            if (rand.nextInt(100) < r[7]) {
                spawnerWorld((wx0 + wx1) / 2, yf + 1, (wz0 + wz1) / 2, zone, tag);
            }
            if (rand.nextFloat() < 0.45F) {
                furnisher.chestAgainstWall(wx0, wz0, wx1, wz1, yf, ModLootTables.forFloor(ZONE_DIFFICULTY[zone]));
            }
            furnisher.vases(wx0, wz0, wx1, wz1, yf, 1 + rand.nextInt(2));
            furnisher.abandon(wx0, wz0, wx1, wz1, yf, FH);
        }
    }

    /** Furnishes one room in world coordinates and returns its tag for wave composition. */
    private RoomTag furnish(Theme theme, int x0, int z0, int x1, int z1, int yf) {
        int y = yf + 1;
        switch (theme) {
            case KITCHEN:
                furnisher.furnish(RoomTag.PANTRY, x0, z0, x1, z1, yf);
                return RoomTag.PANTRY;
            case STORAGE:
            case WINE:
                for (int x = x0; x <= x1; x += 2) {
                    putIfFree(x, y, z0, theme == Theme.WINE ? ModPaletteRegistry.resolveDecor("futuremc:barrel", "minecraft:chest")
                            : Blocks.HAY_BLOCK.getDefaultState());
                    putIfFree(x, y, z1, theme == Theme.WINE ? ModPaletteRegistry.resolveDecor("futuremc:barrel", "minecraft:chest")
                            : darkPlanks);
                }
                return theme == Theme.WINE ? RoomTag.UNDERGROUND_LAIR : RoomTag.PANTRY;
            case LAUNDRY:
                furnisher.furnish(RoomTag.SMELTERY, x0, z0, x1, z1, yf);
                return RoomTag.SMELTERY;
            case SERVANT_ROOM:
            case BEDROOM:
                furnisher.furnish(RoomTag.BEDROOM, x0, z0, x1, z1, yf);
                return RoomTag.BEDROOM;
            case DINING:
                // Long table of fence posts and pressure plates with chairs on both sides.
                int cz = (z0 + z1) / 2;
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    putIfFree(x, y, cz, Blocks.DARK_OAK_FENCE.getDefaultState());
                    putIfFree(x, y + 1, cz, Blocks.WOODEN_PRESSURE_PLATE.getDefaultState());
                    putIfFree(x, y, cz - 1, Blocks.DARK_OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH));
                    putIfFree(x, y, cz + 1, Blocks.DARK_OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH));
                }
                return RoomTag.BARRACKS;
            case BATH:
                for (int x = x0; x <= x1; x += 3) {
                    putIfFree(x, y, z0, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, rand.nextInt(4)));
                }
                return RoomTag.GUARD_ROOM;
            case MUSIC:
                putIfFree(x0, y, z0, Blocks.JUKEBOX.getDefaultState());
                for (int x = x0 + 2; x <= x1 - 1; x += 2) {
                    putIfFree(x, y, z1, Blocks.NOTEBLOCK.getDefaultState());
                }
                return RoomTag.GUARD_ROOM;
            case STUDY:
                for (int x = x0; x <= x1; x++) {
                    putIfFree(x, y, z0, Blocks.BOOKSHELF.getDefaultState());
                    putIfFree(x, y + 1, z0, Blocks.BOOKSHELF.getDefaultState());
                }
                putIfFree((x0 + x1) / 2, y, (z0 + z1) / 2 + 1, Blocks.CRAFTING_TABLE.getDefaultState());
                return RoomTag.LIBRARY;
            case GALLERY:
                for (int z = z0 + 1; z <= z1 - 1; z += 3) {
                    pedestalWorld(x0, y, z);
                    pedestalWorld(x1, y, z);
                }
                return RoomTag.TREASURY;
            case CHAPEL:
                int cx = (x0 + x1) / 2;
                putIfFree(cx, y, z0, pal.get(PaletteSlot.ACCENT));
                putIfFree(cx, y + 1, z0, pal.get(PaletteSlot.LIGHT));
                for (int z = z0 + 2; z <= z1; z += 2) {
                    for (int x = x0; x <= x1; x++) {
                        if (x != cx) {
                            putIfFree(x, y, z, Blocks.DARK_OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH));
                        }
                    }
                }
                return RoomTag.OSSUARY;
            case ARMOURY:
            default:
                putIfFree(x0, y, z0, Blocks.ANVIL.getDefaultState());
                for (int z = z0 + 1; z <= z1; z += 2) {
                    putIfFree(x1, y, z, ModPaletteRegistry.resolveDecor("cfm:*weapon*", "minecraft:bookshelf"));
                }
                return RoomTag.GUARD_ROOM;
        }
    }

    private void pedestalWorld(int x, int y, int z) {
        if (w.get(x, y, z).getMaterial() == Material.AIR && w.get(x, y + 1, z).getMaterial() == Material.AIR) {
            w.set(x, y, z, pal.get(PaletteSlot.ACCENT, rand));
            w.set(x, y + 1, z, ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT,
                    rand.nextInt(6) == 0 ? BlockVase.Variant.GILDED : BlockVase.Variant.DARK));
        }
    }

    private void putIfFree(int x, int y, int z, IBlockState state) {
        if (state != null && w.get(x, y, z).getMaterial() == Material.AIR && !reserved.contains(Blueprint.pack(x, y, z))) {
            w.set(x, y, z, state);
        }
    }

    // ------------------------------------------------------------------ spawners

    private void spawnerAt(int lx, int y, int lz, int zone, RoomTag tag) {
        spawnerWorld(x(lx), y, z(lz), zone, tag);
    }

    private void spawnerWorld(int x, int y, int z, int zone, RoomTag tag) {
        int tier = Math.max(1, Math.min(5, ZONE_DIFFICULTY[zone]));
        StructurePieces.spawner(w, rec, new BlockPos(x, y, z), new SpawnerContext(SpawnerSource.CITADEL, rec.getCitadelId(),
                zone, tier, tag));
    }
}
