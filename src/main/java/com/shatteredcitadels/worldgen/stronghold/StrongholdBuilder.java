package com.shatteredcitadels.worldgen.stronghold;

import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePieces;
import com.shatteredcitadels.worldgen.catacomb.CatacombPalette;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.Random;

/**
 * Writes a {@link StrongholdLayout} into a blueprint: every piece is a masonry box with its
 * interior carved out, passages are cut through both walls of each used doorway, unused doorways
 * stay walled up (or collapsed), then the piece gets its contents.
 *
 * <p>Pieces are addressed in their own frame: {@code a} along the direction of travel from the
 * entry wall (0), {@code c} across from the left wall (0), as in {@link StrongholdLayout}.
 */
public class StrongholdBuilder {

    public static final ResourceLocation[] DEFAULT_MOBS = {
            new ResourceLocation("minecraft", "zombie"), new ResourceLocation("minecraft", "skeleton"),
            new ResourceLocation("minecraft", "spider"), new ResourceLocation("minecraft", "cave_spider"),
            new ResourceLocation("minecraft", "silverfish")};
    public static final ResourceLocation SILVERFISH = new ResourceLocation("minecraft", "silverfish");

    protected final BlockWriter w;
    protected final CitadelInstance rec;
    protected final Random rand;
    protected final CatacombPalette pal = new CatacombPalette();
    protected final IBlockState air = Blocks.AIR.getDefaultState();
    private ResourceLocation[] mobs = DEFAULT_MOBS;
    private int corridors;

    public StrongholdBuilder(BlockWriter w, CitadelInstance rec, Random rand) {
        this.w = w;
        this.rec = rec;
        this.rand = rand;
    }

    /** Mobs for the stronghold's spawners (from the config); silverfish always guard the portal. */
    public StrongholdBuilder withMobs(ResourceLocation[] mobs) {
        if (mobs != null && mobs.length > 0) {
            this.mobs = mobs;
        }
        return this;
    }

