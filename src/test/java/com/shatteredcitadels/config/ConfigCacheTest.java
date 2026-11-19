package com.shatteredcitadels.config;

import net.minecraft.util.ResourceLocation;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigCacheTest {

    private final String[] originalBlacklist = ModConfig.SPAWNER_AND_WAVES.mobBlacklist.clone();
    private final String[] originalOverrides = ModConfig.SPAWNER_AND_WAVES.manualMobTierOverrides.clone();
    private final String[] originalBossPool = ModConfig.BOSS_SETTINGS.bossPoolByTier.clone();
    private final int originalMinFloors = ModConfig.CASTLE_GENERATION.minFloors;
    private final int originalMaxFloors = ModConfig.CASTLE_GENERATION.maxFloors;

    @After
    public void restore() {
        ModConfig.SPAWNER_AND_WAVES.mobBlacklist = originalBlacklist;
        ModConfig.SPAWNER_AND_WAVES.manualMobTierOverrides = originalOverrides;
        ModConfig.BOSS_SETTINGS.bossPoolByTier = originalBossPool;
        ModConfig.CASTLE_GENERATION.minFloors = originalMinFloors;
        ModConfig.CASTLE_GENERATION.maxFloors = originalMaxFloors;
        ConfigCache.rebuild();
    }

    @Test
    public void defaultsAreParsed() {
        ConfigCache.rebuild();
        ConfigCache.Snapshot s = ConfigCache.get();
        assertEquals(36, s.getCastleSectionSize());
        assertEquals(0.06D, s.getSkyCitadelChance(), 1e-9);
        assertEquals(12, s.getMaxActiveMobsPerFloor());
        assertEquals(0.025D, s.getLowFloorRareLootChance(), 1e-9);
        assertTrue(s.isBlacklisted(new ResourceLocation("minecraft:creeper")));
        assertFalse(s.isBlacklisted(new ResourceLocation("minecraft:zombie")));
        assertEquals(Integer.valueOf(3), s.getManualMobTierOverrides().get(new ResourceLocation("minecraft:wither_skeleton")));
        assertEquals(5, s.getConfiguredBossPool().size());
    }

    @Test
    public void invalidEntriesAreIgnoredAndWildcardsWork() {
        ModConfig.SPAWNER_AND_WAVES.mobBlacklist = new String[]{"examplemod:*", "not an id", "", "Minecraft:Ghast"};
        ModConfig.SPAWNER_AND_WAVES.manualMobTierOverrides = new String[]{"minecraft:zombie=9", "minecraft:husk=2", "broken"};
        ModConfig.BOSS_SETTINGS.bossPoolByTier = new String[]{"2=minecraft:husk, bad id ,minecraft:stray", "2=minecraft:husk", "x=minecraft:zombie"};
        ConfigCache.rebuild();
        ConfigCache.Snapshot s = ConfigCache.get();
        assertTrue(s.isBlacklisted(new ResourceLocation("examplemod:anything")));
        assertTrue(s.isBlacklisted(new ResourceLocation("minecraft:ghast")));
        assertEquals(1, s.getManualMobTierOverrides().size());
        List<ResourceLocation> tier2 = s.getConfiguredBossPool().get(2);
        assertEquals(2, tier2.size());
        assertEquals(1, s.getConfiguredBossPool().size());
    }

    @Test
    public void invertedFloorRangeIsSwapped() {
        ModConfig.CASTLE_GENERATION.minFloors = 6;
        ModConfig.CASTLE_GENERATION.maxFloors = 4;
        ConfigCache.rebuild();
        ConfigCache.Snapshot s = ConfigCache.get();
        assertEquals(4, s.getMinFloors());
        assertEquals(6, s.getMaxFloors());
        Random random = new Random(42L);
        for (int i = 0; i < 200; i++) {
            int floors = s.rollFloorCount(random, false);
            assertTrue(floors >= 4 && floors <= 6);
            int sky = s.rollFloorCount(random, true);
            assertTrue(sky >= 10 && sky <= 14);
        }
    }

    @Test
    public void bossScalingCompoundsAndIsBounded() {
        ConfigCache.rebuild();
        ConfigCache.Snapshot s = ConfigCache.get();
        assertEquals(1.0D, s.bossHealthScale(1, false), 1e-9);
        assertEquals(1.15D * 1.15D, s.bossHealthScale(3, false), 1e-9);
        assertEquals(3.0D, s.bossDamageScale(1, true), 1e-9);
        assertTrue(s.bossHealthScale(10_000, true) <= 10_000.0D);
    }
}
