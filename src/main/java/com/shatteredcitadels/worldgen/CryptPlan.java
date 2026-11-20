package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.catacomb.CatacombBuilder;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * Standalone crypt: a ruined shrine on the surface and a spiral down to a small catacomb level
 * whose hall holds a boss altar. {@code levels} is kept for old saves and no longer used.
 */
public final class CryptPlan implements StructurePlan {

    public static final String KIND = "crypt";
    /** Former crypt size; the plan's anchor is still minX + 11, minZ + 11. */
    public static final int SIZE = 21;
    private static final int HALF = CatacombBuilder.FRAGMENT_CELLS * CatacombBuilder.CELL / 2 + 1;

    public final int minX;
    public final int minZ;
    public final int surfaceY;
    public final int levels;
    public final CitadelArchetype archetype;
    public final long seed;
    public final int dimension;

    public CryptPlan(int minX, int minZ, int surfaceY, int levels, CitadelArchetype archetype, long seed, int dimension) {
        this.minX = minX;
        this.minZ = minZ;
        this.surfaceY = surfaceY;
        this.levels = levels;
        this.archetype = archetype;
        this.seed = seed;
        this.dimension = dimension;
    }

    private int topLevelY() {
        return surfaceY - 12;
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
        int cx = minX + 11;
        int cz = minZ + 11;
        return new StructureBoundingBox(cx - HALF - 1, Math.max(BlockWriter.MIN_Y, topLevelY() - 1), cz - HALF - 1,
                cx + HALF + 1, surfaceY + 4, cz + HALF + 1);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        int cx = minX + 11;
        int cz = minZ + 11;
        int topLevelY = topLevelY();
        BlockPalette palette = ModPaletteRegistry.forCitadel(archetype, random.nextLong());
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("crypt", random), StructureType.CRYPT, archetype,
                dimension, new BlockPos(minX, topLevelY, minZ), footprint());
        CatacombBuilder builder = new CatacombBuilder(writer, record, palette, random, 1);
        builder.buildSurfaceShrine(cx, surfaceY, cz);
        return builder.buildFragment(cx, cz, topLevelY, true, surfaceY) == 0 ? null : record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("minX", minX);
        tag.setInteger("minZ", minZ);
        tag.setInteger("surface", surfaceY);
        tag.setInteger("levels", levels);
        tag.setString("archetype", archetype.key());
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        return tag;
    }

    public static CryptPlan read(NBTTagCompound tag) {
        CitadelArchetype archetype = CitadelArchetype.byKey(tag.getString("archetype"));
        return new CryptPlan(tag.getInteger("minX"), tag.getInteger("minZ"), tag.getInteger("surface"),
                tag.getInteger("levels"), archetype == null ? CitadelArchetype.KEEPERS_CITADEL : archetype,
                tag.getLong("seed"), tag.getInteger("dim"));
    }

    @Override
    public String describe() {
        return "crypt at " + minX + "," + surfaceY + "," + minZ;
    }
}
