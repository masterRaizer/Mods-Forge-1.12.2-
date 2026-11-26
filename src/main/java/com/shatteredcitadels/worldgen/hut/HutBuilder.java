package com.shatteredcitadels.worldgen.hut;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWoodSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * The hut above the swamp: a timber cabin on log stilts with a gable roof, a fenced porch and a
 * ladder down to a landing at the water. Local axes: {@code dz} grows towards the front (the door
 * and the porch), {@code dx} to the right of someone leaving through the door; both turn with
 * the hut's facing. The hatch in the middle of the floor does not move with the facing.
 */
public final class HutBuilder {

    /** Half of the cabin's depth: the front and back walls stand at +- DEPTH. */
    static final int DEPTH = 3;
    private static final int PORCH = 2;
    /** The ladder from the landing hangs at this dx, one block in front of the porch. */
    private static final int LADDER_DX = 2;
    private static final int PIER_LENGTH = 6;

    private static final IBlockState PLANKS = Blocks.PLANKS.getDefaultState()
            .withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.SPRUCE);
    private static final IBlockState LOG = Blocks.LOG.getDefaultState()
            .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.SPRUCE);
    private static final IBlockState FENCE = Blocks.SPRUCE_FENCE.getDefaultState();
    private static final IBlockState RIDGE = Blocks.WOODEN_SLAB.getDefaultState()
            .withProperty(BlockWoodSlab.VARIANT, BlockPlanks.EnumType.DARK_OAK);

    private final BlockWriter w;
    private final CitadelInstance record;
    private final Random rand;
    private final HutVariant variant;
    private final EnumFacing front;
    private final EnumFacing side;
    private final int cx;
    private final int floorY;
    private final int cz;
    private final int surfaceY;
    private final boolean hatch;
    private final long lootSeed;
    private final int half;

    public HutBuilder(BlockWriter w, CitadelInstance record, Random rand, HutVariant variant, EnumFacing front,
                      int cx, int floorY, int cz, int surfaceY, boolean hatch, long lootSeed) {
        this.w = w;
        this.record = record;
        this.rand = rand;
        this.variant = variant;
        this.front = front;
        this.side = front.rotateY();
        this.cx = cx;
        this.floorY = floorY;
        this.cz = cz;
        this.surfaceY = surfaceY;
        this.hatch = hatch;
        this.lootSeed = lootSeed;
        this.half = variant.half;
    }

    /** Top of the roof ridge of a hut with that floor. */
    public static int roofTop(HutVariant variant, int floorY) {
        return floorY + variant.wallHeight + 1 + variant.half + 1;
    }

    BlockPos at(int dx, int y, int dz) {
        return new BlockPos(cx + side.getXOffset() * dx + front.getXOffset() * dz, y,
                cz + side.getZOffset() * dx + front.getZOffset() * dz);
    }

    /** The cabin with its porch and roof: witches spawn inside this box. */
    public StructureBoundingBox cabinBox() {
        BlockPos a = at(-half - 1, floorY, -DEPTH - 1);
        BlockPos b = at(half + 1, roofTop(variant, floorY), DEPTH + PORCH);
        return new StructureBoundingBox(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
    }

    public void build() {
        clearSite();
        stilts();
        shell();
        roof();
        porch();
        landing();
        furnish();
        if (variant == HutVariant.TALL) {
            loft();
        }
        if (hatch) {
            // The ladder below faces south: an open trapdoor of the same facing is climbable.
            w.set(cx, floorY, cz, Blocks.TRAPDOOR.getDefaultState()
                    .withProperty(BlockTrapDoor.HALF, BlockTrapDoor.DoorHalf.TOP)
                    .withProperty(BlockTrapDoor.FACING, EnumFacing.SOUTH));
        }
    }

    private void clearSite() {
        int top = roofTop(variant, floorY) + 1;
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -DEPTH - 1; dz <= DEPTH + PORCH; dz++) {
                BlockPos p = at(dx, 0, dz);
                w.clearColumn(p.getX(), p.getZ(), surfaceY + 1, top);
            }
        }
        for (int dx = LADDER_DX - 2; dx <= LADDER_DX + 2; dx++) {
            for (int dz = DEPTH + PORCH + 1; dz <= pierEnd(); dz++) {
                BlockPos p = at(dx, 0, dz);
                w.clearColumn(p.getX(), p.getZ(), surfaceY + 1, floorY + 2);
            }
        }
    }

    private int pierEnd() {
        return DEPTH + PORCH + (variant == HutVariant.DOCK ? PIER_LENGTH : 2);
    }

    private void stilts() {
        for (int dx : new int[]{-half, half}) {
            for (int dz : new int[]{-DEPTH, DEPTH, DEPTH + PORCH}) {
                post(dx, dz, floorY - 1);
            }
        }
        // The ladder hangs on this one.
        post(LADDER_DX, DEPTH + PORCH, floorY - 1);
    }

    private void post(int dx, int dz, int topY) {
        BlockPos p = at(dx, 0, dz);
        w.fillDown(p.getX(), p.getZ(), topY, LOG, 24);
    }

    private void shell() {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -DEPTH; dz <= DEPTH + PORCH; dz++) {
                w.set(at(dx, floorY, dz), PLANKS);
            }
        }
        int top = floorY + variant.wallHeight;
        for (int y = floorY + 1; y <= top; y++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -DEPTH; dz <= DEPTH; dz++) {
                    boolean x = Math.abs(dx) == half;
                    boolean z = Math.abs(dz) == DEPTH;
                    if (x && z) {
                        w.set(at(dx, y, dz), LOG);
                    } else if (x || z) {
                        w.set(at(dx, y, dz), wall(dx, y, dz));
                    }
                }
            }
        }
    }

    private IBlockState wall(int dx, int y, int dz) {
        int level = y - floorY;
        if (dz == DEPTH && dx == 0 && level <= 2) {
            return Blocks.AIR.getDefaultState();
        }
        if (level == 2 && Math.abs(dx) == half && dz == 0) {
            return FENCE;
        }
        if (level == 6 && dx == 0) {
            return FENCE;
        }
        return PLANKS;
    }

    private void roof() {
        int base = floorY + variant.wallHeight + 1;
        IBlockState left = Blocks.DARK_OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, side);
        IBlockState right = Blocks.DARK_OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, side.getOpposite());
        for (int i = 0; i <= half + 1; i++) {
            int out = half + 1 - i;
            int y = base + i;
            for (int dz = -DEPTH - 1; dz <= DEPTH + 1; dz++) {
                if (out == 0) {
                    w.set(at(0, y, dz), RIDGE);
                } else {
                    w.set(at(-out, y, dz), left);
                    w.set(at(out, y, dz), right);
                }
            }
            // Gable ends.
            for (int dx = -out + 1; dx <= out - 1; dx++) {
                w.set(at(dx, y, -DEPTH), PLANKS);
                w.set(at(dx, y, DEPTH), PLANKS);
            }
        }
    }

    private void porch() {
        int y = floorY + 1;
        int edge = DEPTH + PORCH;
        for (int dx = -half; dx <= half; dx++) {
            if (dx != LADDER_DX) {
                w.set(at(dx, y, edge), FENCE);
            }
        }
        w.set(at(-half, y, edge - 1), FENCE);
        w.set(at(half, y, edge - 1), FENCE);
    }

    /** The ladder from the porch down to a plank landing level with the water. */
    private void landing() {
        int z0 = DEPTH + PORCH + 1;
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, front);
        for (int y = surfaceY + 1; y <= floorY; y++) {
            w.set(at(LADDER_DX, y, z0), ladder);
        }
        int end = pierEnd();
        for (int dx = LADDER_DX - 1; dx <= LADDER_DX + 1; dx++) {
            for (int dz = z0; dz <= end; dz++) {
                w.set(at(dx, surfaceY, dz), PLANKS);
            }
        }
        for (int dx : new int[]{LADDER_DX - 1, LADDER_DX + 1}) {
            post(dx, end, surfaceY - 1);
            if (variant == HutVariant.DOCK) {
                w.set(at(dx, surfaceY + 1, end), FENCE);
                post(dx, z0 + 2, surfaceY - 1);
            }
        }
        if (variant == HutVariant.DOCK) {
            BlockPos mooring = at(LADDER_DX + 1, surfaceY, end - 1);
            BlockPos berth = at(LADDER_DX + 2, surfaceY, end - 1);
            w.afterPlace(mooring, (world, pos) -> {
                if (!world.isRemote && world.isBlockLoaded(berth)
                        && world.getBlockState(berth).getMaterial() == Material.WATER) {
                    EntityBoat boat = new EntityBoat(world, berth.getX() + 0.5D, berth.getY() + 1.0D, berth.getZ() + 0.5D);
                    boat.setBoatType(EntityBoat.Type.SPRUCE);
                    world.spawnEntity(boat);
                }
            });
        }
    }

    private void furnish() {
        int y = floorY + 1;
        int in = half - 1;
        int back = -(DEPTH - 1);
        w.set(at(-in, y, back), Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 3));
        for (int dx = -1; dx <= 1; dx++) {
            w.set(at(dx, y, back), Blocks.BOOKSHELF.getDefaultState());
        }
        int stand = rand.nextInt(3) - 1;
        for (int dx = -1; dx <= 1; dx++) {
            if (dx == stand) {
                w.set(at(dx, y + 1, back), Blocks.BREWING_STAND.getDefaultState());
            } else {
                WitchHutLoot.pot(w, at(dx, y + 1, back), rand.nextBoolean());
            }
        }
        WitchHutLoot.chest(w, record, at(in, y, back), front, WitchHutLoot.HUT, lootSeed);
        w.set(at(-in, y, DEPTH - 1), Blocks.CRAFTING_TABLE.getDefaultState());
        if (variant == HutVariant.DOCK) {
            w.set(at(in, y, 0), Blocks.CAULDRON.getDefaultState());
            w.set(at(-in, y, 0), Blocks.BOOKSHELF.getDefaultState());
            WitchHutLoot.pot(w, at(-in, y + 1, 0), true);
        }
        IBlockState rug = Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, EnumDyeColor.PURPLE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.set(at(dx, y, dz), rug);
            }
        }
        if (rand.nextBoolean()) {
            w.set(at(in, floorY + variant.wallHeight, DEPTH - 1 - (variant == HutVariant.TALL ? 1 : 0)), Blocks.WEB.getDefaultState());
        }
    }

    /** A second floor reached by a ladder in the front right corner. */
    private void loft() {
        int in = half - 1;
        int y = floorY + 4;
        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, side.getOpposite());
        for (int dx = -in; dx <= in; dx++) {
            for (int dz = -(DEPTH - 1); dz <= DEPTH - 1; dz++) {
                if (dx != in || dz != DEPTH - 1) {
                    w.set(at(dx, y, dz), PLANKS);
                }
            }
        }
        for (int ly = floorY + 1; ly <= y; ly++) {
            w.set(at(in, ly, DEPTH - 1), ladder);
        }
        w.set(at(-in, y + 1, -(DEPTH - 1)), Blocks.BOOKSHELF.getDefaultState());
        WitchHutLoot.pot(w, at(-in, y + 2, -(DEPTH - 1)), false);
        w.set(at(-in, y + 1, DEPTH - 1), Blocks.CAULDRON.getDefaultState());
        w.set(at(in, y + 1, -(DEPTH - 1)), Blocks.HAY_BLOCK.getDefaultState());
        w.set(at(-in, y + 3, 0), Blocks.WEB.getDefaultState());
    }
}
