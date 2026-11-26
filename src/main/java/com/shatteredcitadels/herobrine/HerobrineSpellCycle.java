package com.shatteredcitadels.herobrine;

import java.util.Random;

/** What Herobrine does next. Pure: the entity only carries the decision out. */
public final class HerobrineSpellCycle {

    public enum Action {
        /** Steps behind the target. */
        TELEPORT,
        /** Marks a spot, then strikes it with lightning. */
        LIGHTNING,
        /** Calls undead minions. */
        SUMMON,
        /** Blinds the players around for a moment. */
        DARKNESS
    }

    /** The shortest pause between two actions: teleports and spells always have a cooldown. */
    public static final int MIN_COOLDOWN = 40;

    private HerobrineSpellCycle() {
    }

    /**
     * @param phase         1..3
     * @param aliveMinions  minions still alive
     * @param maxMinions    the cap of the config; 0 switches summoning off
     * @param darknessReady false while the last darkness is still recent
     */
    public static Action next(Random random, int phase, int aliveMinions, int maxMinions, boolean darknessReady) {
        int roll = random.nextInt(100);
        if (phase <= 1) {
            return Action.TELEPORT;
        }
        if (phase == 2) {
            return roll < 55 ? Action.LIGHTNING : Action.TELEPORT;
        }
        if (roll < 35 && aliveMinions < maxMinions) {
            return Action.SUMMON;
        }
        if (roll < 60) {
            return Action.LIGHTNING;
        }
        if (roll < 78 && darknessReady) {
            return Action.DARKNESS;
        }
        return Action.TELEPORT;
    }

    /** @return ticks until the next action; later phases act faster, never under {@link #MIN_COOLDOWN}. */
    public static int cooldownAfter(Action action, int phase) {
        int base;
        switch (action) {
            case LIGHTNING:
                base = 80;
                break;
            case SUMMON:
                base = 140;
                break;
            case DARKNESS:
                base = 100;
                break;
            default:
                base = 90;
                break;
        }
        return Math.max(MIN_COOLDOWN, base - 10 * (Math.max(1, Math.min(3, phase)) - 1));
    }

    /** @return how many minions one summon may add without passing the cap. */
    public static int summonCount(int aliveMinions, int maxMinions) {
        return Math.max(0, Math.min(3, maxMinions - aliveMinions));
    }
}
