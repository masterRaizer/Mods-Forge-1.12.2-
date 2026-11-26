package com.shatteredcitadels.herobrine;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class HerobrineFightTest {

    @Test
    public void phasesFollowHealth() {
        assertEquals(1, HerobrinePhases.phaseFor(1.0F));
        assertEquals(1, HerobrinePhases.phaseFor(0.67F));
        assertEquals(2, HerobrinePhases.phaseFor(0.66F));
        assertEquals(2, HerobrinePhases.phaseFor(0.34F));
        assertEquals(3, HerobrinePhases.phaseFor(0.33F));
        assertEquals(3, HerobrinePhases.phaseFor(0.0F));
    }

    @Test
    public void phaseNeverGoesBack() {
        assertEquals(2, HerobrinePhases.advance(1, 0.5F));
        assertEquals(2, HerobrinePhases.advance(2, 0.9F));
        assertEquals(3, HerobrinePhases.advance(3, 1.0F));
        assertEquals(3, HerobrinePhases.advance(1, 0.1F));
    }

    private static Set<HerobrineSpellCycle.Action> seen(int phase, int alive, int max, boolean darkness) {
        Random random = new Random(7L);
        Set<HerobrineSpellCycle.Action> seen = EnumSet.noneOf(HerobrineSpellCycle.Action.class);
        for (int i = 0; i < 400; i++) {
            seen.add(HerobrineSpellCycle.next(random, phase, alive, max, darkness));
        }
        return seen;
    }

    @Test
    public void firstPhaseOnlyTeleports() {
        assertEquals(EnumSet.of(HerobrineSpellCycle.Action.TELEPORT), seen(1, 0, 6, true));
    }

    @Test
    public void secondPhaseAddsLightningOnly() {
        assertEquals(EnumSet.of(HerobrineSpellCycle.Action.TELEPORT, HerobrineSpellCycle.Action.LIGHTNING), seen(2, 0, 6, true));
    }

    @Test
    public void thirdPhaseUsesEverything() {
        assertEquals(EnumSet.allOf(HerobrineSpellCycle.Action.class), seen(3, 0, 6, true));
    }

    @Test
    public void noSummonAtTheCapAndNoDarknessOnCooldown() {
        Set<HerobrineSpellCycle.Action> seen = seen(3, 6, 6, false);
        assertFalse(seen.contains(HerobrineSpellCycle.Action.SUMMON));
        assertFalse(seen.contains(HerobrineSpellCycle.Action.DARKNESS));
        assertFalse(seen(3, 0, 0, true).contains(HerobrineSpellCycle.Action.SUMMON));
    }

    @Test
    public void everyActionLeavesABreather() {
        for (HerobrineSpellCycle.Action action : HerobrineSpellCycle.Action.values()) {
            for (int phase = 1; phase <= 3; phase++) {
                assertTrue(action + " " + phase, HerobrineSpellCycle.cooldownAfter(action, phase) >= HerobrineSpellCycle.MIN_COOLDOWN);
            }
            assertTrue(HerobrineSpellCycle.cooldownAfter(action, 3) <= HerobrineSpellCycle.cooldownAfter(action, 1));
        }
    }

    @Test
    public void summonsAreBounded() {
        assertEquals(3, HerobrineSpellCycle.summonCount(0, 6));
        assertEquals(2, HerobrineSpellCycle.summonCount(4, 6));
        assertEquals(0, HerobrineSpellCycle.summonCount(6, 6));
        assertEquals(0, HerobrineSpellCycle.summonCount(9, 6));
        assertEquals(0, HerobrineSpellCycle.summonCount(0, 0));
    }

    @Test
    public void settingsAreClamped() {
        assertEquals(1024.0D, HerobrineSettings.clamp(5000.0D, 20.0D, HerobrineSettings.MAX_HEALTH), 0.0D);
        assertEquals(20.0D, HerobrineSettings.clamp(-3.0D, 20.0D, HerobrineSettings.MAX_HEALTH), 0.0D);
        assertEquals(20.0D, HerobrineSettings.clamp(Double.NaN, 20.0D, HerobrineSettings.MAX_HEALTH), 0.0D);
        assertEquals(16, HerobrineSettings.clamp(99, 0, 16));
        assertEquals(0, HerobrineSettings.clamp(-1, 0, 16));
    }

    @Test
    public void relicOnlyHelpsItsWearerAgainstUndead() {
        assertEquals(10.0F, HerobrineRelic.damage(8.0F, true, true), 0.0001F);
        assertEquals(8.0F, HerobrineRelic.damage(8.0F, false, true), 0.0F);
        assertEquals(8.0F, HerobrineRelic.damage(8.0F, true, false), 0.0F);
    }

    @Test
    public void variantIdsRoundTrip() {
        for (LairVariant variant : LairVariant.values()) {
            assertEquals(variant, LairVariant.byId(variant.id()));
            assertTrue(HerobrineCommands.variantIds().contains(variant.id()));
        }
        assertEquals(null, LairVariant.byId("palace"));
        assertNotSame(LairVariant.KEEP, LairVariant.TOWER);
    }
}
