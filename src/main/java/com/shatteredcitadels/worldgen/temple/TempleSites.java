package com.shatteredcitadels.worldgen.temple;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.Random;

/**
 * The site rules of the scattered-feature generator we replace (vanilla or Biomes O' Plenty): which
 * temple a site biome gets, and the generator's own start for the sites we leave to it.
 */
public interface TempleSites {

    enum Kind { DESERT, JUNGLE, OTHER, NONE, SWAMP }

    /** Creates the generator's own start (witch hut, igloo, or a temple we do not replace). */
    interface StartFactory {
        StructureStart create(World world, Random rand, int chunkX, int chunkZ);
    }

    Kind kindOf(Biome biome);

    StructureStart otherStart(World world, Random rand, int chunkX, int chunkZ);
}