    public void build(StrongholdLayout layout) {
        for (StrongholdLayout.Piece piece : layout.pieces()) {
            shell(piece);
        }
        for (StrongholdLayout.Piece piece : layout.pieces()) {
            doors(piece);
        }
        for (StrongholdLayout.Piece piece : layout.pieces()) {
            switch (piece.kind) {
                case CORRIDOR:
                    corridor(piece);
                    break;
                case TURN:
                case T_JUNCTION:
                    junction(piece);
                    break;
                case STAIRS_UP:
                case STAIRS_DOWN:
                    stairs(piece);
                    break;
                case PORTAL_ROOM:
                    portalRoom(piece);
                    break;
                default:
                    buildRoom(piece);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ frame

    protected BlockPos at(StrongholdLayout.Piece piece, int a, int c, int y) {
        EnumFacing f = piece.facing;
        BlockPos entry = piece == null || piece.doors.isEmpty() ? BlockPos.ORIGIN : piece.doors.get(0).pos;
        int c0 = piece.kind.width / 2;
        BlockPos p = entry.offset(f, a).offset(f.rotateY(), c - c0);
        return new BlockPos(p.getX(), y, p.getZ());
    }

    protected void set(BlockPos pos, IBlockState state) {
        w.set(pos, state);
    }

    // ------------------------------------------------------------------ shell & doors

    private void shell(StrongholdLayout.Piece piece) {
        StructureBoundingBox b = piece.box;
        for (int x = b.minX; x <= b.maxX; x++) {
            for (int z = b.minZ; z <= b.maxZ; z++) {
                boolean edge = x == b.minX || x == b.maxX || z == b.minZ || z == b.maxZ;
                w.set(x, b.minY, z, pal.floor(rand));
                w.set(x, b.maxY, z, pal.ceiling());
                for (int y = b.minY + 1; y < b.maxY; y++) {
                    w.set(x, y, z, edge ? pal.wall(rand) : air);
                }
            }
        }
    }

    private void doors(StrongholdLayout.Piece piece) {
        for (StrongholdLayout.Doorway door : piece.doors) {
            EnumFacing side = door.out.rotateY();
            boolean collapsed = !door.used && rand.nextInt(6) == 0;
            for (int t = -1; t <= 1; t++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = door.pos.offset(side, t).up(dy);
                    if (door.used) {
                        w.set(p, air);
                    } else if (collapsed) {
                        w.set(p, rand.nextBoolean() ? Blocks.GRAVEL.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
                    } else {
                        w.set(p, Blocks.STONEBRICK.getDefaultState().withProperty(net.minecraft.block.BlockStoneBrick.VARIANT,
                                net.minecraft.block.BlockStoneBrick.EnumType.MOSSY));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ corridors

    private void corridor(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int top = piece.box.maxY - 1;
        int variant = rand.nextInt(5);
        corridors++;
        for (int a = 1; a < piece.length - 1; a++) {
            switch (variant) {
                case 1: // cobwebs
                    if (rand.nextInt(3) == 0) {
                        set(at(piece, a, 1 + rand.nextInt(3), top), Blocks.WEB.getDefaultState());
                    }
                    break;
                case 2: // ruined: rubble along the sides, cracked floor
                    if (rand.nextInt(3) == 0) {
                        set(at(piece, a, rand.nextBoolean() ? 1 : 3, y + 1), rand.nextBoolean()
                                ? Blocks.COBBLESTONE.getDefaultState() : Blocks.GRAVEL.getDefaultState());
                    }
                    break;
                case 3: // torch-lit
                    if (a % 4 == 2) {
                        torch(piece, a, 0, y + 3);
                    }
                    break;
                default:
                    break;
            }
        }
        int mid = piece.length / 2;
        if (variant == 4 && piece.length >= 6) {
            // Something left against the wall.
            if (rand.nextBoolean()) {
                StructurePieces.chest(w, rec, at(piece, mid, 1, y + 1), piece.facing.rotateY(),
                        LootTableList.CHESTS_STRONGHOLD_CORRIDOR, rand.nextLong());
            } else {
                vase(at(piece, mid, 3, y + 1));
            }
        }
        if (piece.length >= 6 && corridors % 8 == 0) {
            trap(piece, mid, y);
        } else if (piece.length >= 7 && rand.nextInt(12) == 0) {
            spawner(at(piece, mid, 2, y + 1), mob());
        }
    }

    private void junction(StrongholdLayout.Piece piece) {
        if (rand.nextInt(3) == 0) {
            set(at(piece, 2, 2, piece.box.maxY - 1), Blocks.WEB.getDefaultState());
        }
    }

    /** A stone pressure plate by the wall fires the arrow dispenser hidden in that wall. */
    private void trap(StrongholdLayout.Piece piece, int a, int y) {
        set(at(piece, a, 1, y + 1), Blocks.STONE_PRESSURE_PLATE.getDefaultState());
        BlockPos dispenser = at(piece, a, 0, y + 1);
        set(dispenser, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, piece.facing.rotateY()));
        w.afterPlace(dispenser, (world, pos) -> {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityDispenser) {
                for (int slot = 0; slot < 3; slot++) {
                    ((TileEntityDispenser) tile).setInventorySlotContents(slot, new ItemStack(Items.ARROW, 16));
                }
            }
        });
    }

    private void stairs(StrongholdLayout.Piece piece) {
        boolean up = piece.rise > 0;
        int steps = Math.abs(piece.rise);
        int bottom = piece.box.minY;
        for (int i = 1; i <= steps; i++) {
            int y = up ? piece.floorY + i : piece.floorY - i + 1;
            EnumFacing climb = up ? piece.facing : piece.facing.getOpposite();
            for (int c = 1; c <= 3; c++) {
                for (int fy = bottom + 1; fy < y; fy++) {
                    set(at(piece, i, c, fy), pal.wall(rand));
                }
                set(at(piece, i, c, y), stair(climb));
            }
        }
        int landing = piece.floorY + piece.rise;
        for (int a = steps + 1; a < piece.length - 1; a++) {
            for (int c = 1; c <= 3; c++) {
                for (int fy = bottom + 1; fy <= landing; fy++) {
                    set(at(piece, a, c, fy), pal.wall(rand));
                }
            }
        }
        torch(piece, piece.length / 2, 0, Math.max(piece.floorY, landing) + 3);
    }

    // ------------------------------------------------------------------ portal room

    /**
     * Raised platform with the twelve End portal frames around a 3x3 well over lava; stairs up from
     * the entry side; a silverfish spawner beside the platform; pillars and torches.
     */
    private void portalRoom(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int ca = 9;
        int cc = piece.kind.width / 2;
        // Platform 7x7, two high.
        for (int a = ca - 3; a <= ca + 3; a++) {
            for (int c = cc - 3; c <= cc + 3; c++) {
                set(at(piece, a, c, y + 1), pal.wall(rand));
                set(at(piece, a, c, y + 2), pal.accent());
            }
        }
        // Stairs up from the entry side.
        for (int c = cc - 1; c <= cc + 1; c++) {
            set(at(piece, ca - 5, c, y + 1), stair(piece.facing));
            set(at(piece, ca - 4, c, y + 1), pal.wall(rand));
            set(at(piece, ca - 4, c, y + 2), stair(piece.facing));
        }
        // Lava under the well, frames around it.
        BlockPos centre = at(piece, ca, cc, y + 3);
        for (int da = -1; da <= 1; da++) {
            for (int dc = -1; dc <= 1; dc++) {
                set(at(piece, ca + da, cc + dc, y + 2), Blocks.LAVA.getDefaultState());
            }
        }
        for (int da = -2; da <= 2; da++) {
            for (int dc = -2; dc <= 2; dc++) {
                boolean ring = Math.abs(da) == 2 || Math.abs(dc) == 2;
                boolean corner = Math.abs(da) == 2 && Math.abs(dc) == 2;
                if (!ring || corner) {
                    continue;
                }
                BlockPos pos = at(piece, ca + da, cc + dc, y + 3);
                EnumFacing inward = EnumFacing.getFacingFromVector(centre.getX() - pos.getX(), 0, centre.getZ() - pos.getZ());
                set(pos, Blocks.END_PORTAL_FRAME.getDefaultState().withProperty(BlockEndPortalFrame.FACING, inward)
                        .withProperty(BlockEndPortalFrame.EYE, rand.nextInt(10) == 0));
            }
        }
        // Pillars in the corners, torches on the walls.
        int length = piece.length;
        int width = piece.kind.width;
        for (int[] p : new int[][]{{1, 1}, {1, width - 2}, {length - 2, 1}, {length - 2, width - 2}}) {
            for (int fy = y + 1; fy < piece.box.maxY; fy++) {
                set(at(piece, p[0], p[1], fy), pal.pillar());
            }
        }
        for (int a = 3; a < length - 2; a += 4) {
            torch(piece, a, 0, y + 4);
            torch(piece, a, width - 1, y + 4);
        }
        spawner(at(piece, ca, 1, y + 1), SILVERFISH);
    }

    // ------------------------------------------------------------------ rooms (filled in by the rooms task)

    protected void buildRoom(StrongholdLayout.Piece piece) {
        // Bare room until furnished.
    }

    // ------------------------------------------------------------------ helpers

    protected IBlockState stair(EnumFacing climb) {
        return Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, climb)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM);
    }

    /** A torch on the inner face of wall column {@code c} (0 = left, width-1 = right). */
    protected void torch(StrongholdLayout.Piece piece, int a, int c, int y) {
        BlockPos wall = at(piece, a, c, y);
        EnumFacing into = c == 0 ? piece.facing.rotateY() : piece.facing.rotateYCCW();
        BlockPos pos = wall.offset(into);
        if (w.isPlanned(pos.getX(), y, pos.getZ()) && w.get(pos.getX(), y, pos.getZ()).getBlock() == Blocks.AIR
                && w.isSolid(wall.getX(), y, wall.getZ())) {
            set(pos, Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, into));
        }
    }

    protected ResourceLocation mob() {
        return mobs[rand.nextInt(mobs.length)];
    }

    protected void spawner(BlockPos pos, ResourceLocation mob) {
        set(pos, Blocks.MOB_SPAWNER.getDefaultState());
        w.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityMobSpawner) {
                ((TileEntityMobSpawner) tile).getSpawnerBaseLogic().setEntityId(mob);
                tile.markDirty();
            }
        });
    }

    protected void vase(BlockPos pos) {
        if (ModBlocks.VASE != null) {
            set(pos, ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT,
                    rand.nextInt(6) == 0 ? BlockVase.Variant.GILDED : BlockVase.Variant.CLAY));
        }
    }
}
