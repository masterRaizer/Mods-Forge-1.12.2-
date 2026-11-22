package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.structure.CitadelInstance;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * Every decision about a structure that depends on the world (site, height, dimension), made on
 * the server thread before building. The plan is saved with its job, so a half-written
 * structure is rebuilt identically after a restart; {@link #build} itself must be pure.
 */
public interface StructurePlan {

    String KIND_KEY = "kind";

    /** @return an area whose chunks are written before the rest (e.g. a stronghold's portal room), or null. */
    default net.minecraft.world.gen.structure.StructureBoundingBox priorityArea() {
        return null;
    }

    /** @return the kind stored in NBT, used by {@link StructurePlans#read}. */
    String kind();

    int dimension();

    /** @return the largest area the structure may cover; reserved against other structures. */
    StructureBoundingBox footprint();

    /**
     * Builds the structure into the writer's blueprint and returns its record, or null if nothing
     * could be built. Runs on a worker thread: no world access, all randomness from the plan seed.
     */
    CitadelInstance build(BlockWriter writer);

    NBTTagCompound writeToNBT();

    /** @return a short human-readable description for logs and commands. */
    String describe();
}
