package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/** A gothic manor; the layout itself is fixed in {@link ManorBuilder}, the plan fixes where. */
public final class ManorPlan implements StructurePlan {

    public static final String KIND = "manor";

    /** North-west corner of the plan (local 0, 0). */
    public final int originX;
    public final int originZ;
    public final int baseY;
    public final long seed;
    public final int dimension;

    public ManorPlan(int originX, int originZ, int baseY, long seed, int dimension) {
        this.originX = originX;
        this.originZ = originZ;
        this.baseY = baseY;
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
        return new StructureBoundingBox(originX - 3, Math.max(BlockWriter.MIN_Y, baseY - 24), originZ - ManorBuilder.TOWER_REACH - 2,
                originX + ManorBuilder.WIDTH + 2, Math.min(BlockWriter.MAX_Y, baseY + 60),
                originZ + ManorBuilder.DEPTH + ManorBuilder.TOWER_REACH + 1);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate(CitadelArchetype.GOTHIC_MANOR.key(), random), StructureType.MANOR,
                CitadelArchetype.GOTHIC_MANOR, dimension, new BlockPos(originX, baseY, originZ), footprint());
        new ManorBuilder(writer, this, random, record).build();
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("x", originX);
        tag.setInteger("z", originZ);
        tag.setInteger("y", baseY);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        return tag;
    }

    public static ManorPlan read(NBTTagCompound tag) {
        return new ManorPlan(tag.getInteger("x"), tag.getInteger("z"), tag.getInteger("y"), tag.getLong("seed"),
                tag.getInteger("dim"));
    }

    @Override
    public String describe() {
        return "gothic manor " + ManorBuilder.WIDTH + "x" + (ManorBuilder.DEPTH + 2 * ManorBuilder.TOWER_REACH)
                + " at " + originX + "," + baseY + "," + originZ;
    }
}
