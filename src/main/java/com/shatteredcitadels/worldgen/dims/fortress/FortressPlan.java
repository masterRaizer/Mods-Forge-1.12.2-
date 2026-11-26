package com.shatteredcitadels.worldgen.dims.fortress;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.dims.DimSites;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/** The mod's Nether fortress: a keep with a treasure hall and a web of bridges over the lava. */
public final class FortressPlan implements StructurePlan {

    public static final String KIND = "nether_fortress";
    /** Lowest and number of deck heights: well above the lava sea (31), well below the roof. */
    public static final int MIN_DECK_Y = 52;
    public static final int DECK_SPAN = 12;
    /** Pillars reach this far down from the deck. */
    public static final int PILLAR_DEPTH = 48;
    private static final int DEFAULT_BLAZE = 3;

    private final int centreX;
    private final int deckY;
    private final int centreZ;
    private final long seed;
    private final int dimension;
    private final int blaze;

    public FortressPlan(int centreX, int deckY, int centreZ, long seed, int dimension, int blaze) {
        this.centreX = centreX;
        this.deckY = deckY;
        this.centreZ = centreZ;
        this.seed = seed;
        this.dimension = dimension;
        this.blaze = Math.max(0, Math.min(FortressLayout.MAX_BLAZE, blaze));
    }

    /** A fortress with its keep in the middle of the chunk; needs no look at the world. */
    public static FortressPlan plan(int dimension, int chunkX, int chunkZ, long seed, int blaze) {
        int deckY = MIN_DECK_Y + (int) Math.floorMod(DimSites.scramble(seed), (long) DECK_SPAN);
        return new FortressPlan((chunkX << 4) + 8, deckY, (chunkZ << 4) + 8, seed, dimension, blaze);
    }

    public FortressLayout layout() {
        return FortressLayout.generate(seed, centreX, deckY, centreZ, blaze);
    }

    public int deckY() {
        return deckY;
    }

    public int blaze() {
        return blaze;
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
        int reach = FortressLayout.REACH + 1;
        return new StructureBoundingBox(centreX - reach, Math.max(BlockWriter.MIN_Y, deckY - PILLAR_DEPTH - 2), centreZ - reach,
                centreX + reach, deckY + FortressLayout.KEEP_HEIGHT + 2, centreZ + reach);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        FortressLayout layout = layout();
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate(CitadelArchetype.NETHER_CITADEL.key(), random), StructureType.NETHER_FORTRESS,
                CitadelArchetype.NETHER_CITADEL, dimension, new BlockPos(centreX, deckY, centreZ), footprint());
        // Fortress mobs spawn inside these volumes, as in the pieces of a vanilla fortress.
        for (StructureBoundingBox box : layout.boxes()) {
            record.addPart(box);
        }
        new FortressBuilder(writer, record, random).build(layout);
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("x", centreX);
        tag.setInteger("y", deckY);
        tag.setInteger("z", centreZ);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        tag.setInteger("blaze", blaze);
        return tag;
    }

    public static FortressPlan read(NBTTagCompound tag) {
        return new FortressPlan(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), tag.getLong("seed"),
                tag.hasKey("dim") ? tag.getInteger("dim") : -1, tag.hasKey("blaze") ? tag.getInteger("blaze") : DEFAULT_BLAZE);
    }

    @Override
    public String describe() {
        return "nether fortress at " + centreX + "," + deckY + "," + centreZ;
    }
}
