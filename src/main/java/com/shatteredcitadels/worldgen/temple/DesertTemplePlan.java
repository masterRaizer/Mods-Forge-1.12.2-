package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.catacomb.CatacombBuilder;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import com.shatteredcitadels.worldgen.stronghold.StrongholdMobs;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A desert temple: a stepped sandstone pyramid on the surface with a pillared hall, an entrance
 * colonnade and two towers, a spiral from the hall down to an underground temple that ends in a
 * tomb (a pharaoh's tomb with a boss when {@code pharaoh}).
 */
public final class DesertTemplePlan implements StructurePlan {

    public static final String KIND = "desert_temple";
    /** The underground start lies this far below the pyramid floor. */
    public static final int DEPTH = 16;
    private static final int REACH = 42;

    public final int centreX;
    public final int surfaceY;
    public final int centreZ;
    public final long seed;
    public final int dimension;
    public final boolean pharaoh;

    public DesertTemplePlan(int centreX, int surfaceY, int centreZ, long seed, int dimension, boolean pharaoh) {
        this.centreX = centreX;
        this.surfaceY = surfaceY;
        this.centreZ = centreZ;
        this.seed = seed;
        this.dimension = dimension;
        this.pharaoh = pharaoh;
    }

    public static int undergroundFloor(int surfaceY) {
        return Math.max(StrongholdLayout.MIN_FLOOR_Y + 12, surfaceY - DEPTH);
    }

    /** Plans the temple of the site chunk (which is loaded: it is being populated). */
    public static DesertTemplePlan plan(World world, int chunkX, int chunkZ, long seed) {
        return plan(world, chunkX, chunkZ, seed, null);
    }

    /** @param pharaoh forced variant (a command), or null for the configured chance. */
    public static DesertTemplePlan plan(World world, int chunkX, int chunkZ, long seed, Boolean pharaoh) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int surface = Math.max(40, world.getHeight(x, z) - 1);
        boolean tomb = variantFor(pharaoh, ConfigCache.get().getDesertTempleBossChance(), new Random(seed ^ 0x5DEECE66DL));
        return new DesertTemplePlan(x, surface, z, seed, world.provider.getDimension(), tomb);
    }

    /** @return the forced variant if any, else the boss variant with probability {@code chance}. */
    public static boolean variantFor(Boolean forced, double chance, Random random) {
        return forced != null ? forced : random.nextDouble() < chance;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public StructureBoundingBox footprint() {
        return new StructureBoundingBox(centreX - REACH, Math.max(BlockWriter.MIN_Y, undergroundFloor(surfaceY) - 14), centreZ - REACH,
                centreX + REACH, Math.min(BlockWriter.MAX_Y, surfaceY + 24), centreZ + REACH);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        int floor = undergroundFloor(surfaceY);
        StrongholdLayout layout = StrongholdLayout.generate(seed, centreX, floor, centreZ, TempleKind.CATALOGUE);
        if (layout == null) {
            return null;
        }
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("desert", random), StructureType.DESERT_TEMPLE, CitadelArchetype.KEEPERS_CITADEL,
                dimension, new BlockPos(centreX, surfaceY, centreZ), footprint());
        new DesertTempleBuilder(writer, record, random, pharaoh)
                .withMobs(StrongholdMobs.resolve(ConfigCache.get().getDesertTempleSpawnerMobs(), DesertTempleBuilder.DEFAULT_MOBS))
                .build(layout);
        new Pyramid(writer, random, centreX, surfaceY, centreZ).build();
        spiral(writer, random, floor);
        return record;
    }

    /** 5x5 shaft from the pyramid floor down into the start hall: ring walls, core, steps. */
    private void spiral(BlockWriter w, Random random, int floor) {
        SandstonePalette pal = new SandstonePalette();
        int startCeiling = floor + TempleKind.ENTRY_HALL.height() - 1;
        for (int y = startCeiling; y < surfaceY; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    w.set(centreX + dx, y, centreZ + dz, ring ? pal.wall(random) : Blocks.AIR.getDefaultState());
                }
            }
        }
        for (int y = floor + 1; y <= surfaceY; y++) {
            w.set(centreX, y, centreZ, pal.pillar());
            if (y == surfaceY) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            w.set(centreX + dx, y, centreZ + dz, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
        }
        for (int[] step : CatacombBuilder.spiralPath(surfaceY, floor)) {
            IBlockState stair = Blocks.SANDSTONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.byIndex(step[3]))
                    .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM);
            w.set(centreX + step[0], step[1], centreZ + step[2], stair);
        }
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("x", centreX);
        tag.setInteger("y", surfaceY);
        tag.setInteger("z", centreZ);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        tag.setBoolean("pharaoh", pharaoh);
        return tag;
    }

    public static DesertTemplePlan read(NBTTagCompound tag) {
        return new DesertTemplePlan(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), tag.getLong("seed"),
                tag.getInteger("dim"), tag.getBoolean("pharaoh"));
    }

    @Override
    public String describe() {
        return "desert temple" + (pharaoh ? " (pharaoh's tomb)" : "") + " at " + centreX + "," + surfaceY + "," + centreZ;
    }
}
