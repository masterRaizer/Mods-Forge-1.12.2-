package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.nbt.NBTTagCompound;

/** Reads saved {@link StructurePlan}s back by their kind. */
public final class StructurePlans {

    private StructurePlans() {
    }

    /** @return the plan, or null for an unknown kind (a newer save opened by an older mod). */
    public static StructurePlan read(NBTTagCompound tag) {
        String kind = tag.getString(StructurePlan.KIND_KEY);
        switch (kind) {
            case CitadelPlan.KIND:
                return CitadelPlan.read(tag);
            case CryptPlan.KIND:
                return CryptPlan.read(tag);
            case CastlePlan.KIND:
                return CastlePlan.read(tag);
            case ManorPlan.KIND:
                return ManorPlan.read(tag);
            default:
                ShatteredCitadelsMod.LOGGER.warn("Dropping saved structure job of unknown kind '{}'", kind);
                return null;
        }
    }
}
