package com.shatteredcitadels.worldgen.hut;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the cellar of a {@link CellarLayout}: closed stone shells (nothing leaks in from the swamp
 * above), the ladder shaft up to the hut's hatch, the furniture of each room, the potion traps of
 * the corridors and the witches, which are placed once and stay.
 */
public final class CellarBuilder {

    private static final EnumFacing[] SIDES = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final int TRAP_POTIONS = 5;

    private final BlockWriter w;
    private final CitadelInstance record;
    private final Random rand;
    private final CellarLayout layout;
    private final long lootSeed;
    private int traps;
    private int witches;
    private BlockPos finalChest;

    public CellarBuilder(BlockWriter w, CitadelInstance record, Random rand, CellarLayout layout, long lootSeed) {
        this.w = w;
        this.record = record;
        this.rand = rand;
        this.layout = layout;
        this.lootSeed = lootSeed;
    }

    public int traps() {
        return traps;
    }

    public int witches() {
        return witches;
    }

    public BlockPos finalChest() {
        return finalChest;
    }

    /**
     * @param hutFloorY the hut's floor layer: the shaft ends right under it
     * @param witchCount witches to place in the rooms beyond the entry
     */
    public void build(int hutFloorY, int witchCount) {
        for (CellarLayout.Room room : layout.rooms) {
            shell(room.box());
        }
        for (CellarLayout.Link link : layout.links) {
            shell(link.box());
        }
        for (CellarLayout.Room room : layout.rooms) {
            int in = room.half - 1;
            w.clear(room.centreX - in, room.floorY + 1, room.centreZ - in,
                    room.centreX + in, room.floorY + CellarLayout.ROOM_HEIGHT, room.centreZ + in);
        }
        for (CellarLayout.Link link : layout.links) {
            passage(link);
        }
        shaft(hutFloorY);
        for (CellarLayout.Room room : layout.rooms) {
            furnish(room);
        }
        for (CellarLayout.Link link : layout.links) {
            if (link.trapped()) {
                trap(link);
            }
        }
        placeWitches(witchCount);
    }

    private IBlockState stone() {
        int roll = rand.nextInt(20);
        if (roll < 5) {
            return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
        }
        if (roll < 8) {
            return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        }
        if (roll < 10) {
            return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        }
        return Blocks.STONEBRICK.getDefaultState();
    }

    private void shell(StructureBoundingBox box) {
        for (int x = box.minX; x <= box.maxX; x++) {
            for (int y = box.minY; y <= box.maxY; y++) {
                for (int z = box.minZ; z <= box.maxZ; z++) {
                    w.set(x, y, z, stone());
                }
            }
        }
    }

    /** The walkway of a corridor, through both room walls. */
    private void passage(CellarLayout.Link link) {
        int y = link.from.floorY;
        for (int i = link.start() - 1; i <= link.end() + 1; i++) {
            for (int h = 1; h <= CellarLayout.CORRIDOR_HEIGHT; h++) {
                if (link.alongX()) {
                    w.set(i, y + h, link.line(), AIR);
                } else {
                    w.set(link.line(), y + h, i, AIR);
                }
            }
        }
    }

