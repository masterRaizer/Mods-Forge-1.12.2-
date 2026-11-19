package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.PaletteSlot;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.spawner.WaveSpawnerHooks;
import com.shatteredcitadels.structure.CitadelDataRecord;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.block.BlockBone;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Multi-level crypt: per level a 1-wide maze of narrow corridors on an odd grid, tagged rooms
 * (goblin dens, underground lairs, ossuaries, treasuries), pressure-plate traps that fire
 * arrows or splash potions, and ladder shafts between levels. Used both below citadels
 * (entered from the ground floor) and for rare standalone entrances in the world.
 */
public final class DungeonCryptBuilder {

    public static final int LEVEL_HEIGHT = CitadelPlan.CRYPT_LEVEL_HEIGHT;

    private static final EnumFacing[] HORIZONTALS = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
    private static final RoomTag[] ROOM_TAGS = {RoomTag.GOBLIN_DEN, RoomTag.UNDERGROUND_LAIR, RoomTag.OSSUARY, RoomTag.TREASURY};
    private static final PotionType[] TRAP_POTIONS = {PotionTypes.POISON, PotionTypes.SLOWNESS, PotionTypes.HARMING, PotionTypes.WEAKNESS};

    private final BlockWriter w;
    private final BlockPalette pal;
    private final Random rand;
    private final CitadelDataRecord rec;
    private final int x0;
    private final int z0;
    private final int size;
    private final int baseTier;

    private final IBlockState[] wallVariants;
    private final IBlockState air = Blocks.AIR.getDefaultState();

    /** Per level: open[i][j] true = air at grid cell (x0 + i, z0 + j). */
    private boolean[][][] open;
    /** Per level: room index per cell, -1 = corridor/wall. */
    private int[][][] roomOf;
    private final List<List<int[]>> rooms = new ArrayList<>();

