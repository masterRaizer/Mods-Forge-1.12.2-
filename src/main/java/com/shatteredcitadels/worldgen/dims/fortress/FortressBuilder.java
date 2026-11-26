package com.shatteredcitadels.worldgen.dims.fortress;

import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePieces;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.Random;

/**
 * Writes the fortress of a {@link FortressLayout}: nether brick decks on pillars, parapets with
 * fences, blaze platforms, wart rooms, turrets and the keep. Runs on a worker thread: it only
 * writes to the blueprint.
 */
final class FortressBuilder {

    private static final ResourceLocation BLAZE = new ResourceLocation("minecraft", "blaze");
    private static final EnumFacing[] SIDES = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};

    private final BlockWriter writer;
    private final CitadelInstance record;
    private final Random random;
    private final IBlockState brick = Blocks.NETHER_BRICK.getDefaultState();
    private final IBlockState red = Blocks.RED_NETHER_BRICK.getDefaultState();
    private final IBlockState fence = Blocks.NETHER_BRICK_FENCE.getDefaultState();
    private final IBlockState magma = Blocks.MAGMA.getDefaultState();
    private int deck;

    FortressBuilder(BlockWriter writer, CitadelInstance record, Random random) {
        this.writer = writer;
        this.record = record;
        this.random = random;
    }

    void build(FortressLayout layout) {
        deck = layout.deckY;
        for (FortressLayout.Node node : layout.nodes()) {
            switch (node.kind()) {
                case KEEP:
                    keep(node);
                    break;
                case BLAZE:
                    platform(node);
                    blazeDais(node);
                    break;
                case WART:
                    room(node, false);
                    wartBeds(node);
                    break;
                case TURRET:
                    room(node, true);
                    StructurePieces.chest(writer, record, new BlockPos(node.x, deck + 1, node.z), facingDoor(node),
                            LootTableList.CHESTS_NETHER_BRIDGE, random.nextLong());
                    break;
                default:
                    platform(node);
                    break;
            }
        }
        for (FortressLayout.Edge edge : layout.edges()) {
            edge(layout, edge);
        }
        // Doors last: they cut through the walls and parapets the pieces put up.
        for (FortressLayout.Node node : layout.nodes()) {
            doors(node);
        }
    }

    // ------------------------------------------------------------------ nodes

    /** An open crossing: a deck with a parapet and corner posts. */
    private void platform(FortressLayout.Node node) {
        int h = node.half();
        writer.fill(node.x - h, deck - 1, node.z - h, node.x + h, deck, node.z + h, brick);
        writer.clear(node.x - h, deck + 1, node.z - h, node.x + h, deck + FortressLayout.NODE_HEIGHT, node.z + h);
        for (int o = -h; o <= h; o++) {
            boolean post = Math.abs(o) == h || Math.abs(o) == 2;
            for (int side = 0; side < 4; side++) {
                int x = node.x + perimeterX(side, o, h);
                int z = node.z + perimeterZ(side, o, h);
                writer.set(x, deck + 1, z, brick);
                if (post) {
                    writer.set(x, deck + 2, z, Math.abs(o) == h ? brick : fence);
                    if (Math.abs(o) == h) {
                        writer.set(x, deck + 3, z, fence);
                    }
                }
            }
        }
        cornerPillars(node, 2);
    }

    private void blazeDais(FortressLayout.Node node) {
        writer.fill(node.x - 2, deck + 1, node.z - 2, node.x + 2, deck + 1, node.z + 2, brick);
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                writer.set(node.x + dx, deck + 2, node.z + dz, fence);
            }
        }
        BlockPos pos = new BlockPos(node.x, deck + 2, node.z);
        if (writer.set(pos, Blocks.MOB_SPAWNER.getDefaultState())) {
            writer.afterPlace(pos, (world, at) -> {
                TileEntity tile = world.getTileEntity(at);
                if (tile instanceof TileEntityMobSpawner) {
                    ((TileEntityMobSpawner) tile).getSpawnerBaseLogic().setEntityId(BLAZE);
                }
            });
        }
    }

    /** A roofed room with fence windows; a turret also gets battlements. */
    private void room(FortressLayout.Node node, boolean battlements) {
        int h = node.half();
        int roof = deck + FortressLayout.NODE_HEIGHT - 1;
        writer.fill(node.x - h, deck - 1, node.z - h, node.x + h, roof, node.z + h, brick);
        writer.clear(node.x - h + 1, deck + 1, node.z - h + 1, node.x + h - 1, roof - 1, node.z + h - 1);
        for (int side = 0; side < 4; side++) {
            for (int o = -h; o <= h; o++) {
                int x = node.x + perimeterX(side, o, h);
                int z = node.z + perimeterZ(side, o, h);
                if (Math.abs(o) == 2) {
                    writer.set(x, deck + 2, z, fence);
                    writer.set(x, deck + 3, z, fence);
                }
                if (battlements && (o & 1) == 0) {
                    writer.set(x, roof + 1, z, brick);
                }
            }
        }
        cornerPillars(node, 2);
    }

    /** Four soul sand beds with nether wart around a cross of paths. */
    private void wartBeds(FortressLayout.Node node) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 || dz == 0) {
                    continue;
                }
                writer.set(node.x + dx, deck, node.z + dz, Blocks.SOUL_SAND.getDefaultState());
                writer.set(node.x + dx, deck + 1, node.z + dz,
                        Blocks.NETHER_WART.getDefaultState().withProperty(BlockNetherWart.AGE, random.nextInt(4)));
            }
        }
    }

    /** The keep: a tall hall with red pillars and the treasure on a dais. */
    private void keep(FortressLayout.Node node) {
        int h = node.half();
        int roof = deck + FortressLayout.KEEP_HEIGHT - 2;
        writer.fill(node.x - h, deck - 1, node.z - h, node.x + h, roof, node.z + h, brick);
        writer.clear(node.x - h + 1, deck + 1, node.z - h + 1, node.x + h - 1, roof - 1, node.z + h - 1);
        for (int side = 0; side < 4; side++) {
            for (int o = -h; o <= h; o++) {
                int x = node.x + perimeterX(side, o, h);
                int z = node.z + perimeterZ(side, o, h);
                if (Math.abs(o) == 3 || Math.abs(o) == 6) {
                    writer.set(x, deck + 3, z, fence);
                    writer.set(x, deck + 4, z, fence);
                }
                if ((o & 1) == 0) {
                    writer.set(x, roof + 1, z, brick);
                }
            }
        }
        for (int dx = -4; dx <= 4; dx += 8) {
            for (int dz = -4; dz <= 4; dz += 8) {
                writer.fill(node.x + dx, deck + 1, node.z + dz, node.x + dx, roof - 1, node.z + dz, red);
                writer.set(node.x + dx, deck + 4, node.z + dz, magma);
            }
        }
        writer.fill(node.x - 2, deck + 1, node.z - 2, node.x + 2, deck + 1, node.z + 2, red);
        writer.set(node.x, deck + 2, node.z, magma);
        // Vanilla fortress loot, plus the mod's own.
        ResourceLocation[] loot = {LootTableList.CHESTS_NETHER_BRIDGE, ModLootTables.FLOOR_TIER_3_4,
                LootTableList.CHESTS_NETHER_BRIDGE, ModLootTables.UNDERGROUND_CRYPT};
        for (int side = 0; side < 4; side++) {
            EnumFacing facing = SIDES[side];
            StructurePieces.chest(writer, record,
                    new BlockPos(node.x + facing.getXOffset() * 2, deck + 2, node.z + facing.getZOffset() * 2), facing,
                    loot[side], random.nextLong());
        }
        cornerPillars(node, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                writer.fillDown(node.x + dx, node.z + dz, deck - 2, brick, FortressPlan.PILLAR_DEPTH);
            }
        }
    }

    private void cornerPillars(FortressLayout.Node node, int size) {
        int h = node.half();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int a = 0; a < size; a++) {
                    for (int b = 0; b < size; b++) {
                        writer.fillDown(node.x + sx * (h - a), node.z + sz * (h - b), deck - 2, brick, FortressPlan.PILLAR_DEPTH);
                    }
                }
            }
        }
    }

    /** Opens the wall or parapet towards every bridge of the node. */
    private void doors(FortressLayout.Node node) {
        int h = node.half();
        int top = deck + (node.kind() == FortressLayout.Kind.KEEP ? 4 : 3);
        for (int side = 0; side < 4; side++) {
            if (!node.doors[side]) {
                continue;
            }
            for (int o = -1; o <= 1; o++) {
                int x = node.x + perimeterX(side, o, h);
                int z = node.z + perimeterZ(side, o, h);
                writer.clear(x, deck + 1, z, x, top, z);
            }
        }
    }

    private EnumFacing facingDoor(FortressLayout.Node node) {
        for (int side = 0; side < 4; side++) {
            if (node.doors[side]) {
                return SIDES[side];
            }
        }
        return EnumFacing.NORTH;
    }

    /** Offset along X of the point {@code o} along a side of the square (0 north, 1 east, 2 south, 3 west). */
    private static int perimeterX(int side, int o, int half) {
        return side == 1 ? half : side == 3 ? -half : o;
    }

    private static int perimeterZ(int side, int o, int half) {
        return side == 0 ? -half : side == 2 ? half : o;
    }

    // ------------------------------------------------------------------ bridges and corridors

    private void edge(FortressLayout layout, FortressLayout.Edge edge) {
        net.minecraft.world.gen.structure.StructureBoundingBox box = layout.edgeBox(edge);
        boolean alongX = edge.alongX();
        int from = alongX ? box.minX : box.minZ;
        int to = alongX ? box.maxX : box.maxZ;
        int axis = alongX ? box.minZ + FortressLayout.EDGE_HALF : box.minX + FortressLayout.EDGE_HALF;
        int middle = (from + to) / 2;
        for (int t = from; t <= to; t++) {
            for (int w = -FortressLayout.EDGE_HALF; w <= FortressLayout.EDGE_HALF; w++) {
                int x = alongX ? t : axis + w;
                int z = alongX ? axis + w : t;
                boolean wall = Math.abs(w) == FortressLayout.EDGE_HALF;
                writer.set(x, deck, z, brick);
                writer.clear(x, deck + 1, z, x, deck + FortressLayout.EDGE_HEIGHT, z);
                if (edge.covered) {
                    writer.set(x, deck + FortressLayout.EDGE_HEIGHT, z, brick);
                    if (wall) {
                        boolean window = (t - from) % 3 == 1;
                        writer.fill(x, deck + 1, z, x, deck + FortressLayout.EDGE_HEIGHT - 1, z, brick);
                        if (window) {
                            writer.set(x, deck + 2, z, fence);
                            writer.set(x, deck + 3, z, fence);
                        }
                    }
                } else if (wall) {
                    writer.set(x, deck + 1, z, brick);
                    if ((t - from) % 3 == 1) {
                        writer.set(x, deck + 2, z, fence);
                    }
                }
                if (!wall && Math.abs(t - middle) <= 1) {
                    // One pillar under the middle of the span, with a short arch to either side.
                    if (t == middle) {
                        writer.fillDown(x, z, deck - 1, brick, FortressPlan.PILLAR_DEPTH);
                    } else {
                        writer.set(x, deck - 1, z, brick);
                    }
                }
            }
        }
    }
}
