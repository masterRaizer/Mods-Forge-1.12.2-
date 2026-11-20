package com.shatteredcitadels.entity;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MageSpellCycleTest {

    @Test
    public void noSummonOverCap() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertNotEquals(MageSpellCycle.Spell.SUMMON, MageSpellCycle.next(random, 4, 6));
        }
    }

    @Test
    public void everySpellIsCastSometimes() {
        Random random = new Random(2);
        boolean[] seen = new boolean[MageSpellCycle.Spell.values().length];
        for (int i = 0; i < 1000; i++) {
            seen[MageSpellCycle.next(random, 0, 6).ordinal()] = true;
        }
        for (boolean s : seen) {
            assertTrue(s);
        }
    }

    @Test
    public void cooldownsMatchSpec() {
        assertEquals(50, MageSpellCycle.cooldownAfter(MageSpellCycle.Spell.FIREBALLS));
        assertEquals(40, MageSpellCycle.cooldownAfter(MageSpellCycle.Spell.SLOW_BOLT));
        assertEquals(70, MageSpellCycle.cooldownAfter(MageSpellCycle.Spell.LIGHTNING));
        assertEquals(200, MageSpellCycle.cooldownAfter(MageSpellCycle.Spell.SUMMON));
    }

    @Test
    public void teleportRules() {
        assertTrue(MageSpellCycle.shouldTeleport(40, 0, 200));
        assertFalse(MageSpellCycle.shouldTeleport(39, 199, 200));
        assertTrue(MageSpellCycle.shouldTeleport(0, 200, 200));
    }
}
