package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePieces;
import com.shatteredcitadels.worldgen.stronghold.StrongholdBuilder;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.Random;

/**
 * The underground of a desert temple: the stronghold's shells, doorways, corridors, traps and
 * stairs in sandstone, with burial chambers, storerooms, a scroll library, a pillared hall, a
 * treasury and the tomb. A pharaoh's tomb is a boss arena whose sarcophagus stays sealed until
 * the boss is dead (floor 0, the only floor; the tomb is the only protected part).
 */
public class DesertTempleBuilder extends StrongholdBuilder {

    public static final ResourceLocation PHARAOH = new ResourceLocation("atum", "pharaoh");
    public static final ResourceLocation[] DEFAULT_MOBS = {new ResourceLocation("minecraft", "husk"),
            new ResourceLocation("minecraft", "skeleton"), new ResourceLocation("minecraft", "spider")};
    /** Floor index of the pharaoh's tomb. */
    public static final int TOMB_FLOOR = 0;

    private final boolean pharaoh;
    private final SandstonePalette sand;

    public DesertTempleBuilder(BlockWriter w, CitadelInstance rec, Random rand, boolean pharaoh) {
        super(w, rec, rand, new SandstonePalette());
        this.sand = (SandstonePalette) pal;
        this.pharaoh = pharaoh;
        withMobs(DEFAULT_MOBS);
    }

    @Override
    public DesertTempleBuilder withMobs(ResourceLocation[] mobs) {
        super.withMobs(mobs);
        return this;
    }

    @Override
    protected void buildRoom(StrongholdLayout.Piece piece) {
        if (!(piece.kind instanceof TempleKind)) {
            super.buildRoom(piece);
            return;
        }
        switch ((TempleKind) piece.kind) {
            case ENTRY_HALL:
                entryHall(piece);
                break;
            case BURIAL_CHAMBER:
                burialChamber(piece);
                break;
            case STOREROOM:
                storeroom(piece);
                break;
            case SCROLL_LIBRARY:
                scrollLibrary(piece);
                break;
            case PILLARED_HALL:
                pillaredHall(piece);
                break;
            case TREASURY:
                treasury(piece);
                break;
            case TOMB:
                tomb(piece);
                break;
            default:
                break;
        }
    }

