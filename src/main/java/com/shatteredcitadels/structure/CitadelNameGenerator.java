package com.shatteredcitadels.structure;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Random;

/**
 * Atmospheric citadel names ("Citadel of the Shattered Crown", «Цитадель Расколотой Короны»).
 * The stored name is language-neutral: {@code @name:<group>:<title>:<epithet>}. Each client
 * localises it through the lang keys below, so players see names in their own language.
 */
public final class CitadelNameGenerator {

    public static final String PREFIX = "@name:";
    public static final int TITLES_PER_GROUP = 6;
    public static final int EPITHETS = 16;

    public static final String KEY_FORMAT = "shatteredcitadels.name.format";
    public static final String KEY_TITLE = "shatteredcitadels.name.title.";
    public static final String KEY_EPITHET = "shatteredcitadels.name.epithet.";

    private CitadelNameGenerator() {
    }

    /** @param group archetype key, "castle_dungeons" or "crypt" */
    public static String generate(String group, Random random) {
        String safeGroup = group == null || group.isEmpty() ? "crypt" : group;
        return PREFIX + safeGroup + ":" + random.nextInt(TITLES_PER_GROUP) + ":" + random.nextInt(EPITHETS);
    }

    /** @return {titleKey, epithetKey}, or null if the name is not a generated one. */
    public static String[] parseKeys(String name) {
        if (name == null || !name.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = name.substring(PREFIX.length()).split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            int title = Integer.parseInt(parts[1]);
            int epithet = Integer.parseInt(parts[2]);
            if (title < 0 || title >= TITLES_PER_GROUP || epithet < 0 || epithet >= EPITHETS) {
                return null;
            }
            return new String[]{KEY_TITLE + parts[0] + "." + title, KEY_EPITHET + epithet};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Server-safe chat component: translated by each receiving client. */
    public static ITextComponent toComponent(String name) {
        String[] keys = parseKeys(name);
        if (keys == null) {
            return new TextComponentString(name == null ? "" : name);
        }
        return new TextComponentTranslation(KEY_FORMAT, new TextComponentTranslation(keys[0]),
                new TextComponentTranslation(keys[1]));
    }
}
