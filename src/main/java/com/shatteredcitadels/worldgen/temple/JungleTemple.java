package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePieces;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.Random;

/**
 * The surface of a jungle temple: four stepped tiers of overgrown masonry climbed by switchback
 * stairs on the south side, rooms inside the tiers (the main hall with the spiral down, a guard
 * room, a shrine, a storeroom, a gallery, an antechamber) and an open shrine on top, above the
 * canopy. Two of the three idol caches are here: behind a false wall and behind a painting. In
 * the boss variant the top shrine is the arena (Barako needs the open sky).
 */
public final class JungleTemple {

    public static final ResourceLocation BARAKO = new ResourceLocation("mowziesmobs", "barako");
    /** Floor index of the top shrine's arena. */
    public static final int SHRINE_FLOOR = 0;
    public static final int TIERS = 4;
    public static final int TIER_HEIGHT = 6;
    public static final int RING = 4;
    /** The tile over the under-floor cache. */
    public static final IBlockState CACHE_TILE = new JunglePalette().chiseled();

    private final BlockWriter w;
    private final Random rand;
    private final int cx;
    private final int sy;
    private final int cz;
    private final boolean boss;
    private final CitadelInstance rec;
    private final JunglePalette pal = new JunglePalette();
    private final IBlockState air = Blocks.AIR.getDefaultState();
    private final int half;
    private ResourceLocation[] mobs = JungleTempleBuilder.DEFAULT_MOBS;
    private int idols;

    public JungleTemple(BlockWriter w, Random rand, int cx, int sy, int cz, boolean boss, CitadelInstance rec) {
        this.w = w;
        this.rand = rand;
        this.cx = cx;
        this.sy = sy;
        this.cz = cz;
        this.boss = boss;
        this.rec = rec;
        this.half = 15 + rand.nextInt(4);
    }

    public JungleTemple withMobs(ResourceLocation[] mobs) {
        this.mobs = mobs;
        return this;
    }

    /** @return the caches holding an idol that the surface placed. */
    public int idolCaches() {
        return idols;
    }

    /** @return the half width of the bottom tier. */
    public int half() {
        return half;
    }

    private int h(int k) {
        return half - 3 * k;
    }

    private int base(int k) {
        return sy + TIER_HEIGHT * k;
    }

    public void build() {
        site();
        for (int k = 0; k < TIERS; k++) {
            tier(k);
        }
        for (int k = 0; k < TIERS; k++) {
            door(k);
            stairs(k);
        }
        mainHall();
        secondTier();
        gallery();
        antechamber();
        shrine();
        overgrowth();
    }

    // ------------------------------------------------------------------ site and shell

