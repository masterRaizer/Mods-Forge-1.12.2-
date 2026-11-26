package com.shatteredcitadels.compat;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

/**
 * Places of other mods where nothing of ours may be built: such a mod lays out a whole town long before
 * its chunks exist, and a castle planned by the biome of one column would land on top of it.
 * Looked up by name once, so there is no dependency: without the mod there are no zones.
 */
public final class ForeignZones {
    private static final String PRIPYAT = "modernbackports.stages.s14pripyat.PripyatZone";
    private static final BiPredicate<World, StructureBoundingBox> NONE = (world, box) -> false;
    private static volatile BiPredicate<World, StructureBoundingBox> pripyat;

    private ForeignZones() {
    }

    /** @return true if the footprint (walls and moat included) touches a zone of another mod. */
    public static boolean blocks(World world, StructureBoundingBox footprint) {
        BiPredicate<World, StructureBoundingBox> zone = pripyat;
        if (zone == null) {
            zone = lookup(PRIPYAT);
            pripyat = zone;
        }
        return zone.test(world, footprint);
    }

    /** {@code static boolean touches(World, int x1, int z1, int x2, int z2)} of the class, or no zones at all. */
    static BiPredicate<World, StructureBoundingBox> lookup(String className) {
        final Method touches;
        try {
            touches = Class.forName(className).getMethod("touches", World.class, int.class, int.class, int.class,
                    int.class);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            return NONE;
        }
        return new BiPredicate<World, StructureBoundingBox>() {
            private boolean failed;

            @Override
            public boolean test(World world, StructureBoundingBox box) {
                if (failed) {
                    return false;
                }
                try {
                    return Boolean.TRUE.equals(touches.invoke(null, world, box.minX, box.minZ, box.maxX, box.maxZ));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    failed = true;
                    ShatteredCitadelsMod.LOGGER.warn("Zone check {} failed; it is switched off", className, e);
                    return false;
                }
            }
        };
    }
}