    private IBlockState slab() {
        return Blocks.STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.SAND);
    }

    /** The start: laid out around its centre (the spiral from the pyramid comes down there). */
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
        // A terracotta ring in the floor around the spiral.
        int cx = (b.minX + b.maxX) / 2;
        int cz = (b.minZ + b.maxZ) / 2;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
                    w.set(cx + dx, y, cz + dz, (dx + dz) % 2 == 0 ? sand.orangeTerracotta() : sand.blueTerracotta());
                }
            }
        }
    }

    private void sarcophagus(StrongholdLayout.Piece piece, int a, int c, int y) {
        for (int da = 0; da <= 1; da++) {
            set(at(piece, a + da, c, y + 1), pal.accent());
            set(at(piece, a + da, c, y + 2), slab());
        }
    }

    private void burialChamber(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        for (int c : new int[]{2, width - 3}) {
            for (int a = 2; a + 1 <= piece.length - 3; a += 3) {
                sarcophagus(piece, a, c, y);
            }
        }
        set(at(piece, piece.length - 2, width / 2, y + 1), Blocks.SKULL.getDefaultState().withProperty(BlockSkull.FACING, EnumFacing.UP));
        spawner(at(piece, piece.length / 2, width / 2, y + 1), DEFAULT_MOBS[0]);
        vase(at(piece, 1, 1, y + 1));
        vase(at(piece, 1, width - 2, y + 1));
    }

    private void storeroom(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        StructurePieces.chest(w, rec, at(piece, piece.length - 2, 1, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_DESERT_PYRAMID, rand.nextLong());
        vase(at(piece, piece.length - 2, width - 2, y + 1));
        vase(at(piece, 2, width - 2, y + 1));
        vase(at(piece, piece.length - 3, width - 2, y + 1));
    }

    private void scrollLibrary(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        for (int a = 1; a <= piece.length - 2; a++) {
            for (int c = 1; c <= width - 2; c++) {
                boolean face = a == 1 || a == piece.length - 2 || c == 1 || c == width - 2;
                if (face && !nearDoor(piece, a, c)) {
                    for (int fy = y + 1; fy <= y + 3; fy++) {
                        set(at(piece, a, c, fy), Blocks.BOOKSHELF.getDefaultState());
                    }
                }
            }
        }
        StructurePieces.chest(w, rec, at(piece, piece.length / 2, width / 2, y + 1), piece.facing.getOpposite(),
                LootTableList.CHESTS_DESERT_PYRAMID, rand.nextLong());
        set(at(piece, piece.length / 2, width / 2, piece.box.maxY - 1), Blocks.GLOWSTONE.getDefaultState());
    }

    private void pillaredHall(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        for (int c : new int[]{3, width - 4}) {
            for (int a = 2; a <= piece.length - 3; a += 3) {
                for (int fy = y + 1; fy < piece.box.maxY; fy++) {
                    set(at(piece, a, c, fy), pal.pillar());
                }
            }
        }
        for (int a = 2; a <= piece.length - 3; a++) {
            set(at(piece, a, width / 2, y), a % 2 == 0 ? sand.orangeTerracotta() : sand.blueTerracotta());
        }
        torch(piece, piece.length / 2, 0, y + 3);
        torch(piece, piece.length / 2, width - 1, y + 3);
    }

    /** Gold and two chests; a pressure plate over TNT in the middle, as in the vanilla temple. */
    private void treasury(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        for (int[] p : new int[][]{{1, 1}, {1, width - 2}, {piece.length - 2, 1}, {piece.length - 2, width - 2}}) {
            set(at(piece, p[0], p[1], y + 1), Blocks.GOLD_BLOCK.getDefaultState());
        }
        StructurePieces.chest(w, rec, at(piece, piece.length - 2, width / 2 - 1, y + 1), piece.facing.getOpposite(),
                ModLootTables.CATACOMB_DEEP, rand.nextLong());
        StructurePieces.chest(w, rec, at(piece, piece.length - 2, width / 2 + 1, y + 1), piece.facing.getOpposite(),
                ModLootTables.CATACOMB_DEEP, rand.nextLong());
        BlockPos plate = at(piece, piece.length / 2, width / 2, y + 1);
        set(plate.down(), Blocks.TNT.getDefaultState());
        set(plate, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
    }

    /**
     * The tomb: pillars, a raised sarcophagus. Plain: two chests beside it. Pharaoh: a boss altar
     * in front of it and the sarcophagus chest sealed until the boss is dead.
     */
    private void tomb(StrongholdLayout.Piece piece) {
        int y = piece.floorY;
        int width = piece.kind.width();
        int c0 = width / 2;
        for (int c : new int[]{2, width - 3}) {
            for (int a = 2; a <= piece.length - 3; a += 3) {
                for (int fy = y + 1; fy < piece.box.maxY; fy++) {
                    set(at(piece, a, c, fy), pal.pillar());
                }
            }
        }
        int sa = piece.length - 4;
        for (int a = sa - 1; a <= sa + 1; a++) {
            for (int c = c0 - 1; c <= c0 + 1; c++) {
                set(at(piece, a, c, y + 1), (a + c) % 2 == 0 ? Blocks.GOLD_BLOCK.getDefaultState() : pal.accent());
            }
        }
        for (int a = 3; a < sa - 2; a++) {
            set(at(piece, a, c0, y), a % 2 == 0 ? sand.orangeTerracotta() : sand.blueTerracotta());
        }
        torch(piece, 3, 0, y + 4);
        torch(piece, 3, width - 1, y + 4);
        torch(piece, piece.length - 3, 0, y + 4);
        torch(piece, piece.length - 3, width - 1, y + 4);
        BlockPos chest = at(piece, sa, c0, y + 2);
        if (!pharaoh) {
            StructurePieces.chest(w, rec, chest, piece.facing.getOpposite(), LootTableList.CHESTS_DESERT_PYRAMID, rand.nextLong());
            StructurePieces.chest(w, rec, at(piece, sa, c0 - 3, y + 1), piece.facing.getOpposite(),
                    LootTableList.CHESTS_DESERT_PYRAMID, rand.nextLong());
            return;
        }
        StructurePieces.chest(w, rec, chest, piece.facing.getOpposite(), ModLootTables.BOSS_CHEST_NORMAL, rand.nextLong());
        if (ModBlocks.SEALED_GATE != null && ConfigCache.get().isLockFloorsUntilCleared()) {
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                seal(chest.offset(side));
            }
            seal(chest.up());
        }
        BlockPos altar = at(piece, piece.length / 2 - 2, c0, y);
        StructurePieces.bossAltar(w, altar, rec);
        StructureBoundingBox box = piece.box;
        StructureBoundingBox arena = new StructureBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        rec.setBossArena(arena, altar.up());
        rec.addFloor(TOMB_FLOOR, box.minY, box.maxY, 5, 0, true).setArea(box.minX, box.minZ, box.maxX, box.maxZ)
                .setZoneKey("pharaoh_tomb");
        rec.setPreferredBoss(PHARAOH);
        rec.addPart(arena);
    }

    private void seal(BlockPos pos) {
        IBlockState replaced = w.get(pos.getX(), pos.getY(), pos.getZ());
        if (w.set(pos, ModBlocks.SEALED_GATE.getDefaultState())) {
            rec.addGate(pos, TOMB_FLOOR, Block.getStateId(replaced));
        }
    }
}
