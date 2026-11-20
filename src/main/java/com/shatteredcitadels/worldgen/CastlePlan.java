package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A walled castle: curtain wall with towers, gatehouse and moat around a courtyard (floor 0,
 * sealed keep until its waves are beaten) with the keep (floors 1..n, see {@link #keep}) in the
 * middle. The gate always faces south, towards the keep's door.
 */
public final class CastlePlan implements StructurePlan {

    public static final String KIND = "castle";
    /** Berm, moat lining and water outside the wall. */
    public static final int MOAT_REACH = 8;

    public final CitadelPlan keep;
    /** Half extents of the curtain wall's outer face around the keep centre. */
    public final int halfX;
    public final int halfZ;
    public final int wallHeight;
    public final long seed;

    public CastlePlan(CitadelPlan keep, int halfX, int halfZ, int wallHeight, long seed) {
        this.keep = keep;
        this.halfX = halfX;
        this.halfZ = halfZ;
        this.wallHeight = wallHeight;
        this.seed = seed;
    }

    public int centerX() { return keep.centerX(); }
    public int centerZ() { return keep.centerZ(); }
    public int baseY() { return keep.baseY; }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public int dimension() {
        return keep.dimension;
    }

    @Override
    public StructureBoundingBox footprint() {
        StructureBoundingBox keepBox = keep.footprint();
        int reach = MOAT_REACH + 1;
        return new StructureBoundingBox(Math.min(keepBox.minX, centerX() - halfX - reach), keepBox.minY,
                Math.min(keepBox.minZ, centerZ() - halfZ - reach), Math.max(keepBox.maxX, centerX() + halfX + reach),
                keepBox.maxY, Math.max(keepBox.maxZ, centerZ() + halfZ + reach));
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate(keep.archetype.key(), random), StructureType.WALLED_CASTLE, keep.archetype,
                keep.dimension, new BlockPos(centerX() - halfX, baseY(), centerZ() - halfZ), keep.totalBounds());
        new CastleBuilder(writer, this, random, record).build();
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setTag("keep", keep.writeToNBT());
        tag.setInteger("halfX", halfX);
        tag.setInteger("halfZ", halfZ);
        tag.setInteger("wall", wallHeight);
        tag.setLong("seed", seed);
        return tag;
    }

    public static CastlePlan read(NBTTagCompound tag) {
        return new CastlePlan(CitadelPlan.read(tag.getCompoundTag("keep")), tag.getInteger("halfX"),
                tag.getInteger("halfZ"), tag.getInteger("wall"), tag.getLong("seed"));
    }

    @Override
    public String describe() {
        return "walled castle " + (halfX * 2 + 1) + "x" + (halfZ * 2 + 1) + " around a " + keep.describe();
    }
}
