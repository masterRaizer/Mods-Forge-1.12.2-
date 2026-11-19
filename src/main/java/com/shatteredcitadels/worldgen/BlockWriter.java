package com.shatteredcitadels.worldgen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * The only way builders touch the world. Every write is clipped to a box that is guaranteed to
 * be loaded (during population: the 2x2 chunks being populated) and to Y {@value #MIN_Y}..
 * {@value #MAX_Y}, and uses block update flag 2 (send to clients, no neighbour updates), so
 * generation never loads or populates another chunk (no cascading worldgen lag).
 */
public final class BlockWriter {

    public static final int MIN_Y = 5;
    public static final int MAX_Y = 250;
    /** Notify clients only: no neighbour block updates, no physics, no cascading. */
    public static final int FLAGS = 2;

    private final World world;
    private final StructureBoundingBox clip;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int written;
    private int rejected;

    public BlockWriter(World world, StructureBoundingBox clip) {
        this.world = world;
        this.clip = new StructureBoundingBox(clip.minX, Math.max(MIN_Y, clip.minY), clip.minZ,
                clip.maxX, Math.min(MAX_Y, clip.maxY), clip.maxZ);
    }

    /** @return the populated 2x2-chunk area of chunk (chunkX, chunkZ), full build height. */
    public static StructureBoundingBox populationBox(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return new StructureBoundingBox(minX, MIN_Y, minZ, minX + 31, MAX_Y, minZ + 31);
    }

    public World getWorld() {
        return world;
    }

    public boolean inside(int x, int y, int z) {
        return x >= clip.minX && x <= clip.maxX && y >= clip.minY && y <= clip.maxY
                && z >= clip.minZ && z <= clip.maxZ;
    }

    /** @return the current state, or air when outside the writable area (never loads chunks). */
    public IBlockState get(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return Blocks.AIR.getDefaultState();
        }
        return world.getBlockState(cursor.setPos(x, y, z));
    }

    public boolean set(int x, int y, int z, IBlockState state) {
        if (!inside(x, y, z)) {
            rejected++;
            return false;
        }
        cursor.setPos(x, y, z);
        if (world.getBlockState(cursor) == state) {
            return true;
        }
        // Immutable copy: tile entities created by this call keep a reference to the position.
        if (world.setBlockState(cursor.toImmutable(), state, FLAGS)) {
            written++;
            return true;
        }
        return false;
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

    /** @return true if the block at the position is solid enough to carry a structure. */
    public boolean isSolid(int x, int y, int z) {
        IBlockState state = get(x, y, z);
        return state.getMaterial().isSolid() && state.isOpaqueCube();
    }

    public int getWritten() {
        return written;
    }

    /** @return writes refused because they fell outside the safe box; must stay 0 in worldgen. */
    public int getRejected() {
        return rejected;
    }
}
