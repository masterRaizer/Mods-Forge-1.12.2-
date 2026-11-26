package com.shatteredcitadels.worldgen.bridge;

import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.worldgen.BlockWriter;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockWoodSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The bandit camp of a fortified bridge: two barricades on the deck, a hut with a chest on the far bank
 * (a niche in the far gate tower for the turreted bridge) and three to five guards that appear once,
 * when the chunk of the chest is written.
 */
public final class BridgeCamp {

    public static final ResourceLocation LOOT = ModLootTables.BRIDGE_CAMP;
    private static final ResourceLocation MELEE = new ResourceLocation("minecraft", "vindication_illager");
    private static final ResourceLocation RANGED_FALLBACK = new ResourceLocation("minecraft", "skeleton");
    /** Crossbow pillagers of the mods a pack may carry, best first. */
    private static final ResourceLocation[] RANGED = {
            new ResourceLocation("raids", "pillager"), new ResourceLocation("modernbackports", "pillager")};

    private final BlockWriter w;
    private final Random rand;
    private final BridgeBuilder b;
    private final BridgeStyle style;
    private final int seaLevel;
    private BlockPos chest;
    private int chests;

    public BridgeCamp(BlockWriter w, Random rand, BridgeBuilder bridge, BridgeStyle style, int seaLevel) {
        this.w = w;
        this.rand = rand;
        this.b = bridge;
        this.style = style;
        this.seaLevel = seaLevel;
    }

    public static int guardCount(Random rand) {
        return 3 + rand.nextInt(3);
    }

    /** @return the melee and the ranged guard: the ranged one is a pillager where some mod has it. */
    public static ResourceLocation[] guardIds(Predicate<ResourceLocation> registered) {
        ResourceLocation ranged = RANGED_FALLBACK;
        for (ResourceLocation id : RANGED) {
            if (registered.test(id)) {
                ranged = id;
                break;
            }
        }
        return new ResourceLocation[]{MELEE, ranged};
    }

    public BlockPos chestPos() {
        return chest;
    }

    /** @return how many chests the camp planned (one). */
    public int chestCount() {
        return chests;
    }

    /** @return the deck positions of the two barricades. */
    public int[] barricades() {
        int start = b.deckFirst() + BridgeBuilder.ABUTMENT;
        int width = b.deckLast() - BridgeBuilder.ABUTMENT - start + 1;
        return new int[]{start + width / 3, start + 2 * width / 3};
    }

    public void build() {
        int[] rows = barricades();
        barricade(rows[0], -b.half());
        barricade(rows[1], b.half());
        List<BlockPos> spawns = new ArrayList<>();
        if (style == BridgeStyle.TURRETED) {
            niche(spawns);
        } else {
            hut(spawns);
        }
        guards(spawns);
    }

