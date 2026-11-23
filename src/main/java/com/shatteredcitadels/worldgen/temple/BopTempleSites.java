package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.StructureStart;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

/**
 * Site rules of Biomes O' Plenty's {@code BOPMapGenScatteredFeature}, read by reflection (no compile
 * dependency): its four biome lists and its start class for the sites we leave to it.
 */
public final class BopTempleSites implements TempleSites {

    public static final String GENERATOR_CLASS = "biomesoplenty.common.world.BOPMapGenScatteredFeature";

    private final List<Biome> desert;
    private final List<Biome> jungle;
    private final List<Biome> swamp;
    private final List<Biome> ice;
    private final StartFactory starts;

    public BopTempleSites(List<Biome> desert, List<Biome> jungle, List<Biome> swamp, List<Biome> ice, StartFactory starts) {
        this.desert = desert;
        this.jungle = jungle;
        this.swamp = swamp;
        this.ice = ice;
        this.starts = starts;
    }

    /** @return the rules of BOP's generator, or null if {@code gen} is not one (or BOP changed). */
    public static BopTempleSites from(MapGenScatteredFeature gen) {
        Class<?> type = gen.getClass();
        if (!GENERATOR_CLASS.equals(type.getName())) {
            return null;
        }
        try {
            Constructor<?> start = Class.forName(GENERATOR_CLASS + "$Start")
                    .getConstructor(World.class, Random.class, int.class, int.class);
            return new BopTempleSites(list(type, "DESERT_BIOMES"), list(type, "JUNGLE_BIOMES"), list(type, "SWAMP_BIOMES"),
                    list(type, "ICE_BIOMES"), (world, rand, cx, cz) -> {
                        try {
                            return (StructureStart) start.newInstance(world, rand, cx, cz);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException("Cannot create a Biomes O' Plenty temple start", e);
                        }
                    });
        } catch (ReflectiveOperationException | ClassCastException e) {
            ShatteredCitadelsMod.LOGGER.warn("Biomes O' Plenty temple generator not recognised; its temples stay", e);
            return null;
        }
    }

    /** @return BOP's spacing of scattered features (its {@code distance} option), or 32. */
    public static int spacingOf(MapGenScatteredFeature gen) {
        try {
            Field field = gen.getClass().getDeclaredField("maxDistanceBetweenScatteredFeatures");
            field.setAccessible(true);
            return field.getInt(gen);
        } catch (ReflectiveOperationException e) {
            return 32;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Biome> list(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        // A live view: BOP fills its lists as its biomes register.
        return (List<Biome>) field.get(null);
    }

    @Override
    public Kind kindOf(Biome biome) {
        if (biome == null) {
            return Kind.NONE;
        }
        if (desert.contains(biome)) {
            return Kind.DESERT;
        }
        if (jungle.contains(biome)) {
            return Kind.JUNGLE;
        }
        if (swamp.contains(biome) || ice.contains(biome)) {
            return Kind.OTHER;
        }
        return Kind.NONE;
    }

    @Override
    public StructureStart otherStart(World world, Random rand, int chunkX, int chunkZ) {
        return starts.create(world, rand, chunkX, chunkZ);
    }

}
