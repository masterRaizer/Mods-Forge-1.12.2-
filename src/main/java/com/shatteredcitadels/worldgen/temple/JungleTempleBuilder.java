package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.VaultLock;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePieces;
import com.shatteredcitadels.worldgen.stronghold.StrongholdBuilder;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The underground of a jungle temple: the stronghold's shells, doorways, corridors, traps, stairs
 * and cistern in overgrown masonry, with a spider den, storerooms, a pit hall and the goal room:
 * the idol vault (vault variant) or a treasure storeroom (boss variant, the boss is on top). One
 * of the three idol caches is down here, under a floor tile.
 */
public class JungleTempleBuilder extends StrongholdBuilder {

    public static final ResourceLocation[] DEFAULT_MOBS = {new ResourceLocation("minecraft", "zombie"),
            new ResourceLocation("minecraft", "skeleton"), new ResourceLocation("minecraft", "cave_spider")};
    static final ResourceLocation CAVE_SPIDER = new ResourceLocation("minecraft", "cave_spider");

    private final boolean boss;
    private final JunglePalette jungle;
    private int idols;

    public JungleTempleBuilder(BlockWriter w, CitadelInstance rec, Random rand, boolean boss) {
        super(w, rec, rand, new JunglePalette());
        this.jungle = (JunglePalette) pal;
        this.boss = boss;
        withMobs(DEFAULT_MOBS);
    }

    @Override
    public JungleTempleBuilder withMobs(ResourceLocation[] mobs) {
        super.withMobs(mobs);
        return this;
    }

    /** @return the caches holding an idol that this builder placed. */
    public int idolCaches() {
        return idols;
    }

    @Override
    protected IBlockState rubble() {
        return rand.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.GRAVEL.getDefaultState();
    }

    @Override
    protected IBlockState walledUpDoor() {
        return jungle.wall(rand);
    }

    @Override
    public void build(StrongholdLayout layout) {
        super.build(layout);
        cache(layout);
    }

    @Override
    protected void buildRoom(StrongholdLayout.Piece piece) {
        if (!(piece.kind instanceof JungleKind)) {
            super.buildRoom(piece);
            return;
        }
        switch ((JungleKind) piece.kind) {
            case ENTRY_HALL:
                entryHall(piece);
                break;
            case SPIDER_DEN:
                spiderDen(piece);
                break;
            case STOREROOM:
                storeroom(piece);
                break;
            case PIT_HALL:
                pitHall(piece);
                break;
            case GOAL:
                if (boss) {
                    treasureRoom(piece);
                } else {
                    vault(piece);
                }
                break;
            default:
                break;
        }
    }

    /** The start: pillars in the corners; the spiral from the temple above lands in its centre. */
    private void entryHall(StrongholdLayout.Piece piece) {
        StructureBoundingBox b = piece.box;
        int y = piece.floorY;
        for (int[] p : new int[][]{{b.minX + 2, b.minZ + 2}, {b.maxX - 2, b.minZ + 2}, {b.minX + 2, b.maxZ - 2}, {b.maxX - 2, b.maxZ - 2}}) {
            for (int fy = y + 1; fy < b.maxY; fy++) {
                w.set(p[0], fy, p[1], pal.pillar());
            }
            boolean north = p[1] < (b.minZ + b.maxZ) / 2;
            w.set(p[0], y + 3, p[1] + (north ? 1 : -1), Blocks.TORCH.getDefaultState()
                    .withProperty(BlockTorch.FACING, north ? EnumFacing.SOUTH : EnumFacing.NORTH));
        }
    }

    private void spiderDen(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        for (int a = 1; a <= piece.length - 2; a++) {
            for (int c = 1; c <= width - 2; c++) {
                if (!nearDoor(piece, a, c) && rand.nextInt(3) == 0) {
                    set(at(piece, a, c, y + 1 + rand.nextInt(3)), Blocks.WEB.getDefaultState());
                }
            }
        }
        spawner(at(piece, piece.length / 2, width / 2, y + 1), CAVE_SPIDER);
        vase(at(piece, piece.length - 2, 1, y + 1));
        torch(piece, 2, 0, y + 3);
    }

