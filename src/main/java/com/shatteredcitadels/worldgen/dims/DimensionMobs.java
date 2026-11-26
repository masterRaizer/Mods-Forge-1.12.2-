package com.shatteredcitadels.worldgen.dims;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

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
        List<ResourceLocation> known = known(DimsConfig.mobs(kind), ForgeRegistries.ENTITIES::containsKey);
        return known.isEmpty() ? null : pick(known, random, count);
    }

    /** @return the configured mobs that really exist; the others are skipped silently */
    public static List<ResourceLocation> known(String[] names, Predicate<ResourceLocation> exists) {
        List<ResourceLocation> known = new ArrayList<>();
        for (ResourceLocation id : parse(names)) {
            if (exists.test(id)) {
                known.add(id);
            }
        }
        return known;
    }

    /**
     * @return the configured mobs that do not exist although their mod (or vanilla) is there: a typo
     * or a renamed mob. Mobs of a mod that is not installed are not listed
     */
    public static List<ResourceLocation> unknown(String[] names, Predicate<ResourceLocation> exists, Predicate<String> modLoaded) {
        List<ResourceLocation> unknown = new ArrayList<>();
        for (ResourceLocation id : parse(names)) {
            String domain = id.getNamespace();
            if (!exists.test(id) && ("minecraft".equals(domain) || modLoaded.test(domain))) {
                unknown.add(id);
            }
        }
        return unknown;
    }

    /** @return the single log line about unknown mobs of one config list, or null if there are none */
    public static String warning(String option, List<ResourceLocation> unknown) {
        return unknown.isEmpty() ? null
                : "Config dimensions." + option + ": unknown entity ids are skipped: " + unknown;
    }

    /** Once at startup, after every mod registered its entities: one line per list with a wrong name. */
    public static void warnAboutUnknown(Logger logger) {
        for (DimKind kind : DimKind.values()) {
            String line = warning(kind.key() + "Mobs",
                    unknown(DimsConfig.mobs(kind), ForgeRegistries.ENTITIES::containsKey, Loader::isModLoaded));
            if (line != null) {
                logger.warn(line);
            }
        }
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
