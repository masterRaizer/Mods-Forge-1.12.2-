package com.shatteredcitadels.mob;

import net.minecraft.util.ResourceLocation;

/** One classified mob type. */
public final class MobEntry {

    private final ResourceLocation id;
    private final int tier;
    private final double power;
    private final boolean underground;
    /** Vanilla stand-in that the spawner reinforces with extra buffs. */
    private final boolean reinforcedFallback;
    private final boolean manual;

    public MobEntry(ResourceLocation id, int tier, double power, boolean underground, boolean reinforcedFallback,
                    boolean manual) {
        this.id = id;
        this.tier = tier;
        this.power = power;
        this.underground = underground;
        this.reinforcedFallback = reinforcedFallback;
        this.manual = manual;
    }

    public ResourceLocation getId() { return id; }
    public int getTier() { return tier; }
    public double getPower() { return power; }
    public boolean isUnderground() { return underground; }
    public boolean isReinforcedFallback() { return reinforcedFallback; }
    public boolean isManual() { return manual; }

    @Override
    public String toString() {
        return id + "(T" + tier + (underground ? ",UG" : "") + ")";
    }
}
