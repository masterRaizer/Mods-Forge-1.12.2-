package com.shatteredcitadels.palette;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The five architectural archetypes. Each slot lists alternatives in priority order; the first
 * alternative that resolves to at least one valid block wins. An alternative is a group of
 * variants joined with {@code |}; each variant is {@code modid:block}, {@code modid:block@meta}
 * or a registry scan {@code modid:*keyword*}. The last alternative of every slot is vanilla
 * 1.12.2 and always resolves.
 */
public enum CitadelArchetype {

    /** 1) Stone Citadel of the Keepers. */
    KEEPERS_CITADEL("Stone Citadel of the Keepers", spec()
            .slot(PaletteSlot.WALL,
                    "moreblocks:*stone_brick*",
                    "minecraft:stonebrick@0|minecraft:stonebrick@0|minecraft:stonebrick@0|minecraft:stonebrick@1|minecraft:stonebrick@2")
            .slot(PaletteSlot.PILLAR,
                    "moreblocks:*pillar*",
                    "minecraft:stonebrick@3")
            .slot(PaletteSlot.FLOOR,
                    "futuremc:smooth_stone",
                    "minecraft:double_stone_slab@8")
            .slot(PaletteSlot.STAIRS,
                    "minecraft:stone_brick_stairs")
            .slot(PaletteSlot.ACCENT,
                    "minecraft:mossy_cobblestone|minecraft:stonebrick@1")
            .slot(PaletteSlot.LIGHT,
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "cfm:*chair*|cfm:*table*|cfm:*cabinet*",
                    "futuremc:barrel|futuremc:lectern",
                    "minecraft:bookshelf|minecraft:bookshelf|minecraft:crafting_table")),

    /** 2) Obsidian-Nether Bastille. */
    OBSIDIAN_BASTILLE("Obsidian-Nether Bastille", spec()
            .slot(PaletteSlot.WALL,
                    "unseens_nether:*brick*",
                    "minecraft:nether_brick|minecraft:nether_brick|minecraft:red_nether_brick")
            .slot(PaletteSlot.PILLAR,
                    "futuremc:crying_obsidian|minecraft:obsidian",
                    "minecraft:obsidian")
            .slot(PaletteSlot.FLOOR,
                    "futuremc:*blackstone*",
                    "minecraft:red_nether_brick")
            .slot(PaletteSlot.STAIRS,
                    "minecraft:nether_brick_stairs")
            .slot(PaletteSlot.ACCENT,
                    "tconstruct:seared@5",
                    "minecraft:quartz_block@1")
            .slot(PaletteSlot.LIGHT,
                    "unseens_nether:*glow*",
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "futuremc:blast_furnace|futuremc:smoker",
                    "minecraft:cauldron|minecraft:brewing_stand")),

    /** 3) Techno-Arcane Spire. */
    TECHNO_ARCANE_SPIRE("Techno-Arcane Spire", spec()
            .slot(PaletteSlot.WALL,
                    "thermalfoundation:rockwool@7|thermalfoundation:rockwool@8",
                    "mekanism:plasticblock@7|mekanism:plasticblock@8",
                    "minecraft:concrete@7|minecraft:concrete@8")
            .slot(PaletteSlot.PILLAR,
                    "ic2:*reinforced*",
                    "minecraft:quartz_block@2")
            .slot(PaletteSlot.FLOOR,
                    "mekanism:plasticblock@15",
                    "minecraft:concrete@15|minecraft:concrete@7")
            .slot(PaletteSlot.STAIRS,
                    "minecraft:quartz_stairs")
            .slot(PaletteSlot.ACCENT,
                    "enderio:block_fused_quartz",
                    "thermalfoundation:glass",
                    "minecraft:stained_glass@3")
            .slot(PaletteSlot.LIGHT,
                    "mekanism:glowplasticblock@3",
                    "minecraft:sea_lantern")
            .slot(PaletteSlot.FURNITURE,
                    "minecraft:cauldron|minecraft:bookshelf|minecraft:bookshelf")),

    /** 4) Forge of the Ancient Titans. */
    TITAN_FORGE("Forge of the Ancient Titans", spec()
            .slot(PaletteSlot.WALL,
                    "tconstruct:seared@3|tconstruct:seared@4|tconstruct:seared@6",
                    "minecraft:brick_block")
            .slot(PaletteSlot.PILLAR,
                    "taiga:*basalt*",
                    "moreblocks:*pillar*",
                    "minecraft:stonebrick@3")
            .slot(PaletteSlot.FLOOR,
                    "tconstruct:seared@2",
                    "minecraft:stone@6|minecraft:stone@4")
            .slot(PaletteSlot.STAIRS,
                    "tconstruct:seared_stairs_brick",
                    "minecraft:brick_stairs")
            .slot(PaletteSlot.ACCENT,
                    "tconstruct:seared@11",
                    "minecraft:stained_hardened_clay@1")
            .slot(PaletteSlot.LIGHT,
                    "tconstruct:firewood@1",
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "minecraft:furnace|minecraft:cauldron|minecraft:crafting_table")),

    /** 5) Sky-Piercing Citadel (Citadel Above the Clouds). */
    SKY_CITADEL("Sky-Piercing Citadel", spec()
            .slot(PaletteSlot.WALL,
                    "minecraft:quartz_block@0")
            .slot(PaletteSlot.PILLAR,
                    "draconicevolution:infused_obsidian",
                    "minecraft:purpur_pillar")
            .slot(PaletteSlot.FLOOR,
                    "futuremc:smooth_quartz",
                    "minecraft:purpur_block")
            .slot(PaletteSlot.STAIRS,
                    "minecraft:quartz_stairs")
            .slot(PaletteSlot.ACCENT,
                    "thermalfoundation:glass_alloy",
                    "minecraft:end_bricks")
            .slot(PaletteSlot.LIGHT,
                    "minecraft:sea_lantern")
            .slot(PaletteSlot.FURNITURE,
                    "minecraft:bookshelf|minecraft:end_rod"));

    private final String displayName;
    private final Map<PaletteSlot, String[]> alternatives;

    CitadelArchetype(String displayName, Spec spec) {
        this.displayName = displayName;
        this.alternatives = Collections.unmodifiableMap(spec.slots);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return candidate alternatives for the slot in priority order; last one is vanilla. */
    public String[] getAlternatives(PaletteSlot slot) {
        String[] values = alternatives.get(slot);
        return values == null ? new String[0] : values.clone();
    }

    public static CitadelArchetype byKey(String key) {
        for (CitadelArchetype archetype : values()) {
            if (archetype.key().equals(key)) {
                return archetype;
            }
        }
        return null;
    }

    private static Spec spec() {
        return new Spec();
    }

    private static final class Spec {
        private final Map<PaletteSlot, String[]> slots = new EnumMap<>(PaletteSlot.class);

        Spec slot(PaletteSlot slot, String... alternatives) {
            slots.put(slot, alternatives);
            return this;
        }
    }
}
