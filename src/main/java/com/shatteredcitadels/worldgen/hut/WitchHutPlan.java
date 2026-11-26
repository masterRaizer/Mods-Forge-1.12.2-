package com.shatteredcitadels.worldgen.hut;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A witch hut on stilts over the swamp with, under it, a cellar lair of a few rooms: witches,
 * potion traps and a final chest. It stands where the vanilla swamp hut would.
 */
public final class WitchHutPlan implements StructurePlan {

    public static final String KIND = "witch_hut";
    /** The hut's floor is this far above the water. */
    public static final int STILTS = 3;
    /** The cellar's floor layer is this far below the surface: its ceiling keeps 3 blocks of ground above. */
    public static final int DEPTH = 9;
    /** Horizontal reach of the footprint from the centre (the cellar grid plus a margin). */
    public static final int REACH = 18;
    private static final int MIN_SURFACE = 40;

    public final int centreX;
    public final int surfaceY;
    public final int centreZ;
    public final long seed;
    public final int dimension;
    public final HutVariant variant;
    public final boolean cellar;
    public final int witches;

    public WitchHutPlan(int centreX, int surfaceY, int centreZ, long seed, int dimension, HutVariant variant,
                        boolean cellar, int witches) {
        this.centreX = centreX;
        this.surfaceY = surfaceY;
        this.centreZ = centreZ;
        this.seed = seed;
        this.dimension = dimension;
        this.variant = variant == null ? HutVariant.SMALL : variant;
        this.cellar = cellar;
        this.witches = Math.max(0, witches);
    }

    public static int hutFloor(int surfaceY) {
        return surfaceY + STILTS;
    }

    public static int cellarFloor(int surfaceY) {
        return Math.max(BlockWriter.MIN_Y + 1, surfaceY - DEPTH);
    }

    /** @return a count in {@code min..max}; a reversed range is put in order. */
    public static int witchCount(int min, int max, Random random) {
        int low = Math.max(0, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        return low + random.nextInt(high - low + 1);
    }

    /**
     * Plans the hut of the site chunk (which is loaded: it is being populated).
     *
     * @param forced the variant asked for (a command), or null to pick by seed
     */
    public static WitchHutPlan plan(World world, int chunkX, int chunkZ, long seed, HutVariant forced) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        Random random = new Random(CellarLayout.mix(seed ^ 0x5DEECE66DL));
        HutVariant rolled = HutVariant.values()[random.nextInt(HutVariant.values().length)];
        ConfigCache.Snapshot config = ConfigCache.get();
        int witches = witchCount(config.getWitchHutMinWitches(), config.getWitchHutMaxWitches(), random);
        return new WitchHutPlan(x, surface(world, x, z), z, seed, world.provider.getDimension(),
                forced != null ? forced : rolled, config.isWitchHutCellar(), witches);
    }

    /** The top water or ground block of the column, under any trees and plants. */
    private static int surface(World world, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = Math.min(BlockWriter.MAX_Y - 40, world.getHeight(x, z)); y > MIN_SURFACE; y--) {
            Material material = world.getBlockState(pos.setPos(x, y, z)).getMaterial();
            if (material.isLiquid() || material.blocksMovement() && material != Material.WOOD
                    && material != Material.LEAVES && material != Material.CACTUS && material != Material.GOURD) {
                return y;
            }
        }
        return MIN_SURFACE;
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
        return new StructureBoundingBox(centreX - REACH, cellarFloor(surfaceY) - 1, centreZ - REACH,
                centreX + REACH, HutBuilder.roofTop(HutVariant.TALL, hutFloor(surfaceY)) + 2, centreZ + REACH);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(CellarLayout.mix(seed));
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("swamp", random), StructureType.WITCH_HUT, CitadelArchetype.KEEPERS_CITADEL,
                dimension, new BlockPos(centreX, surfaceY, centreZ), footprint());
        EnumFacing front = EnumFacing.byHorizontalIndex(random.nextInt(4));
        int floor = hutFloor(surfaceY);
        HutBuilder hut = new HutBuilder(writer, record, random, variant, front, centreX, floor, centreZ, surfaceY, cellar, seed);
        hut.build();
        // The only part of the record: the box where only witches spawn (see WitchHutSpawns).
        record.addPart(hut.cabinBox());
        if (cellar) {
            CellarLayout layout = CellarLayout.generate(seed, centreX, cellarFloor(surfaceY), centreZ);
            new CellarBuilder(writer, record, random, layout, seed).build(floor, witches);
        }
        return record;
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
        tag.setString("variant", variant.id);
        tag.setBoolean("cellar", cellar);
        tag.setInteger("witches", witches);
        return tag;
    }

    public static WitchHutPlan read(NBTTagCompound tag) {
        return new WitchHutPlan(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), tag.getLong("seed"),
                tag.getInteger("dim"), HutVariant.byId(tag.getString("variant")), tag.getBoolean("cellar"),
                tag.getInteger("witches"));
    }

    @Override
    public String describe() {
        return "witch hut (" + variant.id + (cellar ? ", cellar" : "") + ") at " + centreX + "," + surfaceY + "," + centreZ;
    }
}
