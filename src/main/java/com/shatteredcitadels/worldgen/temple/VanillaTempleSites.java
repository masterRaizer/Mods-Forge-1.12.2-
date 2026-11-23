package com.shatteredcitadels.worldgen.temple;

import net.minecraft.init.Biomes;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.Random;

/** Vanilla's site biomes (as {@code MapGenScatteredFeature.BIOMELIST}) and its start. */
public final class VanillaTempleSites implements TempleSites {

    public static final VanillaTempleSites INSTANCE = new VanillaTempleSites();

    private VanillaTempleSites() {
    }

    @Override
    public Kind kindOf(Biome biome) {
        if (biome == Biomes.DESERT || biome == Biomes.DESERT_HILLS) {
            return Kind.DESERT;
        }
        if (biome == Biomes.JUNGLE || biome == Biomes.JUNGLE_HILLS) {
            return Kind.JUNGLE;
        }
        if (biome == Biomes.SWAMPLAND || biome == Biomes.ICE_PLAINS || biome == Biomes.COLD_TAIGA) {
            return Kind.OTHER;
        }
        return Kind.NONE;
    }

    @Override
    public StructureStart otherStart(World world, Random rand, int chunkX, int chunkZ) {
        return new MapGenScatteredFeature.Start(world, rand, chunkX, chunkZ);
    }
}
