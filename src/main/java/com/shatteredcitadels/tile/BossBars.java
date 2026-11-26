package com.shatteredcitadels.tile;

import net.minecraft.world.BossInfoServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tells whether a boss brings its own health bar, so the altar does not draw a second one.
 * Works on any mod's entity without a compile-time dependency: a boss-bar field somewhere in the
 * class hierarchy means "own bar", unless the entity says otherwise through a no-argument
 * {@code hasBossBar()} (entities that carry the field but only some of them show it).
 */
public final class BossBars {

    private BossBars() {
    }

    public static boolean hasOwnBar(Object boss) {
        if (boss == null) {
            return false;
        }
        for (Class<?> c = boss.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("hasBossBar") && m.getParameterTypes().length == 0
                            && m.getReturnType() == boolean.class) {
                        m.setAccessible(true);
                        return field(boss.getClass()) && (Boolean) m.invoke(boss);
                    }
                }
            } catch (Throwable t) {
                return false;
            }
        }
        return field(boss.getClass());
    }

    private static boolean field(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field f : c.getDeclaredFields()) {
                    if (BossInfoServer.class.isAssignableFrom(f.getType())) {
                        return true;
                    }
                }
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }
}
