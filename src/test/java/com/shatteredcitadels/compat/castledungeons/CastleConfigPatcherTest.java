package com.shatteredcitadels.compat.castledungeons;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.ModConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CastleConfigPatcherTest {

    private int originalSection;

    @Before
    public void setUp() {
        originalSection = ModConfig.CASTLE_GENERATION.castleSectionSize;
        ModConfig.CASTLE_GENERATION.castleSectionSize = 36;
        ConfigCache.rebuild();
    }

    @After
    public void tearDown() {
        ModConfig.CASTLE_GENERATION.castleSectionSize = originalSection;
        ConfigCache.rebuild();
    }

    @Test
    public void sectionSizeIsRewritten() {
        assertEquals("section-size = 36", CastleConfigPatcher.patchSectionSize("section-size = 24"));
        assertEquals("  section-size=36 ", CastleConfigPatcher.patchSectionSize("  section-size=24 "));
        assertEquals("max-y = 128", CastleConfigPatcher.patchSectionSize("max-y = 128"));
        assertEquals("section-size = 36", CastleConfigPatcher.patchSectionSize("section-size = 36"));
    }

    @Test
    public void blacklistedMobsAreRemovedFromSpawnerLines() {
        assertEquals("spawner-medium = minecraft:cave_spider",
                CastleConfigPatcher.patchSpawnerLine("spawner-medium = minecraft:creeper minecraft:cave_spider"));
        assertEquals("spawner-easy = minecraft:zombie minecraft:skeleton",
                CastleConfigPatcher.patchSpawnerLine("spawner-easy = minecraft:zombie minecraft:skeleton"));
        assertEquals("wall = minecraft:stonebrick",
                CastleConfigPatcher.patchSpawnerLine("wall = minecraft:stonebrick"));
    }
}
