package com.shatteredcitadels.client;

import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.FloorData;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Localised strings for the floor HUD and banner. */
@SideOnly(Side.CLIENT)
public final class CitadelTextFormatter {

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    private CitadelTextFormatter() {
    }

    /** @return the localised citadel name, or the raw text for non-generated names. */
    public static String name(String stored) {
        String[] keys = CitadelNameGenerator.parseKeys(stored);
        if (keys == null) {
            return stored == null ? "" : stored;
        }
        return I18n.format(CitadelNameGenerator.KEY_FORMAT, I18n.format(keys[0]), I18n.format(keys[1]));
    }

    public static String roman(int tier) {
        return ROMAN[FloorData.clampDifficulty(tier) - 1];
    }

    public static String difficulty(int tier) {
        return I18n.format("shatteredcitadels.difficulty." + FloorData.clampDifficulty(tier));
    }

    /** "Floor 3 / 6 — Difficulty: High (Tier III)" or "Crypt -2 — Danger: Deadly". */
    public static String bannerSubtitle(int floorIndex, int surfaceFloors, int tier) {
        if (floorIndex < 0) {
            return I18n.format("shatteredcitadels.hud.banner.crypt", floorIndex, difficulty(tier));
        }
        return I18n.format("shatteredcitadels.hud.banner.floor", floorIndex + 1, Math.max(1, surfaceFloors),
                difficulty(tier), roman(tier));
    }

    /** "Floor 3/6 · High (III)" or "Crypt -2 · Deadly (V)". */
    public static String plaqueFloor(int floorIndex, int surfaceFloors, int tier) {
        if (floorIndex < 0) {
            return I18n.format("shatteredcitadels.hud.plaque.crypt", floorIndex, difficulty(tier), roman(tier));
        }
        return I18n.format("shatteredcitadels.hud.plaque.floor", floorIndex + 1, Math.max(1, surfaceFloors),
                difficulty(tier), roman(tier));
    }

    public static String translate(String key, Object... args) {
        return I18n.format(key, args);
    }
}
