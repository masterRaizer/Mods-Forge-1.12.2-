package com.shatteredcitadels.worldgen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * The only way builders touch a structure. Writes go into a {@link Blueprint} (any thread, no
 * world access); {@link StructureJobs} later copies the blueprint into loaded, populated chunks
 * a few milliseconds per tick, so generation never loads a chunk and never freezes the server.
 * Anything that depends on the real terrain or on tile entities is recorded as a column
 * operation or an after-place action and resolved at write time.
 */
public final class BlockWriter {

    public static final int MIN_Y = 5;
    public static final int MAX_Y = 250;
    /** Notify clients only: no neighbour block updates, no physics, no cascading. */
    public static final int FLAGS = 2;

    private final Blueprint blueprint;
    private int rejected;

    public BlockWriter(Blueprint blueprint) {
        this.blueprint = blueprint;
    }

    public Blueprint getBlueprint() {
        return blueprint;
    }

    public boolean inside(int x, int y, int z) {
        return y >= MIN_Y && y <= MAX_Y;
    }

    /** @return the planned state; air where the blueprint leaves the terrain untouched. */
    public IBlockState get(int x, int y, int z) {
        IBlockState state = inside(x, y, z) ? blueprint.get(x, y, z) : null;
        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    /** @return true if the position is planned (written by this structure), whatever the state. */
    public boolean isPlanned(int x, int y, int z) {
        return blueprint.get(x, y, z) != null;
    }

    public boolean set(int x, int y, int z, IBlockState state) {
        if (!inside(x, y, z) || state == null) {
            rejected++;
            return false;
        }
        blueprint.set(x, y, z, state);
        return true;
    }

    public boolean set(BlockPos pos, IBlockState state) {
        return set(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public void fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, IBlockState state) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    public void clear(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        fill(minX, minY, minZ, maxX, maxY, maxZ, Blocks.AIR.getDefaultState());
    }

    /** @return true if the planned block is solid enough to carry a structure (terrain counts as no). */
    public boolean isSolid(int x, int y, int z) {
        IBlockState state = get(x, y, z);
        return state.getMaterial().isSolid() && state.isOpaqueCube();
    }

    /**
     * At write time: fills from {@code topY} downwards with {@code state} until solid terrain (or a
     * planned block) is reached, at most {@code maxDepth} blocks. Foundations, piers, wall footings.
     */
    public void fillDown(int x, int z, int topY, IBlockState state, int maxDepth) {
        if (topY >= MIN_Y && maxDepth > 0) {
            blueprint.fillDown(x, z, Math.min(MAX_Y, topY), state, maxDepth);
        }
    }

    /** At write time: removes terrain and vegetation from {@code fromY} up to {@code toY}. */
    public void clearColumn(int x, int z, int fromY, int toY) {
        int from = Math.max(MIN_Y, fromY);
        int to = Math.min(MAX_Y, toY);
        if (from <= to) {
            blueprint.clearColumn(x, z, from, to);
        }
    }

    /** At write time: places {@code state} only where the terrain is not already solid. */
    public void setIfNotSolid(int x, int y, int z, IBlockState state) {
        if (inside(x, y, z)) {
            blueprint.setIfNotSolid(x, y, z, state);
        }
    }

    /** Runs {@code action} on the server thread once the block at {@code pos} has been written. */
    public void afterPlace(BlockPos pos, Blueprint.TileAction action) {
        blueprint.afterPlace(pos, action);
    }

    public int getWritten() {
        return blueprint.blockCount();
    }

    /** @return writes refused because they fell outside the build height. */
    public int getRejected() {
        return rejected;
    }
}