    /** Trees and hills cleared above the ground, dips filled below it, in the footprint and a ring. */
    private void site() {
        int reach = half + RING;
        int top = base(TIERS) + 16;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                w.clearColumn(x, z, sy + 1, top);
                boolean core = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= half) {
                    if (!core) {
                        w.fillDown(x, z, sy - 1, pal.wall(rand), 16);
                    }
                } else {
                    w.setIfNotSolid(x, sy, z, Blocks.GRASS.getDefaultState());
                    w.fillDown(x, z, sy - 1, Blocks.DIRT.getDefaultState(), 16);
                }
            }
        }
    }

    /** Tier {@code k}: floor (the ground tier only), walls, hollow inside, roof (the next tier's floor). */
    private void tier(int k) {
        int hk = h(k);
        int b = base(k);
        for (int dx = -hk; dx <= hk; dx++) {
            for (int dz = -hk; dz <= hk; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                int x = cx + dx;
                int z = cz + dz;
                if (k == 0) {
                    w.set(x, b, z, pal.floor(rand));
                }
                for (int y = b + 1; y < b + TIER_HEIGHT; y++) {
                    w.set(x, y, z, d == hk ? pal.wall(rand) : air);
                }
                w.set(x, b + TIER_HEIGHT, z, d == hk ? pal.accent() : pal.ceiling());
            }
        }
    }

    private void door(int k) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = base(k) + 1; y <= base(k) + 3; y++) {
                w.set(cx + dx, y, cz + h(k), air);
            }
        }
    }

    /**
     * The stair up to tier {@code k}'s roof: six steps along the strip in front of its south wall
     * (the ground, or the terrace of the tier below), from |x| = 8 towards the centre, alternating
     * sides so the climb zigzags.
     */
    private void stairs(int k) {
        int s = k % 2 == 0 ? 1 : -1;
        EnumFacing climb = s > 0 ? EnumFacing.WEST : EnumFacing.EAST;
        IBlockState step = Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, climb)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM);
        int b = base(k);
        for (int j = 0; j < TIER_HEIGHT; j++) {
            int x = cx + s * (8 - j);
            int y = b + 1 + j;
            for (int z = cz + h(k) + 1; z <= cz + h(k) + 3; z++) {
                for (int fy = b + 1; fy < y; fy++) {
                    w.set(x, fy, z, pal.wall(rand));
                }
                w.set(x, y, z, step);
                w.set(x, y + 1, z, air);
                w.set(x, y + 2, z, air);
            }
        }
    }

    // ------------------------------------------------------------------ rooms

    private void torch(int x, int y, int z, EnumFacing facing) {
        BlockPos wall = new BlockPos(x, y, z).offset(facing.getOpposite());
        if (w.isPlanned(x, y, z) && w.get(x, y, z).getBlock() == Blocks.AIR && w.isSolid(wall.getX(), y, wall.getZ())) {
            w.set(x, y, z, Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, facing));
        }
    }

    /** Torches on the inner faces of tier {@code k}'s walls, every five blocks. */
    private void wallTorches(int k) {
        int in = h(k) - 1;
        int y = base(k) + 3;
        for (int t = -in + 2; t <= in - 2; t += 5) {
            torch(cx + t, y, cz - in, EnumFacing.SOUTH);
            torch(cx + t, y, cz + in, EnumFacing.NORTH);
            torch(cx - in, y, cz + t, EnumFacing.EAST);
            torch(cx + in, y, cz + t, EnumFacing.WEST);
        }
    }

    private void spawner(BlockPos pos) {
        ResourceLocation mob = mobs[rand.nextInt(mobs.length)];
        w.set(pos, Blocks.MOB_SPAWNER.getDefaultState());
        w.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityMobSpawner) {
                ((TileEntityMobSpawner) tile).getSpawnerBaseLogic().setEntityId(mob);
                tile.markDirty();
            }
        });
    }

    private void vase(BlockPos pos) {
        if (ModBlocks.VASE != null) {
            w.set(pos, ModBlocks.VASE.getDefaultState());
        }
    }

    /** Tier 0: the main hall, jungle-wood columns around the spiral down. */
    private void mainHall() {
        int y = base(0);
        for (int px = -10; px <= 10; px += 5) {
            for (int pz = -10; pz <= 10; pz += 5) {
                int d = Math.max(Math.abs(px), Math.abs(pz));
                if (d <= 3 || d >= half - 1) {
                    continue;
                }
                for (int fy = y + 1; fy < y + TIER_HEIGHT; fy++) {
                    w.set(cx + px, fy, cz + pz, pal.pillar());
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
                    w.set(cx + dx, y, cz + dz, pal.accent());
                }
            }
        }
        wallTorches(0);
        spawner(new BlockPos(cx + half / 2, y + 1, cz - half / 2));
        vase(new BlockPos(cx - half + 1, y + 1, cz - half + 1));
        vase(new BlockPos(cx + half - 1, y + 1, cz - half + 1));
    }

    /**
     * Tier 1: a wall across (east-west) and a wall down the north half make a guard room in the
     * south, a shrine in the north-west (the false-wall cache) and a storeroom in the north-east.
     */
    private void secondTier() {
        int k = 1;
        int hk = h(k);
        int y = base(k);
        for (int dx = -hk + 1; dx <= hk - 1; dx++) {
            for (int fy = y + 1; fy < y + TIER_HEIGHT; fy++) {
                boolean opening = Math.abs(Math.abs(dx) - hk / 2) == 0 && fy <= y + 2;
                w.set(cx + dx, fy, cz, opening ? air : pal.wall(rand));
            }
        }
        for (int dz = -hk + 1; dz < 0; dz++) {
            for (int fy = y + 1; fy < y + TIER_HEIGHT; fy++) {
                boolean opening = dz == -hk / 2 && fy <= y + 2;
                w.set(cx, fy, cz + dz, opening ? air : pal.wall(rand));
            }
        }
        wallTorches(k);
        // Guard room.
        spawner(new BlockPos(cx + hk / 2 + 1, y + 1, cz + hk / 2));
        // Shrine: a statue, and the false-wall cache against the north wall.
        int m = -hk / 2 - 1;
        w.set(cx - hk / 2, y + 1, cz - hk / 2 + 1, pal.accent());
        w.set(cx - hk / 2, y + 2, cz - hk / 2 + 1, Blocks.GOLD_BLOCK.getDefaultState());
        falseWall(cx + m, y, cz - hk + 1);
        // Storeroom.
        StructurePieces.chest(w, rec, new BlockPos(cx + hk - 2, y + 1, cz - hk + 2), EnumFacing.WEST,
                LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        vase(new BlockPos(cx + hk - 2, y + 1, cz - 2));
        vase(new BlockPos(cx + 2, y + 1, cz - hk + 2));
    }

    /**
     * A buttress against the wall at {@code wallZ} (inner face): three wide, two deep, three high.
     * The middle of its front is a cracked brick; behind it, the cache chest under a top slab.
     */
    private void falseWall(int x, int y, int wallZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int fy = y + 1; fy <= y + 3; fy++) {
                    w.set(x + dx, fy, wallZ + dz, pal.wall(rand));
                }
            }
        }
        JungleCaches.chest(w, new BlockPos(x, y + 1, wallZ), EnumFacing.SOUTH, !boss, rand);
        w.set(x, y + 2, wallZ, JungleCaches.TOP_SLAB);
        w.set(x, y + 1, wallZ + 1, pal.cracked());
        w.set(x, y + 4, wallZ + 1, Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.UP));
        idols += boss ? 0 : 1;
    }

    /**
     * Tier 2: a gallery. The north wall is doubled where a painting hangs: behind the painting, in
     * the inner layer, the cache chest.
     */
    private void gallery() {
        int k = 2;
        int hk = h(k);
        int y = base(k);
        int skinZ = cz - hk + 1;
        for (int dx = -hk + 1; dx <= hk - 1; dx++) {
            for (int fy = y + 1; fy <= y + 3; fy++) {
                w.set(cx + dx, fy, skinZ, pal.wall(rand));
            }
        }
        int px = cx - 3 + rand.nextInt(7);
        JungleCaches.chest(w, new BlockPos(px, y + 1, skinZ), EnumFacing.SOUTH, !boss, rand);
        w.set(px, y + 2, skinZ, JungleCaches.TOP_SLAB);
        JungleCaches.painting(w, new BlockPos(px, y + 1, skinZ + 1), EnumFacing.SOUTH);
        idols += boss ? 0 : 1;
        for (int t = -hk + 3; t <= hk - 3; t += 4) {
            for (int fy = y + 1; fy < y + TIER_HEIGHT; fy++) {
                w.set(cx - hk + 3, fy, cz + t + 2, pal.pillar());
                w.set(cx + hk - 3, fy, cz + t + 2, pal.pillar());
            }
        }
        wallTorches(k);
        torch(px + 3, y + 3, skinZ + 1, EnumFacing.SOUTH);
        StructurePieces.chest(w, rec, new BlockPos(cx + hk - 2, y + 1, cz + hk - 2), EnumFacing.WEST,
                LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        vase(new BlockPos(cx - hk + 1, y + 1, cz + hk - 1));
    }

    /** Tier 3: the antechamber below the top shrine. */
    private void antechamber() {
        int k = 3;
        int hk = h(k);
        int y = base(k);
        StructurePieces.chest(w, rec, new BlockPos(cx, y + 1, cz - hk + 1), EnumFacing.SOUTH,
                LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        vase(new BlockPos(cx - hk + 1, y + 1, cz - hk + 1));
        vase(new BlockPos(cx + hk - 1, y + 1, cz - hk + 1));
        wallTorches(k);
    }

    // ------------------------------------------------------------------ top shrine

    /**
     * The open shrine on the top tier's roof: a low parapet (open where the last stair arrives),
     * four jungle-wood pillars crowned with glowstone, and either the boss altar with a sealed reward
     * chest or a golden statue.
     */
    private void shrine() {
        int hk = h(TIERS - 1);
        int top = base(TIERS);
        IBlockState parapet = Blocks.COBBLESTONE_WALL.getDefaultState().withProperty(BlockWall.VARIANT, BlockWall.EnumType.MOSSY);
        for (int dx = -hk; dx <= hk; dx++) {
            for (int dz = -hk; dz <= hk; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != hk) {
                    continue;
                }
                boolean arrival = dz == hk && dx >= -4 && dx <= -2;
                if (!arrival) {
                    w.set(cx + dx, top + 1, cz + dz, parapet);
                }
            }
        }
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            int px = cx + c[0] * (hk - 1);
            int pz = cz + c[1] * (hk - 1);
            for (int y = top + 1; y <= top + 5; y++) {
                w.set(px, y, pz, pal.pillar());
            }
            w.set(px, top + 6, pz, Blocks.GLOWSTONE.getDefaultState());
        }
        if (!boss) {
            w.set(cx, top + 1, cz, pal.accent());
            w.set(cx, top + 2, cz, Blocks.GOLD_BLOCK.getDefaultState());
            w.set(cx, top + 3, cz, pal.accent());
            return;
        }
        BlockPos altar = new BlockPos(cx, top, cz);
        StructurePieces.bossAltar(w, altar, rec);
        BlockPos chest = new BlockPos(cx, top + 1, cz - hk + 2);
        StructurePieces.chest(w, rec, chest, EnumFacing.SOUTH, ModLootTables.BOSS_CHEST_NORMAL, rand.nextLong());
        if (ModBlocks.SEALED_GATE != null && ConfigCache.get().isLockFloorsUntilCleared()) {
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                seal(chest.offset(side));
            }
            seal(chest.up());
        }
        StructureBoundingBox arena = new StructureBoundingBox(cx - hk, top, cz - hk, cx + hk, top + 8, cz + hk);
        rec.setBossArena(arena, altar.up());
        rec.addFloor(SHRINE_FLOOR, arena.minY, arena.maxY, 5, 0, true).setArea(arena.minX, arena.minZ, arena.maxX, arena.maxZ)
                .setZoneKey("barako_shrine");
        rec.setPreferredBoss(BARAKO);
        rec.addPart(arena);
    }

    private void seal(BlockPos pos) {
        IBlockState replaced = w.get(pos.getX(), pos.getY(), pos.getZ());
        if (w.set(pos, ModBlocks.SEALED_GATE.getDefaultState())) {
            rec.addGate(pos, SHRINE_FLOOR, Block.getStateId(replaced));
        }
    }

    // ------------------------------------------------------------------ overgrowth

    /** Vines down the outer walls and jungle leaves on the terraces, never on a planned block. */
    private void overgrowth() {
        IBlockState leaves = Blocks.LEAVES.getDefaultState().withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.JUNGLE)
                .withProperty(BlockLeaves.DECAYABLE, Boolean.FALSE).withProperty(BlockLeaves.CHECK_DECAY, Boolean.FALSE);
        for (int k = 0; k < TIERS; k++) {
            int hk = h(k);
            int top = base(k) + TIER_HEIGHT - 1;
            for (int t = -hk; t <= hk; t++) {
                vine(cx + t, top, cz - hk - 1, BlockVine.SOUTH);
                vine(cx + t, top, cz + hk + 1, BlockVine.NORTH);
                vine(cx - hk - 1, top, cz + t, BlockVine.EAST);
                vine(cx + hk + 1, top, cz + t, BlockVine.WEST);
            }
            if (k == 0) {
                continue;
            }
            // Leaves on the terrace of the tier below.
            int y = base(k) + 1;
            for (int dx = -h(k - 1); dx <= h(k - 1); dx++) {
                for (int dz = -h(k - 1); dz <= h(k - 1); dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > hk && rand.nextInt(10) == 0
                            && !w.isPlanned(cx + dx, y, cz + dz)) {
                        w.set(cx + dx, y, cz + dz, leaves);
                    }
                }
            }
        }
    }

    private void vine(int x, int top, int z, net.minecraft.block.properties.PropertyBool side) {
        if (rand.nextInt(4) != 0) {
            return;
        }
        int length = 2 + rand.nextInt(4);
        IBlockState vine = Blocks.VINE.getDefaultState().withProperty(side, Boolean.TRUE);
        for (int y = top; y > top - length; y--) {
            if (w.isPlanned(x, y, z)) {
                return;
            }
            w.set(x, y, z, vine);
        }
    }
}
