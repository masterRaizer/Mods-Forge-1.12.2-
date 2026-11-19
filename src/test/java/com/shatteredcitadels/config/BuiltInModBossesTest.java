package com.shatteredcitadels.config;

import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuiltInModBossesTest {

    private static ResourceLocation id(String s) {
        return new ResourceLocation(s);
    }

    @Test
    public void topTiersHaveOnlyModdedBuiltIns() {
        Map<Integer, List<ResourceLocation>> table = BuiltInModBosses.byTier();
        for (int tier = 3; tier <= MobTiers.MAX_TIER; tier++) {
            assertFalse("tier " + tier + " has built-in bosses", table.get(tier).isEmpty());
        }
        for (List<ResourceLocation> ids : table.values()) {
            for (ResourceLocation boss : ids) {
                assertFalse(boss + " is vanilla", BuiltInModBosses.isVanilla(boss));
            }
        }
        assertTrue(table.get(5).contains(id("mowziesmobs:frostmaw")));
        assertTrue(table.get(5).contains(id("atum:pharaoh")));
    }

    @Test
    public void mergeAppendsAndDeduplicates() {
        Map<Integer, List<ResourceLocation>> configured = new TreeMap<>();
        configured.put(5, Arrays.asList(id("minecraft:wither_skeleton"), id("atum:pharaoh")));
        configured.put(1, Collections.singletonList(id("minecraft:zombie")));
        Map<Integer, List<ResourceLocation>> extra = new TreeMap<>();
        extra.put(5, Arrays.asList(id("atum:pharaoh"), id("mowziesmobs:frostmaw")));
        extra.put(4, Collections.singletonList(id("atum:warlord")));

        Map<Integer, List<ResourceLocation>> merged = BuiltInModBosses.merge(configured, extra);
        assertEquals(Arrays.asList(id("minecraft:wither_skeleton"), id("atum:pharaoh"), id("mowziesmobs:frostmaw")), merged.get(5));
        assertEquals(Collections.singletonList(id("atum:warlord")), merged.get(4));
        assertEquals(3, merged.size());
    }

    @Test
    public void preferModdedPutsVanillaLast() {
        List<ResourceLocation> candidates = Arrays.asList(id("minecraft:wither_skeleton"), id("atum:pharaoh"),
                id("minecraft:evocation_illager"), id("mowziesmobs:frostmaw"));
        Random random = new Random(7L);
        for (int i = 0; i < 50; i++) {
            List<ResourceLocation> ordered = BuiltInModBosses.orderForSpawn(candidates, true, random);
            assertEquals(4, ordered.size());
            assertFalse(BuiltInModBosses.isVanilla(ordered.get(0)));
            assertFalse(BuiltInModBosses.isVanilla(ordered.get(1)));
            assertTrue(BuiltInModBosses.isVanilla(ordered.get(2)));
            assertTrue(BuiltInModBosses.isVanilla(ordered.get(3)));
        }
        List<ResourceLocation> any = BuiltInModBosses.orderForSpawn(candidates, false, random);
        assertTrue(any.containsAll(candidates));
    }

    @Test
    public void defaultsEnableModBosses() {
        ConfigCache.rebuild();
        ConfigCache.Snapshot s = ConfigCache.get();
        assertTrue(s.isUseBuiltInModBosses());
        assertTrue(s.isPreferModdedBosses());
    }
}