    private void barricade(int a, int passage) {
        IBlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int c = -b.half(); c <= b.half(); c++) {
            if (c == passage) {
                continue;
            }
            IBlockState base = rand.nextBoolean() ? Blocks.HAY_BLOCK.getDefaultState()
                    : Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.SPRUCE)
                    .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y);
            b.put(a, b.deckY() + 1, c, base);
            b.put(a, b.deckY() + 2, c, fence);
        }
    }

    private void placeChest(BlockPos pos, IBlockState state) {
        w.set(pos, state);
        chest = pos;
        chests++;
    }

    /** A 5x5 hut on the far bank beside the deck, its doorway looking away from the river. */
    private void hut(List<BlockPos> spawns) {
        int a0 = b.deckLast() - 1;
        int c0 = b.half() + 3;
        int floor = seaLevel;
        IBlockState planks = Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.SPRUCE);
        IBlockState post = Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.SPRUCE)
                .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y);
        IBlockState roof = Blocks.WOODEN_SLAB.getDefaultState().withProperty(BlockWoodSlab.VARIANT, BlockPlanks.EnumType.DARK_OAK)
                .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM);
        for (int da = -1; da <= 5; da++) {
            for (int dc = -1; dc <= 5; dc++) {
                b.clearColumn(a0 + da, c0 + dc, floor + 1, floor + 5);
            }
        }
        for (int da = 0; da < 5; da++) {
            for (int dc = 0; dc < 5; dc++) {
                boolean wallA = da == 0 || da == 4;
                boolean wallC = dc == 0 || dc == 4;
                b.put(a0 + da, floor, c0 + dc, planks);
                b.fillDown(a0 + da, floor - 1, c0 + dc, Blocks.COBBLESTONE.getDefaultState(), 8);
                b.put(a0 + da, floor + 4, c0 + dc, roof);
                if (!wallA && !wallC) {
                    continue;
                }
                boolean doorway = da == 4 && dc == 2;
                boolean window = dc == 4 && da == 2;
                for (int y = floor + 1; y <= floor + 3; y++) {
                    if (doorway && y <= floor + 2) {
                        continue;
                    }
                    if (window && y == floor + 2) {
                        b.put(a0 + da, y, c0 + dc, Blocks.OAK_FENCE.getDefaultState());
                    } else {
                        b.put(a0 + da, y, c0 + dc, wallA && wallC ? post : planks);
                    }
                }
            }
        }
        // The way out, whatever the bank looks like there.
        b.clearColumn(a0 + 5, c0 + 2, floor + 1, floor + 3);
        b.put(a0 + 1, floor + 1, c0 + 3, roof);
        b.put(a0 + 3, floor + 1, c0 + 3, Blocks.CRAFTING_TABLE.getDefaultState());
        placeChest(b.at(a0 + 1, floor + 1, c0 + 1),
                Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, b.forward()));
        for (int da = 1; da <= 3; da++) {
            for (int dc = 1; dc <= 2; dc++) {
                if (da != 1 || dc != 1) {
                    spawns.add(b.at(a0 + da, floor + 1, c0 + dc));
                }
            }
        }
    }

    /** The turreted bridge keeps its chest in a niche of the far gate tower. */
    private void niche(List<BlockPos> spawns) {
        int a = b.deckLast() - 1;
        b.put(a, b.deckY() + 2, 2, Blocks.AIR.getDefaultState());
        placeChest(b.at(a, b.deckY() + 1, 2),
                Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, b.sideways().getOpposite()));
        for (int da = -1; da <= 1; da++) {
            for (int c = -1; c <= 1; c++) {
                spawns.add(b.at(a + da, b.deckY() + 1, c));
            }
        }
    }

    /** Loot and guards come with the chunk of the chest; guards stand only in that chunk, so nothing else is loaded. */
    private void guards(List<BlockPos> candidates) {
        final BlockPos chestAt = chest;
        final long lootSeed = rand.nextLong();
        final int count = guardCount(rand);
        final List<BlockPos> spots = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (pos.getX() >> 4 == chestAt.getX() >> 4 && pos.getZ() >> 4 == chestAt.getZ() >> 4) {
                spots.add(pos);
            }
        }
        if (spots.isEmpty()) {
            spots.add(chestAt.up());
        }
        w.afterPlace(chestAt, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityLockableLoot) {
                ((TileEntityLockableLoot) tile).setLootTable(LOOT, lootSeed);
            }
            spawnGuards(world, spots, count);
        });
    }

    private static void spawnGuards(World world, List<BlockPos> spots, int count) {
        if (world.isRemote || world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return;
        }
        ResourceLocation[] ids = guardIds(EntityList::isRegistered);
        for (int i = 0; i < count; i++) {
            BlockPos pos = spots.get(i % spots.size());
            Entity entity = EntityList.createEntityByIDFromName(ids[i < 2 ? 0 : 1], world);
            if (entity == null) {
                continue;
            }
            entity.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360F, 0F);
            if (entity instanceof EntityLiving) {
                EntityLiving living = (EntityLiving) entity;
                living.onInitialSpawn(world.getDifficultyForLocation(pos), null);
                living.enablePersistence();
            }
            world.spawnEntity(entity);
        }
    }
}
