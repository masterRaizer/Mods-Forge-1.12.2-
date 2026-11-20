package com.shatteredcitadels.entity;

import java.util.Random;

/** The guardian mage's choice of spell, its cooldowns and when it blinks away (pure logic). */
public final class MageSpellCycle {

    public enum Spell { FIREBALLS, SLOW_BOLT, LIGHTNING, SUMMON }

    /** A summon brings this many guards. */
    public static final int GUARDS_PER_SUMMON = 3;
    /** Ticks a player may stay in melee range before the mage teleports. */
    public static final int MELEE_TICKS_BEFORE_TELEPORT = 40;

    private static final int[] WEIGHTS = {4, 3, 3, 2};

    private MageSpellCycle() {
    }

    /** Weighted pick; never a summon that would exceed {@code maxGuards}. */
    public static Spell next(Random random, int aliveGuards, int maxGuards) {
        boolean canSummon = aliveGuards + GUARDS_PER_SUMMON <= maxGuards;
        int total = 0;
        for (Spell spell : Spell.values()) {
            if (spell != Spell.SUMMON || canSummon) {
                total += WEIGHTS[spell.ordinal()];
            }
        }
        int roll = random.nextInt(total);
        for (Spell spell : Spell.values()) {
            if (spell == Spell.SUMMON && !canSummon) {
                continue;
            }
            roll -= WEIGHTS[spell.ordinal()];
            if (roll < 0) {
                return spell;
            }
        }
        return Spell.FIREBALLS;
    }

    public static int cooldownAfter(Spell spell) {
        switch (spell) {
            case FIREBALLS:
                return 50;
            case SLOW_BOLT:
                return 40;
            case LIGHTNING:
                return 70;
            default:
                return 200;
        }
    }

    public static boolean shouldTeleport(int ticksInMelee, int ticksSinceTeleport, int nextTeleportAt) {
        return ticksInMelee >= MELEE_TICKS_BEFORE_TELEPORT || ticksSinceTeleport >= nextTeleportAt;
    }
}
