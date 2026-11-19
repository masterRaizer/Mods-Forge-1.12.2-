package com.shatteredcitadels.config;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bosses of popular 1.12.2 mob mods, merged into {@code bossPoolByTier} when
 * {@code boss_settings.useBuiltInModBosses} is on. Lives in code rather than in the config
 * defaults because Forge keeps an existing config file's array untouched, so new defaults would
 * never reach an already-generated config. IDs of mods that are not installed are dropped when
 * the pool is resolved against the entity registry.
 */
public final class BuiltInModBosses {

    private static final String VANILLA = "minecraft";

    private static final String[][] TABLE = {
            /* tier 1 */ {},
            /* tier 2 */ {"thermalfoundation:blizz", "thermalfoundation:blitz", "thermalfoundation:basalz"},
            /* tier 3 */ {"atum:stonewarden", "mowziesmobs:ferrous_wroughtnaut"},
            /* tier 4 */ {"atum:warlord", "mowziesmobs:ferrous_wroughtnaut", "mowziesmobs:barako"},
            /* tier 5 */ {"mowziesmobs:frostmaw", "atum:pharaoh", "atum:warlord"}
    };

    private static final Map<Integer, List<ResourceLocation>> BY_TIER;

    static {
        Map<Integer, List<ResourceLocation>> map = new TreeMap<>();
        for (int i = 0; i < TABLE.length; i++) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (String id : TABLE[i]) {
                ids.add(new ResourceLocation(id));
            }
            if (!ids.isEmpty()) {
                map.put(MobTiers.MIN_TIER + i, Collections.unmodifiableList(ids));
            }
        }
        BY_TIER = Collections.unmodifiableMap(map);
    }

    private BuiltInModBosses() {
    }

    /** @return tier -> built-in modded boss IDs (not checked against the registry). */
    public static Map<Integer, List<ResourceLocation>> byTier() {
        return BY_TIER;
    }

    /** @return configured pool with the built-in entries appended per tier, duplicates removed. */
    static Map<Integer, List<ResourceLocation>> merge(Map<Integer, List<ResourceLocation>> configured,
                                                      Map<Integer, List<ResourceLocation>> extra) {
        Map<Integer, Set<ResourceLocation>> merged = new TreeMap<>();
        for (Map<Integer, List<ResourceLocation>> source : Arrays.asList(configured, extra)) {
            for (Map.Entry<Integer, List<ResourceLocation>> entry : source.entrySet()) {
                Set<ResourceLocation> bucket = merged.get(entry.getKey());
                if (bucket == null) {
                    bucket = new LinkedHashSet<>();
                    merged.put(entry.getKey(), bucket);
                }
                bucket.addAll(entry.getValue());
            }
        }
        Map<Integer, List<ResourceLocation>> result = new TreeMap<>();
        for (Map.Entry<Integer, Set<ResourceLocation>> entry : merged.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static boolean isVanilla(ResourceLocation id) {
        return VANILLA.equals(id.getNamespace());
    }

    /**
     * @return a shuffled copy of the candidates; with {@code preferModded} every modded boss comes
     * before every vanilla one, so vanilla mobs are only tried when no modded boss fits the arena.
     */
    public static List<ResourceLocation> orderForSpawn(List<ResourceLocation> candidates, boolean preferModded, Random random) {
        if (!preferModded) {
            List<ResourceLocation> all = new ArrayList<>(candidates);
            Collections.shuffle(all, random);
            return all;
        }
        List<ResourceLocation> modded = new ArrayList<>();
        List<ResourceLocation> vanilla = new ArrayList<>();
        for (ResourceLocation id : candidates) {
            (isVanilla(id) ? vanilla : modded).add(id);
        }
        Collections.shuffle(modded, random);
        Collections.shuffle(vanilla, random);
        modded.addAll(vanilla);
        return modded;
    }
}