    /**
     * @param size square size in blocks; must be odd so the maze has a solid outer ring.
     */
    public DungeonCryptBuilder(BlockWriter writer, BlockPalette palette, Random random, CitadelDataRecord record,
                               int minX, int minZ, int size, int baseTier) {
        this.w = writer;
        this.pal = palette;
        this.rand = random;
        this.rec = record;
        this.x0 = minX;
        this.z0 = minZ;
        this.size = (size & 1) == 0 ? size - 1 : size;
        this.baseTier = baseTier;
        this.wallVariants = new IBlockState[]{
                Blocks.STONEBRICK.getDefaultState(),
                Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY),
                Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED),
                Blocks.COBBLESTONE.getDefaultState(),
                Blocks.MOSSY_COBBLESTONE.getDefaultState()
        };
    }

    /**
     * Builds {@code levels} levels starting with level 0 at {@code topLevelY} going down, and a
     * ladder shaft from cell (shaftX, shaftZ) on level 0 up to {@code shaftTopY}.
     *
     * @return number of levels actually built (limited by the world bottom).
     */
    public int build(int topLevelY, int levels, int shaftX, int shaftZ, int shaftTopY) {
        int maxLevels = (topLevelY - (BlockWriter.MIN_Y + 1)) / LEVEL_HEIGHT + 1;
        int count = Math.max(0, Math.min(levels, maxLevels));
        if (count == 0) {
            return 0;
        }
        int si = clampOdd(shaftX - x0);
        int sj = clampOdd(shaftZ - z0);

        open = new boolean[count][size][size];
        roomOf = new int[count][size][size];
        for (int k = 0; k < count; k++) {
            layoutLevel(k, k == 0 ? si : -1, k == 0 ? sj : -1);
        }
        for (int k = 0; k < count; k++) {
            carveLevel(k, topLevelY - k * LEVEL_HEIGHT);
        }
        for (int k = 0; k + 1 < count; k++) {
            connectLevels(k, topLevelY - k * LEVEL_HEIGHT);
        }
        buildEntranceShaft(si, sj, topLevelY, shaftTopY);
        for (int k = 0; k < count; k++) {
            int yL = topLevelY - k * LEVEL_HEIGHT;
            placeTraps(k, yL, si, sj);
            decorateRooms(k, yL);
            rec.addFloor(-(k + 1), yL, yL + LEVEL_HEIGHT - 1);
        }
        rec.setHasCrypt(true);
        rec.expandBounds(new StructureBoundingBox(x0, topLevelY - (count - 1) * LEVEL_HEIGHT, z0,
                x0 + size - 1, shaftTopY, z0 + size - 1));
        return count;
    }

    /** Small ruined shrine around a standalone crypt entrance, centred on (cx, surfaceY, cz). */
    public void buildSurfaceRuin(int cx, int surfaceY, int cz) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                w.set(x, surfaceY, z, wall());
                w.clear(x, surfaceY + 1, z, x, surfaceY + 4, z);
                for (int y = surfaceY - 1; y >= surfaceY - 4 && !w.isSolid(x, y, z); y--) {
                    w.set(x, y, z, wall());
                }
                boolean edge = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                if (edge) {
                    int height = rand.nextInt(4);
                    for (int y = 1; y <= height; y++) {
                        w.set(x, surfaceY + y, z, wall());
                    }
                }
            }
        }
        int[][] corners = {{cx - 3, cz - 3}, {cx + 3, cz - 3}, {cx - 3, cz + 3}, {cx + 3, cz + 3}};
        for (int[] c : corners) {
            w.fill(c[0], surfaceY + 1, c[1], c[0], surfaceY + 4, c[1], pal.get(PaletteSlot.PILLAR));
        }
        w.set(cx - 1, surfaceY, cz - 1, pal.get(PaletteSlot.ACCENT));
        w.set(cx + 1, surfaceY, cz + 1, pal.get(PaletteSlot.ACCENT));
        w.set(cx, surfaceY, cz, air);
        rec.addRoom(new StructureBoundingBox(cx - 3, surfaceY, cz - 3, cx + 3, surfaceY + 4, cz + 3),
                RoomTag.ENTRANCE_HALL, 0);
    }

    // ------------------------------------------------------------------ layout

    private void layoutLevel(int k, int si, int sj) {
        boolean[][] o = open[k];
        int[][] r = roomOf[k];
        for (int[] row : r) {
            java.util.Arrays.fill(row, -1);
        }
        int levelRoomsStart = rooms.size();
        int wanted = 2 + rand.nextInt(3);
        for (int attempt = 0; attempt < 24 && rooms.size() - levelRoomsStart < wanted; attempt++) {
            int rw = rand.nextBoolean() ? 3 : 5;
            int rh = rand.nextBoolean() ? 3 : 5;
            int maxStart = size - 1 - Math.max(rw, rh);
            if (maxStart < 1) {
                break;
            }
            int ri = 1 + 2 * rand.nextInt(Math.max(1, (size - 1 - rw) / 2));
            int rj = 1 + 2 * rand.nextInt(Math.max(1, (size - 1 - rh) / 2));
            if (ri + rw > size - 1 || rj + rh > size - 1 || overlapsRoom(r, ri - 1, rj - 1, ri + rw, rj + rh)
                    || (si >= ri - 1 && si <= ri + rw && sj >= rj - 1 && sj <= rj + rh)) {
                continue;
            }
            int index = rooms.size();
            List<int[]> room = new ArrayList<>();
            room.add(new int[]{k, ri, rj, ri + rw - 1, rj + rh - 1, (index + k) % ROOM_TAGS.length});
            rooms.add(room);
            for (int i = ri; i < ri + rw; i++) {
                for (int j = rj; j < rj + rh; j++) {
                    o[i][j] = true;
                    r[i][j] = index;
                }
            }
        }
        carveMaze(k);
        // Doors from each room of this level into the maze.
        for (int index = levelRoomsStart; index < rooms.size(); index++) {
            openRoomDoors(k, rooms.get(index).get(0));
        }
        if (si > 0 && sj > 0) {
            o[si][sj] = true;
        }
    }

    private boolean overlapsRoom(int[][] r, int minI, int minJ, int maxI, int maxJ) {
        for (int i = Math.max(0, minI); i <= Math.min(size - 1, maxI); i++) {
            for (int j = Math.max(0, minJ); j <= Math.min(size - 1, maxJ); j++) {
                if (r[i][j] >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Iterative recursive-backtracker over odd cells, joining every reachable component. */
    private void carveMaze(int k) {
        boolean[][] o = open[k];
        int[][] r = roomOf[k];
        boolean[][] visited = new boolean[size][size];
        boolean progress = true;
        boolean first = true;
        while (progress) {
            progress = false;
            for (int i = 1; i < size - 1; i += 2) {
                for (int j = 1; j < size - 1; j += 2) {
                    if (visited[i][j] || r[i][j] >= 0) {
                        continue;
                    }
                    int[] link = first ? null : visitedNeighbour(visited, r, i, j);
                    if (!first && link == null) {
                        continue;
                    }
                    if (link != null) {
                        o[(i + link[0]) / 2][(j + link[1]) / 2] = true;
                    }
                    dfs(k, visited, i, j);
                    first = false;
                    progress = true;
                }
            }
        }
    }

    private int[] visitedNeighbour(boolean[][] visited, int[][] r, int i, int j) {
        int[][] steps = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] s : steps) {
            int ni = i + s[0];
            int nj = j + s[1];
            if (ni > 0 && nj > 0 && ni < size - 1 && nj < size - 1 && visited[ni][nj] && r[ni][nj] < 0) {
                return new int[]{ni, nj};
            }
        }
        return null;
    }

    private void dfs(int k, boolean[][] visited, int si, int sj) {
        boolean[][] o = open[k];
        int[][] r = roomOf[k];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{si, sj});
        visited[si][sj] = true;
        o[si][sj] = true;
        List<int[]> options = new ArrayList<>(4);
        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            options.clear();
            int[][] steps = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
            for (int[] s : steps) {
                int ni = cell[0] + s[0];
                int nj = cell[1] + s[1];
                if (ni > 0 && nj > 0 && ni < size - 1 && nj < size - 1 && !visited[ni][nj] && r[ni][nj] < 0) {
                    options.add(new int[]{ni, nj});
                }
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] next = options.get(rand.nextInt(options.size()));
            o[(cell[0] + next[0]) / 2][(cell[1] + next[1]) / 2] = true;
            o[next[0]][next[1]] = true;
            visited[next[0]][next[1]] = true;
            stack.push(next);
        }
    }

    private void openRoomDoors(int k, int[] room) {
        boolean[][] o = open[k];
        List<int[]> doors = new ArrayList<>();
        for (int i = room[1]; i <= room[3]; i += 2) {
            addDoor(doors, i, room[2] - 1, i, room[2] - 2);
            addDoor(doors, i, room[4] + 1, i, room[4] + 2);
        }
        for (int j = room[2]; j <= room[4]; j += 2) {
            addDoor(doors, room[1] - 1, j, room[1] - 2, j);
            addDoor(doors, room[3] + 1, j, room[3] + 2, j);
        }
        Collections.shuffle(doors, rand);
        int count = Math.min(doors.size(), 1 + rand.nextInt(2));
        for (int d = 0; d < count; d++) {
            o[doors.get(d)[0]][doors.get(d)[1]] = true;
        }
    }

    private void addDoor(List<int[]> doors, int di, int dj, int bi, int bj) {
        if (di > 0 && dj > 0 && di < size - 1 && dj < size - 1 && bi > 0 && bj > 0 && bi < size - 1 && bj < size - 1) {
            doors.add(new int[]{di, dj});
        }
    }

    // ------------------------------------------------------------------ carving

    private void carveLevel(int k, int yL) {
        boolean[][] o = open[k];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = x0 + i;
                int z = z0 + j;
                w.set(x, yL, z, wall());
                w.set(x, yL + LEVEL_HEIGHT - 1, z, wall());
                for (int y = yL + 1; y <= yL + LEVEL_HEIGHT - 2; y++) {
                    w.set(x, y, z, o[i][j] ? air : wall());
                }
            }
        }
    }

    private void connectLevels(int k, int yL) {
        boolean[][] upper = open[k];
        boolean[][] lower = open[k + 1];
        List<int[]> candidates = new ArrayList<>();
        for (int i = 1; i < size - 1; i += 2) {
            for (int j = 1; j < size - 1; j += 2) {
                if (!upper[i][j] || !lower[i][j] || roomOf[k][i][j] >= 0 || roomOf[k + 1][i][j] >= 0) {
                    continue;
                }
                for (EnumFacing side : HORIZONTALS) {
                    int ni = i + side.getXOffset();
                    int nj = j + side.getZOffset();
                    if (!upper[ni][nj] && !lower[ni][nj]) {
                        candidates.add(new int[]{i, j, side.getHorizontalIndex()});
                        break;
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int[] c = candidates.get(rand.nextInt(candidates.size()));
        EnumFacing wallSide = EnumFacing.byHorizontalIndex(c[2]);
        int x = x0 + c[0];
        int z = z0 + c[1];
        int bottom = yL - LEVEL_HEIGHT + 1;
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, wallSide.getOpposite());
        for (int y = bottom; y <= yL; y++) {
            w.set(x, y, z, ladder);
        }
    }

    private void buildEntranceShaft(int si, int sj, int topLevelY, int shaftTopY) {
        if (si <= 0 || sj <= 0 || shaftTopY <= topLevelY) {
            return;
        }
        boolean[][] o = open[0];
        EnumFacing wallSide = EnumFacing.NORTH;
        for (EnumFacing side : HORIZONTALS) {
            if (!o[si + side.getXOffset()][sj + side.getZOffset()]) {
                wallSide = side;
                break;
            }
        }
        int x = x0 + si;
        int z = z0 + sj;
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, wallSide.getOpposite());
        int ceiling = topLevelY + LEVEL_HEIGHT - 1;
        for (int y = topLevelY + 1; y <= shaftTopY; y++) {
            if (y >= ceiling) {
                // Above the crypt: seal the shaft against caves and keep the ladder backing solid.
                for (EnumFacing side : HORIZONTALS) {
                    int nx = x + side.getXOffset();
                    int nz = z + side.getZOffset();
                    if (y < shaftTopY && !w.isSolid(nx, y, nz)) {
                        w.set(nx, y, nz, wall());
                    }
                }
            }
            w.set(x, y, z, ladder);
        }
    }

    // ------------------------------------------------------------------ traps & decor

    private void placeTraps(int k, int yL, int si, int sj) {
        boolean[][] o = open[k];
        List<int[]> corridors = new ArrayList<>();
        for (int i = 1; i < size - 1; i += 2) {
            for (int j = 1; j < size - 1; j += 2) {
                if (!o[i][j] || roomOf[k][i][j] >= 0 || (k == 0 && i == si && j == sj)) {
                    continue;
                }
                boolean alongX = o[i - 1][j] && o[i + 1][j] && !o[i][j - 1] && !o[i][j + 1];
                boolean alongZ = o[i][j - 1] && o[i][j + 1] && !o[i - 1][j] && !o[i + 1][j];
                if (alongX || alongZ) {
                    corridors.add(new int[]{i, j, alongX ? 1 : 0});
                }
            }
        }
        Collections.shuffle(corridors, rand);
        int traps = Math.min(corridors.size(), 2 + rand.nextInt(3) + k);
        for (int t = 0; t < traps; t++) {
            int[] c = corridors.get(t);
            int x = x0 + c[0];
            int z = z0 + c[1];
            int y = yL + 1;
            if (w.get(x, y, z).getBlock() == Blocks.LADDER) {
                continue;
            }
            // Dispenser sits in the side wall and faces the corridor; the plate powers it.
            EnumFacing side = c[2] == 1 ? (rand.nextBoolean() ? EnumFacing.NORTH : EnumFacing.SOUTH)
                    : (rand.nextBoolean() ? EnumFacing.EAST : EnumFacing.WEST);
            int dx = x + side.getXOffset();
            int dz = z + side.getZOffset();
            w.set(x, y, z, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
            if (w.set(dx, y, dz, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, side.getOpposite()))) {
                fillDispenser(new BlockPos(dx, y, dz));
            }
            rec.addRoom(new StructureBoundingBox(x, yL, z, x, yL + LEVEL_HEIGHT - 1, z), RoomTag.TRAP_CORRIDOR, -(k + 1));
        }
    }

    /**
     * Fills the dispenser through NBT. {@code setInventorySlotContents} would call
     * {@code markDirty()}, whose comparator update reads blocks two blocks away and can load a
     * neighbouring chunk during population (cascading worldgen).
     */
    private void fillDispenser(BlockPos pos) {
        TileEntity tile = w.getWorld().getTileEntity(pos);
        if (!(tile instanceof TileEntityDispenser)) {
            return;
        }
        NBTTagList items = new NBTTagList();
        if (rand.nextFloat() < 0.6F) {
            items.appendTag(slot(0, new ItemStack(Items.ARROW, 8 + rand.nextInt(9))));
        } else {
            PotionType type = TRAP_POTIONS[rand.nextInt(TRAP_POTIONS.length)];
            for (int slot = 0; slot < 3; slot++) {
                items.appendTag(slot(slot, PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), type)));
            }
        }
        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
        tag.setTag("Items", items);
        tile.readFromNBT(tag);
    }

    private static NBTTagCompound slot(int slot, ItemStack stack) {
        NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound());
        entry.setByte("Slot", (byte) slot);
        return entry;
    }

    private void decorateRooms(int k, int yL) {
        int floorIndex = -(k + 1);
        for (List<int[]> holder : rooms) {
            int[] room = holder.get(0);
            if (room[0] != k) {
                continue;
            }
            RoomTag tag = ROOM_TAGS[room[5]];
            int minX = x0 + room[1];
            int minZ = z0 + room[2];
            int maxX = x0 + room[3];
            int maxZ = z0 + room[4];
            int ccx = (minX + maxX) / 2;
            int ccz = (minZ + maxZ) / 2;
            rec.addRoom(new StructureBoundingBox(minX, yL, minZ, maxX, yL + LEVEL_HEIGHT - 1, maxZ), tag, floorIndex);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    w.set(x, yL, z, rand.nextInt(4) == 0 ? pal.get(PaletteSlot.ACCENT) : wall());
                }
            }
            placeTorch(minX, yL + 2, ccz, EnumFacing.EAST);
            int tier = MobTiers.clamp(baseTier + k);
            switch (tag) {
                case GOBLIN_DEN:
                case UNDERGROUND_LAIR:
                    spawner(new BlockPos(ccx, yL + 1, ccz), floorIndex, tier, tag);
                    cobwebs(minX, maxX, minZ, maxZ, yL + LEVEL_HEIGHT - 2);
                    break;
                case OSSUARY:
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                            if (edge && rand.nextInt(3) == 0) {
                                w.set(x, yL + 1, z, Blocks.BONE_BLOCK.getDefaultState()
                                        .withProperty(BlockBone.AXIS, rand.nextBoolean() ? EnumFacing.Axis.X : EnumFacing.Axis.Z));
                            }
                        }
                    }
                    break;
                case TREASURY:
                default:
                    placeChest(new BlockPos(ccx, yL + 1, ccz));
                    break;
            }
        }
        // Cobwebs in some dead ends.
        boolean[][] o = open[k];
        for (int i = 1; i < size - 1; i += 2) {
            for (int j = 1; j < size - 1; j += 2) {
                if (!o[i][j] || roomOf[k][i][j] >= 0) {
                    continue;
                }
                int exits = (o[i - 1][j] ? 1 : 0) + (o[i + 1][j] ? 1 : 0) + (o[i][j - 1] ? 1 : 0) + (o[i][j + 1] ? 1 : 0);
                int x = x0 + i;
                int z = z0 + j;
                if (exits == 1 && rand.nextInt(3) == 0 && w.get(x, yL + 2, z).getBlock() == Blocks.AIR) {
                    w.set(x, yL + 2, z, Blocks.WEB.getDefaultState());
                }
            }
        }
    }

    private void cobwebs(int minX, int maxX, int minZ, int maxZ, int y) {
        int[][] corners = {{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}};
        for (int[] c : corners) {
            if (rand.nextBoolean() && w.get(c[0], y, c[1]).getBlock() == Blocks.AIR) {
                w.set(c[0], y, c[1], Blocks.WEB.getDefaultState());
            }
        }
    }

    private void placeTorch(int x, int y, int z, EnumFacing facing) {
        int bx = x - facing.getXOffset();
        int bz = z - facing.getZOffset();
        if (w.isSolid(bx, y, bz) && w.get(x, y, z).getBlock() == Blocks.AIR) {
            w.set(x, y, z, Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, facing));
        }
    }

    private void spawner(BlockPos pos, int floorIndex, int tier, RoomTag tag) {
        if (!w.inside(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        SpawnerContext context = new SpawnerContext(SpawnerSource.CRYPT, rec.getId(), floorIndex, tier, tag);
        if (WaveSpawnerHooks.place(w.getWorld(), pos, context)) {
            rec.addSpawnerPoint(pos, floorIndex, tier);
        }
    }

    private void placeChest(BlockPos pos) {
        if (!w.set(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, EnumFacing.SOUTH))) {
            return;
        }
        TileEntity tile = w.getWorld().getTileEntity(pos);
        if (tile instanceof TileEntityLockableLoot) {
            ((TileEntityLockableLoot) tile).setLootTable(LootTableList.CHESTS_SIMPLE_DUNGEON, rand.nextLong());
        }
        rec.addChestPoint(pos);
    }

    private IBlockState wall() {
        return wallVariants[rand.nextInt(wallVariants.length)];
    }

    private int clampOdd(int value) {
        int v = Math.max(1, Math.min(size - 2, value));
        return (v & 1) == 0 ? v - 1 : v;
    }
}
