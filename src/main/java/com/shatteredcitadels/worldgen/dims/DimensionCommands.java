package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.dims.fortress.FortressPlan;
import net.minecraft.command.CommandException;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Random;

/** The part of {@code /citadel spawn} that belongs to the Nether, the End and Atum. */
public final class DimensionCommands {

    public static final String FORTRESS = "nether_fortress";

    private DimensionCommands() {
    }

    /**
     * @return the queued plan, or null if the kind and dimension are the business of the usual command
     * @throws CommandException if the kind is known here but cannot be built at this place
     */
    public static StructurePlan planAndQueue(World world, String rawKind, String variant, int chunkX, int chunkZ)
            throws CommandException {
        String kind = rawKind.toLowerCase(Locale.ROOT);
        DimKind dim = DimKind.of(world);
        Random random = new Random(world.getSeed() ^ DimSites.scramble(((long) chunkX << 32) ^ chunkZ) ^ System.nanoTime());
        StructurePlan plan;
        if (FORTRESS.equals(kind)) {
            if (dim != DimKind.NETHER) {
                throw new CommandException("A nether fortress can only be built in the Nether");
            }
            plan = FortressPlan.plan(world.provider.getDimension(), chunkX, chunkZ, random.nextLong(),
                    DimsConfig.fortressBlazeSpawners());
        } else if (dim == null) {
            return null;
        } else if ("tower".equals(kind) || kind.equals(dim.archetype().key())) {
            plan = DimensionPlanner.tower(world, dim, chunkX, chunkZ, random, null, true);
        } else if ("castle".equals(kind)) {
            if (dim == DimKind.ATUM) {
                throw new CommandException("Castles are not built in Atum; use tower or desert_temple");
            }
            plan = DimensionPlanner.castle(world, dim, chunkX, chunkZ, random, true);
        } else if ("desert_temple".equals(kind) && dim == DimKind.ATUM) {
            plan = DimensionPlanner.temple(world, chunkX, chunkZ, random.nextLong(),
                    variant == null ? null : Boolean.valueOf("pharaoh".equalsIgnoreCase(variant)));
        } else if (CitadelArchetype.byKey(kind) != null) {
            // Any other look in the dimension's ground and with its garrison; non-stone parts are still remapped.
            plan = DimensionPlanner.tower(world, dim, chunkX, chunkZ, random, CitadelArchetype.byKey(kind), true);
        } else {
            throw new CommandException("'" + kind + "' is not built in this dimension. Use: tower, castle, "
                    + dim.archetype().key() + (dim == DimKind.ATUM ? ", desert_temple" : "")
                    + (dim == DimKind.NETHER ? ", " + FORTRESS : ""));
        }
        if (plan == null) {
            throw new CommandException("No room for that structure here (no ground, lava, or the roof is too low)");
        }
        if (!CitadelWorldGenerator.isAreaFree(world, plan.footprint())) {
            throw new CommandException("Another structure is too close; move further away");
        }
        StructureJobs.get(world).submit(plan);
        return plan;
    }
}
