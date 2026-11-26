package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.StructurePlans;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * A tower, castle or temple of the overworld builders placed in another dimension: the inner plan
 * builds as usual, then its overworld blocks are turned into the blocks of the dimension.
 */
public final class DimensionPlan implements StructurePlan {

    public static final String KIND = "dims";

    private final DimKind dim;
    private final StructurePlan inner;

    public DimensionPlan(DimKind dim, StructurePlan inner) {
        this.dim = dim;
        this.inner = inner;
    }

    public DimKind dim() {
        return dim;
    }

    public StructurePlan inner() {
        return inner;
    }

    /** The kind of the wrapped plan: distance rules between structures see a tower as a tower. */
    @Override
    public String kind() {
        return inner.kind();
    }

    @Override
    public int dimension() {
        return inner.dimension();
    }

    @Override
    public StructureBoundingBox footprint() {
        return inner.footprint();
    }

    @Override
    public StructureBoundingBox priorityArea() {
        return inner.priorityArea();
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        CitadelInstance record = inner.build(writer);
        DimPalette.apply(writer.getBlueprint(), dim);
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setString("dim", dim.key());
        tag.setTag("inner", inner.writeToNBT());
        return tag;
    }

    /** @return the plan; the bare inner plan if the dimension kind is unknown; null if the inner plan is */
    public static StructurePlan read(NBTTagCompound tag) {
        StructurePlan inner = StructurePlans.read(tag.getCompoundTag("inner"));
        DimKind dim = DimKind.byKey(tag.getString("dim"));
        return inner == null || dim == null ? inner : new DimensionPlan(dim, inner);
    }

    @Override
    public String describe() {
        return inner.describe() + " (" + dim.key() + ")";
    }
}
