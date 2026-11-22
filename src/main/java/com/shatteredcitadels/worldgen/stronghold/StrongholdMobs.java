package com.shatteredcitadels.worldgen.stronghold;

import net.minecraft.util.ResourceLocation;

/** Mobs of the stronghold's spawners; the config list replaces the defaults (Task 4 wires the config). */
public final class StrongholdMobs {

    private StrongholdMobs() {
    }

    public static ResourceLocation[] configured() {
        return StrongholdBuilder.DEFAULT_MOBS;
    }
}
