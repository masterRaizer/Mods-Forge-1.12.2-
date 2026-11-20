package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/** Standalone crypt with a ruined shrine on the surface. */
public final class CryptPlan implements StructurePlan {

    public static final String KIND = "crypt";
    public static final int SIZE = 21;

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
        return new StructureBoundingBox(minX, BlockWriter.MIN_Y, minZ, minX + SIZE - 1, surfaceY + 4, minZ + SIZE - 1);
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
                dimension, new BlockPos(minX, topLevelY, minZ),
                new StructureBoundingBox(minX, topLevelY, minZ, minX + SIZE - 1, surfaceY + 4, minZ + SIZE - 1));
        DungeonCryptBuilder builder = new DungeonCryptBuilder(writer, palette, random, record, minX, minZ, SIZE, 1);
        builder.buildSurfaceRuin(cx, surfaceY, cz);
        return builder.build(topLevelY, levels, cx, cz, surfaceY, true) == 0 ? null : record;
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
        return "crypt, " + levels + " levels at " + minX + "," + surfaceY + "," + minZ;
    }
}
