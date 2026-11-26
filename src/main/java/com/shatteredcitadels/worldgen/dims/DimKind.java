package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.palette.CitadelArchetype;
import net.minecraft.world.World;

import java.util.Locale;

/** The dimensions besides the overworld that get the mod's structures, each in its own stone. */
public enum DimKind {
    NETHER,
    END,
    ATUM;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DimKind byKey(String key) {
        for (DimKind kind : values()) {
            if (kind.key().equals(key)) {
                return kind;
            }
        }
        return null;
    }

    /** The tower stone of the dimension. */
    public CitadelArchetype archetype() {
        switch (this) {
            case NETHER:
                return CitadelArchetype.NETHER_CITADEL;
            case END:
                return CitadelArchetype.END_CITADEL;
            default:
                return CitadelArchetype.ATUM_CITADEL;
        }
    }

    /**
     * Pure selection: the Nether and the End by id, Atum by the name of its dimension type or the
     * package of its world provider (no compile dependency on the mod).
     *
     * @return the kind, or null for the overworld and every other dimension
     */
    public static DimKind of(int dimensionId, String typeName, String providerClass) {
        if (dimensionId == -1) {
            return NETHER;
        }
        if (dimensionId == 1) {
            return END;
        }
        if (dimensionId == 0) {
            return null;
        }
        String type = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        String provider = providerClass == null ? "" : providerClass.toLowerCase(Locale.ROOT);
        if (type.equals("atum") || provider.contains(".atum.")) {
            return ATUM;
        }
        return null;
    }

    public static DimKind of(World world) {
        if (world == null || world.provider == null) {
            return null;
        }
        String typeName;
        try {
            typeName = world.provider.getDimensionType().getName();
        } catch (RuntimeException e) {
            typeName = null;
        }
        return of(world.provider.getDimension(), typeName, world.provider.getClass().getName());
    }
}
