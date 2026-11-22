package com.shatteredcitadels.worldgen.stronghold;

import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * Stronghold sites are reserved: other structures keep off them, so the eye of ender always
 * leads to a stronghold. The nearest site comes from the world's stronghold generator (the vanilla
 * site list; only the biome provider is read, no chunk is loaded).
 */
public final class StrongholdSites {

    /** Half-size of a stronghold's footprint around its site. */
    public static final int REACH = StrongholdLayout.RADIUS + 2;

    private StrongholdSites() {
    }

    /** @return true if a structure with this footprint would overlap the stronghold of {@code site}. */
    public static boolean conflicts(BlockPos site, StructureBoundingBox box) {
        return site != null && box.intersectsWith(site.getX() - REACH, site.getZ() - REACH, site.getX() + REACH,
                site.getZ() + REACH);
    }

    /** @return true if the footprint overlaps the nearest stronghold site of the world. */
    public static boolean blocks(World world, StructureBoundingBox box) {
        if (world.isRemote || world.provider.getDimension() != 0 || !ConfigCache.get().isReplaceStrongholds()) {
            return false;
        }
        BlockPos centre = new BlockPos((box.minX + box.maxX) / 2, 64, (box.minZ + box.maxZ) / 2);
        BlockPos site = world.findNearestStructure("Stronghold", centre, false);
        return conflicts(site, box);
    }
}
