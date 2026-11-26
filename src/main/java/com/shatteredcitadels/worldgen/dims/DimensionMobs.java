package com.shatteredcitadels.worldgen.dims;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The garrison of the other dimensions: wave spawners there draw from the dimension's own list. */
public final class DimensionMobs {

    private DimensionMobs() {
    }

    /**
     * @return the mobs one floor fights in this world's dimension, or null where the usual tier
     * registry decides (overworld, other dimensions, or none of the configured mobs exists)
     */
    public static List<ResourceLocation> combo(World world, Random random, int count) {
        DimKind kind = DimsConfig.active(world);
        if (kind == null) {
            return null;
        }
        List<ResourceLocation> known = new ArrayList<>();
        for (ResourceLocation id : parse(DimsConfig.mobs(kind))) {
            if (ForgeRegistries.ENTITIES.containsKey(id)) {
                known.add(id);
            }
        }
        return known.isEmpty() ? null : pick(known, random, count);
    }

    /** @return valid ids of the configured names in order, without blanks and duplicates */
    public static List<ResourceLocation> parse(String[] names) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (names == null) {
            return ids;
        }
        for (String name : names) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ResourceLocation id = new ResourceLocation(trimmed);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** @return up to {@code count} different mobs of the list, chosen by the random */
    public static List<ResourceLocation> pick(List<ResourceLocation> known, Random random, int count) {
        List<ResourceLocation> shuffled = new ArrayList<>(known);
        Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, Math.max(1, Math.min(count, shuffled.size()))));
    }
}
