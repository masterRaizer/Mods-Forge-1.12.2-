package com.shatteredcitadels.worldgen.catacomb;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/** A buried piece of catacomb: one small level, no entrance, no boss; takes the place of a vanilla spawner dungeon. */
public final class FragmentPlan implements StructurePlan {

    public static final String KIND = "fragment";
    private static final int HALF = CatacombBuilder.FRAGMENT_CELLS * CatacombBuilder.CELL / 2 + 1;

    public final int centreX;
    public final int centreZ;
    public final int levelY;
    public final long seed;
    public final int dimension;

    public FragmentPlan(int centreX, int centreZ, int levelY, long seed, int dimension) {
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.levelY = levelY;
        this.seed = seed;
        this.dimension = dimension;
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
        return new StructureBoundingBox(centreX - HALF - 1, Math.max(BlockWriter.MIN_Y, levelY - 1), centreZ - HALF - 1,
                centreX + HALF + 1, levelY + CatacombBuilder.CORRIDOR_HEIGHT + 4, centreZ + HALF + 1);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        CitadelArchetype archetype = CitadelArchetype.KEEPERS_CITADEL;
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("crypt", random), StructureType.CRYPT, archetype, dimension,
                new BlockPos(centreX, levelY, centreZ), footprint());
        CatacombBuilder builder = new CatacombBuilder(writer, record, ModPaletteRegistry.forCitadel(archetype, random.nextLong()),
                random, 1);
        return builder.buildFragment(centreX, centreZ, levelY, false, levelY) == 0 ? null : record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("x", centreX);
        tag.setInteger("z", centreZ);
        tag.setInteger("y", levelY);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        return tag;
    }

    public static FragmentPlan read(NBTTagCompound tag) {
        return new FragmentPlan(tag.getInteger("x"), tag.getInteger("z"), tag.getInteger("y"), tag.getLong("seed"),
                tag.getInteger("dim"));
    }

    @Override
    public String describe() {
        return "catacomb fragment at " + centreX + "," + levelY + "," + centreZ;
    }
}
