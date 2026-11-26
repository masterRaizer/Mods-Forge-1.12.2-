package com.shatteredcitadels.herobrine;

/** The three phases of the Herobrine fight, decided by the share of health left. */
public final class HerobrinePhases {

    /** At or below this share of health the second phase begins (lightning). */
    public static final float SECOND = 0.66F;
    /** At or below this share of health the third phase begins (minions, darkness). */
    public static final float THIRD = 0.33F;

    private HerobrinePhases() {
    }

    /** @return 1, 2 or 3 */
    public static int phaseFor(float healthFraction) {
        if (Float.isNaN(healthFraction) || healthFraction > SECOND) {
            return 1;
        }
        return healthFraction > THIRD ? 2 : 3;
    }

    /** @return the phase after a change of health: a fight never goes back to an earlier phase. */
    public static int advance(int current, float healthFraction) {
        return Math.max(Math.max(1, Math.min(3, current)), phaseFor(healthFraction));
    }
}
