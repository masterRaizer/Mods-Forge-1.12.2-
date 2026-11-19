package com.shatteredcitadels.compat.castledungeons;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Dependency-free status holder written by the mixin config plugin while classes are being
 * transformed. Must not reference Minecraft, Forge or config classes.
 */
public final class CastleDungeonsMixinState {

    private static final Set<String> APPLIED = Collections.synchronizedSet(new LinkedHashSet<String>());
    private static final Set<String> SKIPPED = Collections.synchronizedSet(new LinkedHashSet<String>());

    private CastleDungeonsMixinState() {
    }

    public static void markApplied(String mixinClass) {
        APPLIED.add(simpleName(mixinClass));
    }

    public static void markSkipped(String mixinClass) {
        SKIPPED.add(simpleName(mixinClass));
    }

    public static boolean isApplied(String simpleMixinName) {
        return APPLIED.contains(simpleMixinName);
    }

    public static String describe() {
        synchronized (APPLIED) {
            synchronized (SKIPPED) {
                return "applied=" + APPLIED + ", skipped=" + SKIPPED;
            }
        }
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
