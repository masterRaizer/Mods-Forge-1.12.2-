package com.shatteredcitadels.worldgen;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A whole structure built in memory: block states, column operations that depend on the terrain
 * (foundations down to solid ground, terraforming) and tile entity set-up actions. Builders fill
 * it through {@link BlockWriter} on any thread; {@link StructureJobs} later writes it chunk by
 * chunk into the world on the server thread.
 */
public final class Blueprint {

    /** Writes something into the world once the block at {@code pos} exists (server thread). */
    public interface TileAction {
        void apply(World world, BlockPos pos);
    }

    /** Column operation resolved against the real terrain at write time. */
    static final class ColumnOp {
        static final int FILL_DOWN = 0;
        static final int CLEAR = 1;
        static final int SET_IF_NOT_SOLID = 2;

        final int kind;
        final int x;
        final int z;
        final int y;
        final int stateIdOrToY;
        final int maxDepth;

        ColumnOp(int kind, int x, int z, int y, int stateIdOrToY, int maxDepth) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.y = y;
            this.stateIdOrToY = stateIdOrToY;
            this.maxDepth = maxDepth;
        }
    }

    /** Everything one chunk receives, in write order: clears, blocks, fills, tile actions. */
    static final class ChunkPart {
        final int chunkX;
        final int chunkZ;
        final List<ColumnOp> clears = new ArrayList<>();
        final IntArrayList positions = new IntArrayList();
        final IntArrayList states = new IntArrayList();
        final List<ColumnOp> fills = new ArrayList<>();
        final List<BlockPos> actionPositions = new ArrayList<>();
        final List<TileAction> actions = new ArrayList<>();

        ChunkPart(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        int size() {
            return clears.size() + positions.size() + fills.size() + actions.size();
        }

        static int pack(int x, int y, int z) {
            return (x & 15) | ((z & 15) << 4) | (y << 8);
        }

        BlockPos unpack(int packed) {
            return new BlockPos((chunkX << 4) | (packed & 15), packed >>> 8, (chunkZ << 4) | ((packed >>> 4) & 15));
        }
    }

    private final Long2IntOpenHashMap states = new Long2IntOpenHashMap();
    private final List<ColumnOp> ops = new ArrayList<>();
    private final List<BlockPos> actionPositions = new ArrayList<>();
    private final List<TileAction> actions = new ArrayList<>();
    private Long2ObjectOpenHashMap<ChunkPart> parts;

    public Blueprint() {
        states.defaultReturnValue(-1);
    }

    /** Same packing as {@code BlockPos#toLong()} (decoded by {@code BlockPos.fromLong}), without allocating. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    /** @return the planned state, or null if the blueprint leaves this position to the terrain. */
    @SuppressWarnings("deprecation")
    IBlockState get(int x, int y, int z) {
        int id = states.get(pack(x, y, z));
        return id < 0 ? null : Block.getStateById(id);
    }

    void set(int x, int y, int z, IBlockState state) {
        states.put(pack(x, y, z), Block.getStateId(state));
    }

    void fillDown(int x, int z, int topY, IBlockState state, int maxDepth) {
        ops.add(new ColumnOp(ColumnOp.FILL_DOWN, x, z, topY, Block.getStateId(state), maxDepth));
    }

    void clearColumn(int x, int z, int fromY, int toY) {
        ops.add(new ColumnOp(ColumnOp.CLEAR, x, z, fromY, toY, 0));
    }

    void setIfNotSolid(int x, int y, int z, IBlockState state) {
        ops.add(new ColumnOp(ColumnOp.SET_IF_NOT_SOLID, x, z, y, Block.getStateId(state), 0));
    }

    void afterPlace(BlockPos pos, TileAction action) {
        actionPositions.add(pos.toImmutable());
        actions.add(action);
    }

    /**
     * Replaces block states of an unsealed blueprint (a structure moved to another dimension) and
     * keeps terrain clearing at or below {@code clearCeiling}.
     *
     * @param stateIds maps a {@link Block#getStateId state id} to the id to place instead
     */
    public void remap(java.util.function.IntUnaryOperator stateIds, int clearCeiling) {
        for (long key : states.keySet().toLongArray()) {
            int old = states.get(key);
            int mapped = stateIds.applyAsInt(old);
            if (mapped != old) {
                states.put(key, mapped);
            }
        }
        for (int i = 0; i < ops.size(); i++) {
            ColumnOp op = ops.get(i);
            if (op.kind == ColumnOp.CLEAR) {
                if (op.stateIdOrToY > clearCeiling) {
                    ops.set(i, new ColumnOp(ColumnOp.CLEAR, op.x, op.z, op.y, clearCeiling, 0));
                }
            } else {
                int mapped = stateIds.applyAsInt(op.stateIdOrToY);
                if (mapped != op.stateIdOrToY) {
                    ops.set(i, new ColumnOp(op.kind, op.x, op.z, op.y, mapped, op.maxDepth));
                }
            }
        }
    }

    public int blockCount() {
        return states.size();
    }

    /** Splits the blueprint into per-chunk parts; the builder must not write afterwards. */
    void seal() {
        Long2ObjectOpenHashMap<ChunkPart> map = new Long2ObjectOpenHashMap<>();
        for (Long2IntOpenHashMap.Entry entry : states.long2IntEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            ChunkPart part = part(map, pos.getX() >> 4, pos.getZ() >> 4);
            part.positions.add(ChunkPart.pack(pos.getX(), pos.getY(), pos.getZ()));
            part.states.add(entry.getIntValue());
        }
        for (ColumnOp op : ops) {
            ChunkPart part = part(map, op.x >> 4, op.z >> 4);
            (op.kind == ColumnOp.CLEAR ? part.clears : part.fills).add(op);
        }
        for (int i = 0; i < actions.size(); i++) {
            BlockPos pos = actionPositions.get(i);
            ChunkPart part = part(map, pos.getX() >> 4, pos.getZ() >> 4);
            part.actionPositions.add(pos);
            part.actions.add(actions.get(i));
        }
        states.clear();
        states.trim();
        ops.clear();
        actions.clear();
        actionPositions.clear();
        parts = map;
    }

    private static ChunkPart part(Long2ObjectOpenHashMap<ChunkPart> map, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        ChunkPart part = map.get(key);
        if (part == null) {
            part = new ChunkPart(chunkX, chunkZ);
            map.put(key, part);
        }
        return part;
    }

    /** @return chunk key -> part; empty before {@link #seal()}. */
    Long2ObjectOpenHashMap<ChunkPart> parts() {
        return parts == null ? new Long2ObjectOpenHashMap<ChunkPart>() : parts;
    }

    List<Long> chunkKeys() {
        return parts == null ? Collections.<Long>emptyList() : new ArrayList<>(parts.keySet());
    }
}
