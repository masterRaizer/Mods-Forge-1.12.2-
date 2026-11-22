package com.shatteredcitadels.worldgen.stronghold;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.catacomb.CatacombSite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/** The End stronghold centred on a stronghold site (the block the eye of ender leads to). */
public final class StrongholdPlan implements StructurePlan {

    public static final String KIND = "stronghold";
    /** Lowest main floor: stairs may lead 12 lower and no floor may be below y 12 (liquid caverns). */
    public static final int MIN_MAIN_FLOOR = StrongholdLayout.MIN_FLOOR_Y + 12;
    private static final int REACH = StrongholdLayout.RADIUS + 2;

    public final int centreX;
    public final int floorY;
    public final int centreZ;
    public final long seed;
    public final int dimension;

    public StrongholdPlan(int centreX, int floorY, int centreZ, long seed, int dimension) {
        this.centreX = centreX;
        this.floorY = floorY;
        this.centreZ = centreZ;
        this.seed = seed;
        this.dimension = dimension;
    }

    /** @return the main floor: y 24-35, at least 20 under the estimated lowest ground, never below 24. */
    public static int floorYFor(int estimatedSurface, Random random) {
        int wanted = MIN_MAIN_FLOOR + random.nextInt(12);
        return Math.max(MIN_MAIN_FLOOR, Math.min(wanted, estimatedSurface - 20));
    }

    /** Plans the stronghold of the site in chunk ({@code chunkX}, {@code chunkZ}); only the biome provider is read. */
    public static StrongholdPlan plan(World world, int chunkX, int chunkZ, long seed) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int surface = CatacombSite.estimatedSurface(CatacombSite.minBaseHeight(world, x, z, REACH));
        return new StrongholdPlan(x, floorYFor(surface, new Random(seed)), z, seed, world.provider.getDimension());
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
        return new StructureBoundingBox(centreX - REACH, Math.max(BlockWriter.MIN_Y, floorY - 14), centreZ - REACH,
                centreX + REACH, floorY + 26, centreZ + REACH);
    }

    private transient StructureBoundingBox portalArea;

    /** The portal room: its chunks are written first. */
    @Override
    public StructureBoundingBox priorityArea() {
        if (portalArea == null) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, centreX, floorY, centreZ);
            portalArea = layout == null || layout.portalRoom() == null
                    ? new StructureBoundingBox(centreX, floorY, centreZ, centreX, floorY, centreZ) : layout.portalRoom().box;
        }
        return portalArea;
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        StrongholdLayout layout = StrongholdLayout.generate(seed, centreX, floorY, centreZ);
        if (layout == null) {
            return null;
        }
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("crypt", random), StructureType.STRONGHOLD, CitadelArchetype.KEEPERS_CITADEL,
                dimension, new BlockPos(centreX, floorY, centreZ), footprint());
        new StrongholdBuilder(writer, record, random).withMobs(StrongholdMobs.configured()).build(layout);
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("x", centreX);
        tag.setInteger("y", floorY);
        tag.setInteger("z", centreZ);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        return tag;
    }

    public static StrongholdPlan read(NBTTagCompound tag) {
        return new StrongholdPlan(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), tag.getLong("seed"),
                tag.getInteger("dim"));
    }

    @Override
    public String describe() {
        return "stronghold at " + centreX + "," + floorY + "," + centreZ;
    }
}
