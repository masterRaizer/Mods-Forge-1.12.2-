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
        // A sunken piece (the cistern) is solid below its room floor; stairs span their whole box.
        int floor = piece.rise != 0 ? b.minY : Math.max(b.minY, piece.floorY);
        for (int x = b.minX; x <= b.maxX; x++) {
            for (int z = b.minZ; z <= b.maxZ; z++) {
                boolean edge = x == b.minX || x == b.maxX || z == b.minZ || z == b.maxZ;
                for (int y = b.minY; y < floor; y++) {
                    w.set(x, y, z, pal.wall(rand));
                }
                w.set(x, floor, z, pal.floor(rand));
                w.set(x, b.maxY, z, pal.ceiling());
                for (int y = floor + 1; y < b.maxY; y++) {
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

    // ------------------------------------------------------------------ rooms

    /** Furnishes a room piece according to its kind. */
    protected void buildRoom(StrongholdLayout.Piece piece) {
        switch (piece.kind) {
            case CROSSING:
                crossing(piece);
                break;
            case GRAND_LIBRARY:
                grandLibrary(piece);
                break;
            case SMALL_LIBRARY:
                smallLibrary(piece);
                break;
            case PRISON:
                prison(piece);
                break;
            case ARMOURY:
                armoury(piece);
                break;
            case TREASURE:
                treasure(piece);
                break;
            case MESS_HALL:
                messHall(piece);
                break;
            case STOREROOM:
                storeroom(piece);
                break;
            case QUARTERS:
                quarters(piece);
                break;
            case CISTERN:
                cistern(piece);
                break;
            case PILLARED_HALL:
                pillaredHall(piece);
                break;
            case CRYPT:
                crypt(piece);
                break;
            default:
                break;
        }
    }

    private int lengthOf(StrongholdLayout.Piece piece) {
        return piece.length;
    }

    /** True if (a, c) on the walk level is in front of one of the piece's doorways (kept clear). */
    private boolean nearDoor(StrongholdLayout.Piece piece, int a, int c) {
        BlockPos p = at(piece, a, c, piece.floorY + 1);
        return nearDoorAt(piece, p.getX(), p.getZ());
    }

    private static boolean nearDoorAt(StrongholdLayout.Piece piece, int x, int z) {
        for (StrongholdLayout.Doorway door : piece.doors) {
            if (door.used && Math.abs(door.pos.getX() - x) <= 2 && Math.abs(door.pos.getZ() - z) <= 2) {
                return true;
            }
        }
        return false;
    }

    private void crossing(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width;
        if (piece.depth == 0) {
            // The start is laid out around its centre: four pillars and torches, the middle stays open.
            StructureBoundingBox b = piece.box;
            for (int[] p : new int[][]{{b.minX + 2, b.minZ + 2}, {b.maxX - 2, b.minZ + 2}, {b.minX + 2, b.maxZ - 2}, {b.maxX - 2, b.maxZ - 2}}) {
                for (int fy = y + 1; fy < b.maxY; fy++) {
                    w.set(p[0], fy, p[1], pal.pillar());
                }
                w.set(p[0], y + 3, p[1] + (p[1] < (b.minZ + b.maxZ) / 2 ? 1 : -1), Blocks.TORCH.getDefaultState()
                        .withProperty(BlockTorch.FACING, p[1] < (b.minZ + b.maxZ) / 2 ? EnumFacing.SOUTH : EnumFacing.NORTH));
            }
            StructurePieces.chest(w, rec, new BlockPos(b.minX + 1, y + 1, b.minZ + 1), EnumFacing.SOUTH,
                    LootTableList.CHESTS_STRONGHOLD_CROSSING, rand.nextLong());
            return;
        }
        for (int[] p : new int[][]{{2, 2}, {2, width - 3}, {lengthOf(piece) - 3, 2}, {lengthOf(piece) - 3, width - 3}}) {
            for (int fy = y + 1; fy < piece.box.maxY; fy++) {
                set(at(piece, p[0], p[1], fy), pal.pillar());
            }
        }
        StructurePieces.chest(w, rec, at(piece, 1, 1, y + 1), piece.facing, LootTableList.CHESTS_STRONGHOLD_CROSSING, rand.nextLong());
        torch(piece, lengthOf(piece) / 2, 0, y + 3);
        torch(piece, lengthOf(piece) / 2, width - 1, y + 3);
    }

    private void shelvesAlongWalls(StrongholdLayout.Piece piece, int fromY, int toY) {
        int length = lengthOf(piece);
        int width = piece.kind.width;
        for (int a = 1; a <= length - 2; a++) {
            for (int c = 1; c <= width - 2; c++) {
                boolean face = a == 1 || a == length - 2 || c == 1 || c == width - 2;
                if (!face || nearDoor(piece, a, c)) {
                    continue;
                }
                for (int y = fromY; y <= toY; y++) {
                    set(at(piece, a, c, y), Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
    }

    private void grandLibrary(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        shelvesAlongWalls(piece, y + 1, y + 4);
        // Aisles of shelves on the ground floor, a gap in the middle of each row.
        for (int c = 4; c <= width - 5; c += 3) {
            for (int a = 4; a <= length - 5; a++) {
                if (a == length / 2) {
                    continue;
                }
                for (int fy = y + 1; fy <= y + 3; fy++) {
                    set(at(piece, a, c, fy), Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
        // Gallery two blocks wide around the walls, with a fence rail and ladders.
        int gy = y + 6;
        for (int a = 1; a <= length - 2; a++) {
            for (int c = 1; c <= width - 2; c++) {
                boolean ring = a <= 2 || a >= length - 3 || c <= 2 || c >= width - 3;
                if (!ring) {
                    continue;
                }
                set(at(piece, a, c, gy), Blocks.PLANKS.getDefaultState().withProperty(net.minecraft.block.BlockPlanks.VARIANT,
                        net.minecraft.block.BlockPlanks.EnumType.DARK_OAK));
                boolean edge = a == 3 - 1 && c > 2 && c < width - 3 || a == length - 3 && c > 2 && c < width - 3
                        || c == 2 && a > 2 && a < length - 3 || c == width - 3 && a > 2 && a < length - 3;
                if (edge) {
                    set(at(piece, a, c, gy + 1), Blocks.DARK_OAK_FENCE.getDefaultState());
                }
            }
        }
        shelvesAlongWalls(piece, gy + 1, gy + 3);
        for (int fy = y + 1; fy <= gy; fy++) {
            set(at(piece, 3, 3, fy), pal.pillar());
            set(at(piece, 2, 3, fy), Blocks.LADDER.getDefaultState().withProperty(net.minecraft.block.BlockLadder.FACING,
                    piece.facing.getOpposite()));
        }
        // Chandelier.
        BlockPos centre = at(piece, length / 2, width / 2, piece.box.maxY - 1);
        set(centre, Blocks.DARK_OAK_FENCE.getDefaultState());
        set(centre.down(), Blocks.GLOWSTONE.getDefaultState());
        StructurePieces.chest(w, rec, at(piece, length / 2, 3, y + 1), piece.facing.rotateY(), LootTableList.CHESTS_STRONGHOLD_LIBRARY,
                rand.nextLong());
        StructurePieces.chest(w, rec, at(piece, length / 2, width - 3, gy + 1), piece.facing.rotateYCCW(),
                LootTableList.CHESTS_STRONGHOLD_LIBRARY, rand.nextLong());
    }

    private void smallLibrary(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        shelvesAlongWalls(piece, y + 1, y + 3);
        int mid = lengthOf(piece) / 2;
        set(at(piece, mid, piece.kind.width / 2, y + 1), Blocks.CRAFTING_TABLE.getDefaultState());
        set(at(piece, mid - 1, piece.kind.width / 2, y + 1), stair(piece.facing));
        StructurePieces.chest(w, rec, at(piece, mid + 1, piece.kind.width / 2, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_STRONGHOLD_LIBRARY, rand.nextLong());
        set(at(piece, mid, piece.kind.width / 2, piece.box.maxY - 1), Blocks.GLOWSTONE.getDefaultState());
    }

    private void prison(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        for (int side = 0; side < 2; side++) {
            int bars = side == 0 ? 3 : width - 4;
            int back = side == 0 ? 1 : width - 2;
            for (int a = 2; a <= length - 3; a++) {
                boolean divider = (a - 2) % 3 == 0;
                for (int fy = y + 1; fy <= y + 3; fy++) {
                    set(at(piece, a, bars, fy), Blocks.IRON_BARS.getDefaultState());
                    if (divider) {
                        for (int c = Math.min(bars, back); c <= Math.max(bars, back); c++) {
                            set(at(piece, a, c, fy), pal.wall(rand));
                        }
                    }
                }
                if ((a - 2) % 3 == 2 && rand.nextBoolean()) {
                    set(at(piece, a, back, y + 1), rand.nextBoolean() ? Blocks.SKULL.getDefaultState()
                            .withProperty(net.minecraft.block.BlockSkull.FACING, EnumFacing.UP) : Blocks.WEB.getDefaultState());
                }
            }
        }
        spawner(at(piece, length - 3, width / 2, y + 1), rand.nextBoolean() ? DEFAULT_MOBS[0] : DEFAULT_MOBS[1]);
        StructurePieces.chest(w, rec, at(piece, 1, width / 2 + 1, y + 1), piece.facing, LootTableList.CHESTS_STRONGHOLD_CORRIDOR,
                rand.nextLong());
    }

    private void armoury(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        set(at(piece, length / 2, width / 2, y + 1), Blocks.ANVIL.getDefaultState());
        for (int a = 2; a <= length - 3; a += 2) {
            BlockPos standPos = at(piece, a, 1, y + 1);
            EnumFacing faces = piece.facing.rotateY();
            int seedArmour = rand.nextInt(4);
            w.afterPlace(standPos, (world, pos) -> {
                net.minecraft.entity.item.EntityArmorStand stand = new net.minecraft.entity.item.EntityArmorStand(world,
                        pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                stand.rotationYaw = faces.getHorizontalAngle();
                boolean iron = seedArmour < 2;
                stand.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST,
                        new ItemStack(iron ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
                if (seedArmour % 2 == 0) {
                    stand.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD,
                            new ItemStack(iron ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
                }
                world.spawnEntity(stand);
            });
            set(at(piece, a, width - 2, y + 1), Blocks.OAK_FENCE.getDefaultState());
        }
        StructurePieces.chest(w, rec, at(piece, length - 2, width / 2, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_STRONGHOLD_CORRIDOR, rand.nextLong());
    }

    /** Behind an iron door; a button by the door, on a chiseled brick, opens it. */
    private void treasure(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        int c0 = width / 2;
        for (int c = c0 - 1; c <= c0 + 1; c++) {
            for (int fy = y + 1; fy <= y + 3; fy++) {
                if (c != c0 || fy == y + 3) {
                    set(at(piece, 0, c, fy), pal.wall(rand));
                }
            }
        }
        EnumFacing doorFacing = piece.facing;
        set(at(piece, 0, c0, y + 1), Blocks.IRON_DOOR.getDefaultState().withProperty(net.minecraft.block.BlockDoor.FACING, doorFacing)
                .withProperty(net.minecraft.block.BlockDoor.HALF, net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER));
        set(at(piece, 0, c0, y + 2), Blocks.IRON_DOOR.getDefaultState().withProperty(net.minecraft.block.BlockDoor.FACING, doorFacing)
                .withProperty(net.minecraft.block.BlockDoor.HALF, net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER));
        set(at(piece, 0, c0 - 1, y + 2), pal.accent());
        set(at(piece, -1, c0 - 1, y + 2), Blocks.STONE_BUTTON.getDefaultState()
                .withProperty(net.minecraft.block.BlockButton.FACING, piece.facing.getOpposite()));
        // And one inside on the same block, so nobody is shut in.
        set(at(piece, 1, c0 - 1, y + 2), Blocks.STONE_BUTTON.getDefaultState()
                .withProperty(net.minecraft.block.BlockButton.FACING, piece.facing));
        for (int[] p : new int[][]{{1, 1}, {1, width - 2}, {length - 2, 1}, {length - 2, width - 2}}) {
            set(at(piece, p[0], p[1], y + 1), Blocks.GOLD_BLOCK.getDefaultState());
        }
        StructurePieces.chest(w, rec, at(piece, length - 2, c0 - 1, y + 1), piece.facing.getOpposite(),
                com.shatteredcitadels.loot.ModLootTables.CATACOMB_DEEP, rand.nextLong());
        StructurePieces.chest(w, rec, at(piece, length - 2, c0 + 1, y + 1), piece.facing.getOpposite(),
                com.shatteredcitadels.loot.ModLootTables.CATACOMB_DEEP, rand.nextLong());
        set(at(piece, length / 2, c0, piece.box.maxY - 1), Blocks.GLOWSTONE.getDefaultState());
    }

    private void messHall(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        for (int c : new int[]{3, width - 4}) {
            for (int a = 3; a <= length - 4; a++) {
                if (a == length / 2) {
                    continue;
                }
                set(at(piece, a, c, y + 1), Blocks.OAK_FENCE.getDefaultState());
                set(at(piece, a, c, y + 2), Blocks.WOODEN_PRESSURE_PLATE.getDefaultState());
                set(at(piece, a, c - 1, y + 1), stair(piece.facing.rotateY()));
                set(at(piece, a, c + 1, y + 1), stair(piece.facing.rotateYCCW()));
            }
        }
        set(at(piece, length - 2, 1, y + 1), Blocks.CAULDRON.getDefaultState());
        set(at(piece, length - 2, width - 2, y + 1), Blocks.FURNACE.getDefaultState());
        set(at(piece, length - 2, width - 3, y + 1), Blocks.FURNACE.getDefaultState());
        torch(piece, length / 2, 0, y + 3);
        torch(piece, length / 2, width - 1, y + 3);
    }

    private void storeroom(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        StructurePieces.chest(w, rec, at(piece, length - 2, 1, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_STRONGHOLD_CORRIDOR, rand.nextLong());
        vase(at(piece, length - 2, width - 2, y + 1));
        vase(at(piece, 2, width - 2, y + 1));
        set(at(piece, length - 3, 1, y + 1), Blocks.LOG.getDefaultState());
        set(at(piece, length - 3, 1, y + 2), Blocks.PLANKS.getDefaultState());
        if (rand.nextBoolean()) {
            vase(at(piece, length - 2, width / 2, y + 1));
        }
    }

    private void quarters(StrongholdLayout.Piece piece) {
        StructureBoundingBox b = piece.box;
        com.shatteredcitadels.structure.RoomTag[] tags = {com.shatteredcitadels.structure.RoomTag.PANTRY,
                com.shatteredcitadels.structure.RoomTag.BEDROOM, com.shatteredcitadels.structure.RoomTag.SMELTERY};
        com.shatteredcitadels.worldgen.RoomFurnisher furnisher = new com.shatteredcitadels.worldgen.RoomFurnisher(
                new com.shatteredcitadels.worldgen.RoomFurnisher.Site() {
                    @Override
                    public boolean free(int x, int y, int z) {
                        return x > b.minX && x < b.maxX && z > b.minZ && z < b.maxZ
                                && w.get(x, y, z).getBlock() == Blocks.AIR && !nearDoorAt(piece, x, z);
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
                        StructurePieces.chest(w, rec, pos, facing, lootTable, rand.nextLong());
                    }

                    @Override
                    public void afterPlace(BlockPos pos, com.shatteredcitadels.worldgen.Blueprint.TileAction action) {
                        w.afterPlace(pos, action);
                    }
                }, rand, com.shatteredcitadels.palette.ModPaletteRegistry.quiet(
                        com.shatteredcitadels.palette.CitadelArchetype.KEEPERS_CITADEL), BlockVase.Variant.CLAY);
        furnisher.furnish(tags[rand.nextInt(tags.length)], b.minX + 1, b.minZ + 1, b.maxX - 1, b.maxZ - 1, piece.floorY);
        furnisher.abandon(b.minX + 1, b.minZ + 1, b.maxX - 1, b.maxZ - 1, piece.floorY, b.maxY - piece.floorY - 1);
    }

    /** A sunken pool two deep, crossed by two walkways. */
    private void cistern(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        for (int a = 1; a <= length - 2; a++) {
            for (int c = 1; c <= width - 2; c++) {
                boolean pool = a >= 3 && a <= length - 4 && c >= 3 && c <= width - 4;
                boolean walkway = a == length / 2 || c == width / 2;
                if (!pool || walkway) {
                    continue;
                }
                set(at(piece, a, c, y - 2), pal.floor(rand));
                set(at(piece, a, c, y - 1), Blocks.WATER.getDefaultState());
                set(at(piece, a, c, y), Blocks.WATER.getDefaultState());
            }
        }
        // Walls of the pool below the floor: the ring around it at y - 1 and y - 2.
        for (int a = 2; a <= length - 3; a++) {
            for (int c = 2; c <= width - 3; c++) {
                boolean pool = a >= 3 && a <= length - 4 && c >= 3 && c <= width - 4;
                boolean walkway = a == length / 2 || c == width / 2;
                if (!pool || walkway) {
                    set(at(piece, a, c, y - 1), pal.wall(rand));
                    set(at(piece, a, c, y - 2), pal.wall(rand));
                }
            }
        }
        for (int a = 2; a <= length - 3; a += 4) {
            torch(piece, a, 0, y + 3);
            torch(piece, a, width - 1, y + 3);
        }
    }

    private void pillaredHall(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        for (int c : new int[]{3, width - 4}) {
            for (int a = 2; a <= length - 3; a += 3) {
                for (int fy = y + 1; fy < piece.box.maxY; fy++) {
                    set(at(piece, a, c, fy), pal.pillar());
                }
                torch(piece, a, 0, y + 4);
            }
        }
        for (int a = 2; a <= length - 3; a++) {
            set(at(piece, a, width / 2, y), pal.accent());
        }
    }

    private void crypt(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int length = lengthOf(piece);
        int width = piece.kind.width;
        for (int c : new int[]{2, width - 3}) {
            for (int a = 2; a + 1 <= length - 3; a += 3) {
                for (int da = 0; da <= 1; da++) {
                    set(at(piece, a + da, c, y + 1), pal.pillar());
                    set(at(piece, a + da, c, y + 2), Blocks.STONE_SLAB.getDefaultState().withProperty(
                            net.minecraft.block.BlockStoneSlab.VARIANT, net.minecraft.block.BlockStoneSlab.EnumType.SMOOTHBRICK));
                }
            }
        }
        set(at(piece, length - 2, width / 2, y + 1), Blocks.SKULL.getDefaultState()
                .withProperty(net.minecraft.block.BlockSkull.FACING, EnumFacing.UP));
        spawner(at(piece, length / 2, width / 2, y + 1), DEFAULT_MOBS[2]);
        vase(at(piece, 1, 1, y + 1));
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
