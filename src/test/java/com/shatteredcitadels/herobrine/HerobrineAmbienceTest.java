package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.config.ModConfig;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** How rare the watching figure is and how the lair whispers. */
public class HerobrineAmbienceTest {

    /** The owner asked for about 0.03 % per attempt. */
    @Test
    public void apparitionIsVeryRareByDefault() {
        assertEquals(0.0003D, new ModConfig.Herobrine().apparitionChance, 1.0e-9D);
    }

    @Test
    public void rareChanceAlmostNeverRolls() {
        Random random = new Random(42L);
        int shown = 0;
        for (int i = 0; i < 100_000; i++) {
            if (ApparitionRule.rolls(random, 0.0003D)) {
                shown++;
            }
        }
        assertTrue("shown " + shown, shown > 5 && shown < 80);
    }

    @Test
    public void whisperIsHeardOnlyCloseToALivingLair() {
        assertTrue(ApparitionRule.WHISPER_RADIUS <= 64);
        assertTrue(ApparitionRule.isHaunted(10.0D, 10.0D, 0, 0, false, ApparitionRule.WHISPER_RADIUS));
        assertFalse(ApparitionRule.isHaunted(10.0D, 10.0D, 0, 0, true, ApparitionRule.WHISPER_RADIUS));
        assertFalse(ApparitionRule.isHaunted(ApparitionRule.WHISPER_RADIUS + 1.0D, 0.0D, 0, 0, false, ApparitionRule.WHISPER_RADIUS));
    }

    /** One look every five seconds: a whisper about once in half a minute, never a drone. */
    @Test
    public void whisperIsOccasional() {
        Random random = new Random(7L);
        int heard = 0;
        for (int i = 0; i < 10_000; i++) {
            if (ApparitionRule.whispers(random)) {
                heard++;
            }
        }
        assertTrue("heard " + heard, heard > 1000 && heard < 2500);
    }
}
