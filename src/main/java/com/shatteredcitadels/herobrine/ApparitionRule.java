package com.shatteredcitadels.herobrine;

import java.util.Random;

/** When and where the harmless figure may show itself. Pure: the handler supplies the facts. */
public final class ApparitionRule {

    public static final int MIN_DISTANCE = 30;
    public static final int MAX_DISTANCE = 60;
    /** The figure vanishes when a player comes this close. */
    public static final double VANISH_DISTANCE = 12.0D;
    /** The figure vanishes after being looked at for this many ticks. */
    public static final int VANISH_LOOK_TICKS = 20;
    /** The figure vanishes by itself after this many ticks. */
    public static final int LIFETIME_TICKS = 600;
    /** The lair whispers to players this close to it (blocks, horizontally). */
    public static final int WHISPER_RADIUS = 48;
    /** One look in this many ends in a whisper. */
    private static final int WHISPER_ODDS = 6;
    private static final int TICKS_PER_MINUTE = 1200;

    private ApparitionRule() {
    }

    /** @return the world time of the next attempt after one made at {@code now}. */
    public static long nextAttempt(long now, int intervalMinutes) {
        return now + (long) Math.max(1, intervalMinutes) * TICKS_PER_MINUTE;
    }

    /** @return true if the surroundings suit the figure: night, a thunderstorm or no sky above. */
    public static boolean isMoment(boolean night, boolean thundering, boolean underground) {
        return night || thundering || underground;
    }

    /**
     * @param nextAttemptAt the time stored for the player; 0 for a player not seen yet
     * @return true if an attempt is due now (the caller then stores {@link #nextAttempt})
     */
    public static boolean isDue(long now, long nextAttemptAt) {
        return now >= nextAttemptAt;
    }

    /**
     * The figure only haunts the land around a lair whose master is not defeated yet.
     *
     * @return true if the player stands within {@code radius} blocks (horizontally) of the lair centre
     */
    public static boolean isHaunted(double playerX, double playerZ, int lairX, int lairZ, boolean defeated, int radius) {
        if (defeated || radius <= 0) {
            return false;
        }
        double dx = playerX - lairX;
        double dz = playerZ - lairZ;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /** @return true if this look of a player standing near a living lair ends in a whisper. */
    public static boolean whispers(Random random) {
        return random.nextInt(WHISPER_ODDS) == 0;
    }

    /** @return true if a due attempt at a fitting moment really shows the figure. */
    public static boolean rolls(Random random, double chance) {
        return random.nextDouble() < chance;
    }

    /**
     * Picks the column of the figure.
     *
     * @return {x, z}, {@value #MIN_DISTANCE} to {@value #MAX_DISTANCE} blocks from the player
     */
    public static int[] spot(Random random, double playerX, double playerZ) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = MIN_DISTANCE + 0.5D + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE - 1);
        return new int[]{(int) Math.floor(playerX + Math.cos(angle) * distance),
                (int) Math.floor(playerZ + Math.sin(angle) * distance)};
    }

    /**
     * @param dot cosine of the angle between the look of the player and the direction to the figure
     * @return true if the player looks at the figure closely enough to count
     */
    public static boolean isLookedAt(double dot, double distance) {
        return distance > 0.0D && dot > 1.0D - 0.03D / Math.max(1.0D, distance / 16.0D);
    }

    /** @return true if the figure has to vanish now. */
    public static boolean shouldVanish(int age, double nearestPlayerDistance, int lookedAtTicks) {
        return age >= LIFETIME_TICKS || nearestPlayerDistance < VANISH_DISTANCE || lookedAtTicks >= VANISH_LOOK_TICKS;
    }
}