    /** A closed 3x3 shaft with a ladder from the entry room up to the hatch in the hut's floor. */
    private void shaft(int hutFloorY) {
        CellarLayout.Room entry = layout.entry();
        int x = entry.centreX;
        int z = entry.centreZ;
        int ceiling = entry.floorY + CellarLayout.ROOM_HEIGHT + 1;
        for (int y = ceiling + 1; y < hutFloorY; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        w.set(x + dx, y, z + dz, rand.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                                : Blocks.COBBLESTONE.getDefaultState());
                    }
                }
            }
        }
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.SOUTH);
        for (int y = entry.floorY + 1; y < hutFloorY; y++) {
            w.set(x, y, z, ladder);
            if (y < ceiling) {
                w.set(x, y, z - 1, Blocks.COBBLESTONE.getDefaultState());
            }
        }
    }

    /** A room seen from its door-less back wall: {@code a} runs along that wall, {@code b} towards it. */
    private final class Frame {
        final CellarLayout.Room room;
        final EnumFacing back;
        final EnumFacing right;
        /** True if even the back wall has a door (a room with four corridors). */
        final boolean backOpen;
        final int n;
        final int y;

        Frame(CellarLayout.Room room) {
            this.room = room;
            int first = rand.nextInt(SIDES.length);
            EnumFacing free = null;
            for (int i = 0; i < SIDES.length && free == null; i++) {
                EnumFacing side = SIDES[(first + i) % SIDES.length];
                if (!room.hasDoor(side)) {
                    free = side;
                }
            }
            this.backOpen = free == null;
            this.back = free == null ? SIDES[first] : free;
            this.right = back.rotateY();
            this.n = room.half - 1;
            this.y = room.floorY + 1;
        }

        BlockPos at(int a, int level, int b) {
            return new BlockPos(room.centreX + right.getXOffset() * a + back.getXOffset() * b, level,
                    room.centreZ + right.getZOffset() * a + back.getZOffset() * b);
        }

        void put(int a, int level, int b, IBlockState state) {
            w.set(at(a, level, b), state);
        }
    }

    private void furnish(CellarLayout.Room room) {
        Frame f = new Frame(room);
        switch (room.kind()) {
            case BREWING_LAB:
                lab(f);
                break;
            case HERB_STORE:
                herbs(f);
                break;
            case CELLS:
                cells(f);
                break;
            case RITUAL:
                ritual(f);
                break;
            default:
                entry(f);
                break;
        }
        int top = room.floorY + CellarLayout.ROOM_HEIGHT;
        for (int a : new int[]{-f.n, f.n}) {
            for (int b : new int[]{-f.n, f.n}) {
                BlockPos corner = f.at(a, top, b);
                if (rand.nextInt(5) < 2 && isAir(corner)) {
                    w.set(corner, Blocks.WEB.getDefaultState());
                }
            }
        }
    }

    private boolean isAir(BlockPos pos) {
        return w.get(pos.getX(), pos.getY(), pos.getZ()).getMaterial() == Material.AIR;
    }

    private void entry(Frame f) {
        IBlockState planks = Blocks.PLANKS.getDefaultState();
        f.put(f.n, f.y, f.n, planks);
        f.put(f.n, f.y + 1, f.n, planks);
        f.put(f.n, f.y + 2, f.n, Blocks.TORCH.getDefaultState());
        f.put(f.n, f.y, -f.n, planks);
        f.put(-f.n, f.y, f.n, Blocks.CAULDRON.getDefaultState());
        f.put(-f.n, f.y, -f.n, Blocks.WEB.getDefaultState());
    }

    private void lab(Frame f) {
        for (int a = -f.n; a <= f.n; a++) {
            if (a == 0) {
                continue;
            }
            if (a == -f.n) {
                f.put(a, f.y, f.n, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 1 + rand.nextInt(3)));
            } else {
                f.put(a, f.y, f.n, Blocks.BOOKSHELF.getDefaultState());
                if (a == f.n) {
                    WitchHutLoot.pot(w, f.at(a, f.y + 1, f.n), rand.nextBoolean());
                } else {
                    f.put(a, f.y + 1, f.n, Blocks.BREWING_STAND.getDefaultState());
                }
            }
        }
        // Nether wart beds in the two corners away from the benches.
        for (int a : new int[]{-f.n, f.n}) {
            for (int b = -f.n; b <= -f.n + 1; b++) {
                f.put(a, f.y - 1, b, Blocks.SOUL_SAND.getDefaultState());
                f.put(a, f.y, b, Blocks.NETHER_WART.getDefaultState().withProperty(BlockNetherWart.AGE, rand.nextInt(4)));
            }
        }
    }

    private void herbs(Frame f) {
        WitchHutLoot.chest(w, record, f.at(-f.n, f.y, f.n), f.back.getOpposite(), WitchHutLoot.HUT, lootSeed);
        f.put(f.n, f.y, f.n, Blocks.HAY_BLOCK.getDefaultState());
        f.put(f.n, f.y + 1, f.n, Blocks.HAY_BLOCK.getDefaultState());
        f.put(f.n, f.y, f.n - 1, Blocks.HAY_BLOCK.getDefaultState());
        IBlockState podzol = Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.PODZOL);
        for (int a = -f.n; a <= f.n; a++) {
            for (int b = -f.n; b <= f.n; b++) {
                if (a == 0 || b == 0 || !isAir(f.at(a, f.y, b)) || rand.nextInt(3) != 0) {
                    continue;
                }
                f.put(a, f.y - 1, b, podzol);
                f.put(a, f.y, b, (rand.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).getDefaultState());
            }
        }
        f.put(-f.n + 1, f.y, f.n, Blocks.PLANKS.getDefaultState());
        WitchHutLoot.pot(w, f.at(-f.n + 1, f.y + 1, f.n), rand.nextBoolean());
    }

    /** A barred row of cells along the back wall. */
    private void cells(Frame f) {
        IBlockState bars = Blocks.IRON_BARS.getDefaultState();
        int top = f.room.floorY + CellarLayout.ROOM_HEIGHT;
        for (int level = f.y; level <= top; level++) {
            for (int a = -f.n; a <= f.n; a++) {
                if (a != 0 || !f.backOpen) {
                    f.put(a, level, f.n - 1, bars);
                }
            }
            f.put(-1, level, f.n, bars);
            f.put(1, level, f.n, bars);
        }
        f.put(-f.n, f.y, f.n, rand.nextBoolean() ? Blocks.BONE_BLOCK.getDefaultState() : Blocks.WEB.getDefaultState());
        f.put(f.n, f.y, f.n, Blocks.WEB.getDefaultState());
        if (!f.backOpen) {
            f.put(0, f.y, f.n, skull());
        }
    }

    private static IBlockState skull() {
        return Blocks.SKULL.getDefaultState().withProperty(BlockSkull.FACING, EnumFacing.UP);
    }

    private void ritual(Frame f) {
        finalChest = f.at(0, f.y, f.n);
        WitchHutLoot.chest(w, record, finalChest, f.back.getOpposite(), WitchHutLoot.CELLAR, lootSeed);
        for (int a : new int[]{-1, 1}) {
            f.put(a, f.y, f.n, Blocks.NETHER_BRICK.getDefaultState());
            f.put(a, f.y + 1, f.n, skull());
        }
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                f.put(a, f.y - 1, b, Blocks.NETHER_BRICK.getDefaultState());
            }
        }
        f.put(0, f.y, 0, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 3));
        for (int a : new int[]{-f.n, f.n}) {
            f.put(a, f.y, f.n, Blocks.NETHER_BRICK_FENCE.getDefaultState());
            f.put(a, f.y + 1, f.n, Blocks.REDSTONE_TORCH.getDefaultState());
        }
    }

    /** A pressure plate in the corridor and, in the wall beside it, a dispenser of splash potions. */
    private void trap(CellarLayout.Link link) {
        int y = link.from.floorY + 1;
        BlockPos plate;
        BlockPos dispenser;
        EnumFacing facing;
        if (link.alongX()) {
            plate = new BlockPos(link.trapAt(), y, link.line());
            dispenser = plate.add(0, 0, link.trapSide);
            facing = link.trapSide > 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
        } else {
            plate = new BlockPos(link.line(), y, link.trapAt());
            dispenser = plate.add(link.trapSide, 0, 0);
            facing = link.trapSide > 0 ? EnumFacing.WEST : EnumFacing.EAST;
        }
        w.set(plate, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
        w.set(dispenser, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, facing));
        int potion = link.potion;
        w.afterPlace(dispenser, (world, pos) -> {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityDispenser) {
                PotionType type = potion == 0 ? PotionTypes.POISON : potion == 1 ? PotionTypes.SLOWNESS : PotionTypes.WEAKNESS;
                for (int slot = 0; slot < TRAP_POTIONS; slot++) {
                    ((TileEntityDispenser) tile).setInventorySlotContents(slot,
                            PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), type));
                }
            }
        });
        traps++;
    }

    /** Spreads the witches over the rooms beyond the entry, the ritual room first. */
    private void placeWitches(int count) {
        List<CellarLayout.Room> lairs = new ArrayList<>();
        lairs.add(layout.ritual());
        for (CellarLayout.Room room : layout.rooms) {
            if (room.index > 0 && room != layout.ritual()) {
                lairs.add(room);
            }
        }
        List<List<BlockPos>> spots = new ArrayList<>();
        for (CellarLayout.Room room : lairs) {
            spots.add(freeSpots(room));
        }
        for (int i = 0, misses = 0; witches < count && misses < lairs.size(); i++) {
            List<BlockPos> free = spots.get(i % lairs.size());
            if (free.isEmpty()) {
                misses++;
                continue;
            }
            misses = 0;
            witch(free.remove(rand.nextInt(free.size())));
            witches++;
        }
    }

    /** Floor blocks of the room with two blocks of air above. */
    private List<BlockPos> freeSpots(CellarLayout.Room room) {
        List<BlockPos> free = new ArrayList<>();
        int in = room.half - 1;
        for (int x = room.centreX - in; x <= room.centreX + in; x++) {
            for (int z = room.centreZ - in; z <= room.centreZ + in; z++) {
                BlockPos floor = new BlockPos(x, room.floorY, z);
                if (w.isSolid(x, room.floorY, z) && isAir(floor.up()) && isAir(floor.up(2))) {
                    free.add(floor);
                }
            }
        }
        return free;
    }

    private void witch(BlockPos floor) {
        w.afterPlace(floor, (world, pos) -> {
            if (world.isRemote || world.getDifficulty() == EnumDifficulty.PEACEFUL) {
                return;
            }
            EntityWitch witch = new EntityWitch(world);
            witch.setLocationAndAngles(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    world.rand.nextFloat() * 360.0F, 0.0F);
            witch.onInitialSpawn(world.getDifficultyForLocation(pos.up()), null);
            witch.enablePersistence();
            world.spawnEntity(witch);
        });
    }
}
