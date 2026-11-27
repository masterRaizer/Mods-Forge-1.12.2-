package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.config.ModConfig;
import com.shatteredcitadels.structure.StructureType;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Rarely the boss of a gothic manor is Herobrine himself instead of the usual guardian. */
public class ManorBossTest {

    @Test
    public void herobrineKeepsHisOwnFightOnAManorAltar() {
        // His own phases and spells run in his entity; the altar's guardian kit on top made him twice as strong.
        assertFalse(com.shatteredcitadels.boss.BossAbilityController.usesAltarAbilities(EntityHerobrine.class));
        assertTrue(com.shatteredcitadels.boss.BossAbilityController.usesAltarAbilities(net.minecraft.entity.monster.EntityZombie.class));
        assertTrue(com.shatteredcitadels.boss.BossAbilityController.usesAltarAbilities(net.minecraft.entity.monster.EntityWitherSkeleton.class));
    }

    @Test
    public void defaultChanceIsSmall() {
        assertEquals(0.03D, new ModConfig.Herobrine().manorBossChance, 1.0e-9D);
    }

    @Test
    public void onlyManorsAndOnlyWhenHerobrineIsOn() {
        Random random = new Random(1L);
        for (StructureType type : StructureType.values()) {
            assertEquals(type.name(), type == StructureType.MANOR, HerobrineSettings.takesThrone(type, true, 1.0D, random));
        }
        assertFalse(HerobrineSettings.takesThrone(StructureType.MANOR, false, 1.0D, random));
        assertFalse(HerobrineSettings.takesThrone(null, true, 1.0D, random));
    }

    @Test
    public void zeroNeverRollsAndBrokenValuesAreClamped() {
        Random random = new Random(2L);
        for (int i = 0; i < 1000; i++) {
            assertFalse(HerobrineSettings.takesThrone(StructureType.MANOR, true, 0.0D, random));
            assertFalse(HerobrineSettings.takesThrone(StructureType.MANOR, true, -5.0D, random));
            assertFalse(HerobrineSettings.takesThrone(StructureType.MANOR, true, Double.NaN, random));
            assertTrue(HerobrineSettings.takesThrone(StructureType.MANOR, true, 7.0D, random));
        }
    }

    @Test
    public void threePercentIsRareButHappens() {
        Random random = new Random(42L);
        int hits = 0;
        for (int i = 0; i < 10_000; i++) {
            if (HerobrineSettings.takesThrone(StructureType.MANOR, true, 0.03D, random)) {
                hits++;
            }
        }
        assertTrue("hits " + hits, hits > 220 && hits < 380);
    }

    @Test
    public void optionIsNamedInBothLanguagesAndUsedByTheBossFactory() throws IOException {
        for (String lang : new String[]{"en_us", "ru_ru"}) {
            assertTrue(lang, read("src/main/resources/assets/shatteredcitadels/lang/" + lang + ".lang")
                    .contains("config.shatteredcitadels.herobrine.manorBossChance="));
        }
        String factory = read("src/main/java/com/shatteredcitadels/boss/BossFactory.java");
        assertTrue(factory.contains("HerobrineSettings.takesThrone(citadel.getType(), random)"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
