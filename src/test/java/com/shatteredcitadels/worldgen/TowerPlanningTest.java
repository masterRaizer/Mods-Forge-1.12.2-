package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.ModConfig;
import org.junit.After;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TowerPlanningTest {

    private final int originalTowerMin = ModConfig.CASTLE_GENERATION.towerMinFloors;
    private final int originalTowerMax = ModConfig.CASTLE_GENERATION.towerMaxFloors;

    @After
    public void restore() {
        ModConfig.CASTLE_GENERATION.towerMinFloors = originalTowerMin;
        ModConfig.CASTLE_GENERATION.towerMaxFloors = originalTowerMax;
        ConfigCache.rebuild();
    }

    @Test
    public void towersReachTheBuildLimitWithoutCrossingIt() {
        int arena = 14;
        for (int baseY = 60; baseY <= 100; baseY += 5) {
            int floors = CitadelWorldGenerator.fitFloors(baseY, 30, 3, arena);
            int roof = baseY + (floors - 1) * CitadelPlan.FLOOR_HEIGHT + arena;
            assertTrue("roof " + roof + " at base " + baseY, roof + 4 <= BlockWriter.MAX_Y);
            assertTrue("one more floor would not fit", roof + CitadelPlan.FLOOR_HEIGHT + 4 > BlockWriter.MAX_Y);
        }
        // From sea level a tower gets 22 floors with its roof at Y 246.
        assertEquals(22, CitadelWorldGenerator.fitFloors(64, 30, 3, arena));
    }

    @Test
    public void shortCitadelsAreUntouched() {
        assertEquals(7, CitadelWorldGenerator.fitFloors(70, 7, 3, 12));
    }

    @Test
    public void towerRangeIsConfigurableAndScalingIsCapped() {
        ModConfig.CASTLE_GENERATION.towerMinFloors = 16;
        ModConfig.CASTLE_GENERATION.towerMaxFloors = 18;
        ConfigCache.rebuild();
        ConfigCache.Snapshot s = ConfigCache.get();
        Random random = new Random(3L);
        for (int i = 0; i < 100; i++) {
            int floors = s.rollTowerFloorCount(random);
            assertTrue(floors >= 16 && floors <= 18);
        }
        assertEquals(s.bossHealthScale(ConfigCache.MAX_SCALING_FLOOR, false), s.bossHealthScale(22, false), 1e-9);
        assertEquals(s.waveMobHealthScale(ConfigCache.MAX_SCALING_FLOOR), s.waveMobHealthScale(21), 1e-9);
        assertTrue(s.bossHealthScale(ConfigCache.MAX_SCALING_FLOOR, false) > s.bossHealthScale(7, false));
    }
}
