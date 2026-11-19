package com.shatteredcitadels.mob;

import com.shatteredcitadels.config.MobTiers;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Immutable result of a scan: mobs per tier plus the underground pool. Published atomically by
 * {@link ModMobTierScanner}; readers never see a partial scan.
 */
public final class MobTierRegistry {

    private static final ResourceLocation ZOMBIE = new ResourceLocation("minecraft", "zombie");

    /** Vanilla stand-ins, used when a tier or the underground pool would otherwise be empty. */
    static final String[][] VANILLA_TIERS = {
            {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider"},
            {"minecraft:husk", "minecraft:stray", "minecraft:zombie_villager"},
            {"minecraft:wither_skeleton", "minecraft:vindication_illager", "minecraft:blaze"},
            {"minecraft:evocation_illager", "minecraft:wither_skeleton", "minecraft:vindication_illager"},
            {"minecraft:wither_skeleton", "minecraft:evocation_illager", "minecraft:blaze"}
    };
    static final String[] VANILLA_UNDERGROUND = {
            "minecraft:cave_spider", "minecraft:husk", "minecraft:wither_skeleton", "minecraft:zombie_pigman",
            "minecraft:stray"
    };

    private static volatile MobTierRegistry current = new MobTierRegistry(Collections.<Integer, List<MobEntry>>emptyMap(),
            Collections.<MobEntry>emptyList(), false);

    private final Map<Integer, List<MobEntry>> tiers;
    private final List<MobEntry> underground;
    private final boolean scanned;

    MobTierRegistry(Map<Integer, List<MobEntry>> tiers, List<MobEntry> underground, boolean scanned) {
        Map<Integer, List<MobEntry>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<MobEntry>> e : tiers.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        this.tiers = Collections.unmodifiableMap(copy);
        this.underground = Collections.unmodifiableList(new ArrayList<>(underground));
        this.scanned = scanned;
    }

    public static MobTierRegistry get() {
        return current;
    }

    static void publish(MobTierRegistry registry) {
        current = registry;
    }

    public boolean isScanned() {
        return scanned;
    }

    public List<MobEntry> getTier(int tier) {
        List<MobEntry> list = tiers.get(tier);
        return list == null ? Collections.<MobEntry>emptyList() : list;
    }

    public List<MobEntry> getUnderground() {
        return underground;
    }

    public int size() {
        int n = 0;
        for (List<MobEntry> list : tiers.values()) {
            n += list.size();
        }
        return n;
    }

    /**
     * Picks {@code count} distinct mob types for a floor: the requested tier first, padded from the
     * neighbouring lower tiers, mixed with the underground pool when {@code underground} is set.
     * Never empty: falls back to a zombie.
     */
    public List<MobEntry> pickCombo(Random random, int tier, boolean underground, int count) {
        int wanted = Math.max(1, count);
        List<MobEntry> candidates = new ArrayList<>();
        if (underground) {
            for (MobEntry entry : this.underground) {
                if (Math.abs(entry.getTier() - tier) <= 1) {
                    candidates.add(entry);
                }
            }
        }
        for (int t = MobTiers.clamp(tier); t >= MobTiers.MIN_TIER && candidates.size() < wanted * 2; t--) {
            candidates.addAll(getTier(t));
        }
        List<MobEntry> unique = new ArrayList<>();
        for (MobEntry entry : candidates) {
            boolean seen = false;
            for (MobEntry u : unique) {
                if (u.getId().equals(entry.getId())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(entry);
            }
        }
        Collections.shuffle(unique, random);
        List<MobEntry> combo = new ArrayList<>(unique.subList(0, Math.min(wanted, unique.size())));
        if (combo.isEmpty()) {
            combo.add(new MobEntry(ZOMBIE, MobTiers.MIN_TIER, 32.0D, false, true, false));
        }
        return combo;
    }
}
