package com.shatteredcitadels.tile;

import net.minecraft.world.BossInfoServer;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BossBarsTest {

    static class Plain { int health; }

    static class WithBar { @SuppressWarnings("unused") private BossInfoServer bar; }

    static class ChildOfWithBar extends WithBar { }

    /** Like Mowzie's Mobs: every entity carries the field, a method says who shows it. */
    static class Switchable {
        @SuppressWarnings("unused") private BossInfoServer bar;
        protected boolean hasBossBar() { return false; }
    }

    static class SwitchedOn extends Switchable {
        @Override protected boolean hasBossBar() { return true; }
    }

    static class MethodOnly { protected boolean hasBossBar() { return true; } }

    @Test
    public void entityWithoutBarFieldKeepsOurBar() {
        assertFalse(BossBars.hasOwnBar(new Plain()));
        assertFalse(BossBars.hasOwnBar(null));
        assertFalse(BossBars.hasOwnBar(new MethodOnly()));
    }

    @Test
    public void barFieldAnywhereInHierarchyCounts() {
        assertTrue(BossBars.hasOwnBar(new WithBar()));
        assertTrue(BossBars.hasOwnBar(new ChildOfWithBar()));
    }

    @Test
    public void hasBossBarMethodDecides() {
        assertFalse(BossBars.hasOwnBar(new Switchable()));
        assertTrue(BossBars.hasOwnBar(new SwitchedOn()));
    }
}