    private void storeroom(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        StructurePieces.chest(w, rec, at(piece, piece.length - 2, 1, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        vase(at(piece, piece.length - 2, width - 2, y + 1));
        vase(at(piece, 2, width - 2, y + 1));
        torch(piece, piece.length / 2, 0, y + 3);
    }

    /**
     * A pit across the hall with webs and a cave spider spawner at the bottom, crossed by a narrow
     * bridge whose pressure plates sit on dispensers that shoot up.
     */
    private void pitHall(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        int c0 = width / 2;
        for (int a = 2; a <= piece.length - 3; a++) {
            for (int c = 1; c <= width - 2; c++) {
                if (c == c0) {
                    continue;
                }
                for (int d = 0; d < JungleKind.PIT_DEPTH; d++) {
                    set(at(piece, a, c, y - d), air);
                }
                if (rand.nextInt(3) == 0) {
                    set(at(piece, a, c, y - JungleKind.PIT_DEPTH + 1), Blocks.WEB.getDefaultState());
                }
            }
        }
        spawner(at(piece, piece.length / 2, 1, y - JungleKind.PIT_DEPTH + 1), CAVE_SPIDER);
        for (int a = 3; a <= piece.length - 4; a += 3) {
            BlockPos plate = at(piece, a, c0, y + 1);
            BlockPos dispenser = plate.down();
            set(dispenser, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, EnumFacing.UP));
            set(plate, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
            arrows(dispenser);
        }
        torch(piece, 1, 0, y + 3);
        torch(piece, piece.length - 2, width - 1, y + 3);
    }

    private void arrows(BlockPos dispenser) {
        w.afterPlace(dispenser, (world, pos) -> {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityDispenser) {
                for (int slot = 0; slot < 2; slot++) {
                    ((TileEntityDispenser) tile).setInventorySlotContents(slot, new ItemStack(Items.ARROW, 16));
                }
            }
        });
    }

    /** Boss variant: the goal room is a storeroom of treasure. */
    private void treasureRoom(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        for (int c : new int[]{2, width - 3}) {
            StructurePieces.chest(w, rec, at(piece, piece.length - 2, c, y + 1), piece.facing.getOpposite(),
                    LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        }
        set(at(piece, piece.length - 2, width / 2, y + 1), Blocks.GOLD_BLOCK.getDefaultState());
        vase(at(piece, piece.length - 3, 1, y + 1));
        vase(at(piece, piece.length - 3, width - 2, y + 1));
        torch(piece, 3, 0, y + 3);
        torch(piece, 3, width - 1, y + 3);
    }

    /**
     * Vault variant: an antechamber, then a wall of vault brick with a sealed door and three idol
     * sockets; behind it the vault, every face of it vault brick. Only the idols open it.
     */
    private void vault(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        int c0 = width / 2;
        int top = piece.box.maxY;
        IBlockState brick = ModBlocks.VAULT_BRICK.getDefaultState();
        // Every face of the vault part (from the vault wall to the far wall).
        BlockPos v0 = at(piece, JungleKind.ANTECHAMBER, 0, y);
        BlockPos v1 = at(piece, piece.length - 1, width - 1, top);
        StructureBoundingBox box = new StructureBoundingBox(v0, v1);
        for (int a = JungleKind.ANTECHAMBER; a <= piece.length - 1; a++) {
            for (int c = 0; c <= width - 1; c++) {
                for (int fy = y; fy <= top; fy++) {
                    boolean face = a == JungleKind.ANTECHAMBER || a == piece.length - 1 || c == 0 || c == width - 1
                            || fy == y || fy == top;
                    set(at(piece, a, c, fy), face ? brick : air);
                }
            }
        }
        // The sealed door, three wide and three high.
        for (int c = c0 - 1; c <= c0 + 1; c++) {
            for (int fy = y + 1; fy <= y + 3; fy++) {
                BlockPos gate = at(piece, JungleKind.ANTECHAMBER, c, fy);
                set(gate, ModBlocks.SEALED_GATE.getDefaultState());
                rec.addGate(gate, VaultLock.GATE_FLOOR, Block.getStateId(air));
            }
        }
        List<BlockPos> sockets = new ArrayList<>();
        for (int[] s : new int[][]{{c0 - 3, y + 2}, {c0 + 3, y + 2}, {c0, y + 5}}) {
            BlockPos socket = at(piece, JungleKind.ANTECHAMBER, s[0], s[1]);
            set(socket, ModBlocks.IDOL_SOCKET.getDefaultState());
            sockets.add(socket);
        }
        rec.setVault(new VaultLock(box, sockets));
        // Antechamber: torches beside the sockets.
        torch(piece, JungleKind.ANTECHAMBER - 2, 0, y + 3);
        torch(piece, JungleKind.ANTECHAMBER - 2, width - 1, y + 3);
        // The treasure inside.
        for (int c : new int[]{2, width - 3}) {
            StructurePieces.chest(w, rec, at(piece, piece.length - 3, c, y + 1), piece.facing.getOpposite(),
                    LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        }
        StructurePieces.chest(w, rec, at(piece, piece.length - 2, c0, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_JUNGLE_TEMPLE, rand.nextLong());
        for (int c : new int[]{1, width - 2}) {
            set(at(piece, piece.length - 2, c, y + 1), Blocks.GOLD_BLOCK.getDefaultState());
            set(at(piece, JungleKind.ANTECHAMBER + 2, c, y + 1), Blocks.EMERALD_BLOCK.getDefaultState());
        }
        set(at(piece, (JungleKind.ANTECHAMBER + piece.length) / 2, c0, top - 1), Blocks.GLOWSTONE.getDefaultState());
    }

    /** The underground cache: a chest under a chiseled tile in a room's floor, a torch on the wall near it. */
    private void cache(StrongholdLayout layout) {
        StrongholdLayout.Piece room = null;
        for (StrongholdLayout.Piece piece : layout.pieces()) {
            if (piece.kind == JungleKind.STOREROOM || piece.kind == JungleKind.SPIDER_DEN) {
                room = piece;
                break;
            }
        }
        BlockPos tile;
        EnumFacing facing;
        if (room != null) {
            tile = at(room, 2, room.kind.width() - 3, room.floorY);
            facing = room.facing.getOpposite();
            torch(room, 2, room.kind.width() - 1, room.floorY + 3);
        } else {
            StructureBoundingBox b = layout.start().box;
            tile = new BlockPos(b.minX + 4, layout.start().floorY, b.minZ + 2);
            facing = EnumFacing.SOUTH;
        }
        set(tile, JungleTemple.CACHE_TILE);
        JungleCaches.chest(w, tile.down(), facing, !boss, rand);
        idols += boss ? 0 : 1;
    }
}
