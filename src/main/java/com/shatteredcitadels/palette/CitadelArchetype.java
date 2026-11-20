package com.shatteredcitadels.palette;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The five architectural archetypes. Each slot lists alternatives; every modded alternative that
 * resolves becomes an option, and each citadel rolls one option per slot (see
 * {@link ModPaletteRegistry#forCitadel}). An alternative is a group of variants joined with
 * {@code |}; see {@link ModPaletteRegistry} for the variant syntax. The last alternative of every
 * slot is vanilla 1.12.2, always resolves, and is used only when no modded option does.
 */
public enum CitadelArchetype {

    /** 1) Stone Citadel of the Keepers. */
    KEEPERS_CITADEL("Stone Citadel of the Keepers", spec()
            .slot(PaletteSlot.WALL,
                    "quark:world_stone_bricks[variant=stone_marble_bricks]|quark:world_stone_bricks[variant=stone_marble_bricks]|quark:world_stone_carved[variant=stone_marble_carved]",
                    "quark:world_stone_bricks[variant=stone_limestone_bricks]|quark:world_stone_bricks[variant=stone_limestone_bricks]|quark:world_stone_carved[variant=stone_limestone_carved]",
                    "quark:world_stone_bricks[variant=stone_slate_bricks]|quark:world_stone_bricks[variant=stone_slate_bricks]|quark:world_stone_carved[variant=stone_slate_carved]",
                    "quark:world_stone_bricks[variant=stone_andesite_bricks]|minecraft:stonebrick@0|minecraft:stonebrick@1",
                    "tconstruct:brownstone[type=brick]|tconstruct:brownstone[type=brick]|tconstruct:brownstone[type=brick_cracked]",
                    "atum:*limestone_brick*",
                    "minecraft:stonebrick@0|minecraft:stonebrick@0|minecraft:stonebrick@0|minecraft:stonebrick@1|minecraft:stonebrick@2")
            .slot(PaletteSlot.PILLAR,
                    "quark:marble[variant=stone_marble_smooth]",
                    "quark:slate[variant=stone_slate_smooth]",
                    "quark:world_stone_carved[variant=stone_andesite_carved]",
                    "projectred-exploration:stone[*marble_brick*]",
                    "minecraft:stonebrick@3")
            .slot(PaletteSlot.FLOOR,
                    "futuremc:smooth_stone",
                    "quark:world_stone_pavement[variant=stone_andesite_pavement]|quark:world_stone_pavement[variant=stone_diorite_pavement]",
                    "quark:world_stone_pavement[variant=stone_slate_pavement]",
                    "tconstruct:brownstone[type=paver]",
                    "minecraft:double_stone_slab@8")
            .slot(PaletteSlot.STAIRS,
                    "quark:stone_marble_bricks_stairs",
                    "quark:stone_andesite_bricks_stairs",
                    "quark:stone_slate_bricks_stairs",
                    "minecraft:stone_brick_stairs")
            .slot(PaletteSlot.ACCENT,
                    "quark:world_stone_carved[variant=stone_jasper_carved]",
                    "quark:biome_cobblestone[variant=cobbed_stone]|minecraft:mossy_cobblestone",
                    "tconstruct:brownstone[type=brick_fancy]",
                    "nb:cracked_stone_bricks|quark:world_stone_carved[variant=stone_andesite_carved]",
                    "modernbackports:dead_brain_coral_block|modernbackports:dead_tube_coral_block|modernbackports:dead_horn_coral_block",
                    "minecraft:mossy_cobblestone|minecraft:stonebrick@1")
            .slot(PaletteSlot.LIGHT,
                    "nb:shroom_light",
                    "quark:blaze_lantern",
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "cfm:*chair*|cfm:*table*|cfm:*cabinet*",
                    "futuremc:barrel",
                    "minecraft:bookshelf|minecraft:bookshelf|minecraft:crafting_table")),

    /** 2) Obsidian-Nether Bastille. */
    OBSIDIAN_BASTILLE("Obsidian-Nether Bastille", spec()
            .slot(PaletteSlot.WALL,
                    "quark:charred_nether_bricks|quark:charred_nether_bricks|minecraft:nether_brick",
                    "quark:polished_netherrack[variant=polished_netherrack_bricks]|minecraft:nether_brick",
                    "nb:black_stone_bricks|nb:black_stone_bricks|nb:cracked_stone_bricks",
                    "nb:polished_black_stone|nb:black_stone_bricks",
                    "moreblocks:crackednetherbricks|moreblocks:chiselednetherbricks|minecraft:nether_brick",
                    "minecraft:nether_brick|minecraft:nether_brick|minecraft:red_nether_brick")
            .slot(PaletteSlot.PILLAR,
                    "nb:cry_obi|minecraft:obsidian",
                    "enderio:block_reinforced_obsidian",
                    "nb:polished_basalt",
                    "nb:smooth_basalt",
                    "quark:basalt[variant=stone_basalt_smooth]",
                    "minecraft:obsidian")
            .slot(PaletteSlot.FLOOR,
                    "nb:polished_black_stone",
                    "nb:black_stone",
                    "quark:polished_netherrack[variant=polished_netherrack]",
                    "minecraft:red_nether_brick")
            .slot(PaletteSlot.STAIRS,
                    "quark:charred_nether_brick_stairs",
                    "nb:black_stone_brick_stairs",
                    "nb:polished_black_stone_stairs",
                    "quark:polished_netherrack_bricks_stairs",
                    "minecraft:nether_brick_stairs")
            .slot(PaletteSlot.ACCENT,
                    "quark:magma_bricks",
                    "nb:gilded_blackstone",
                    "nb:chisled_polished_black_stone",
                    "nb:quartz_bricks",
                    "quark:world_stone_bricks[variant=stone_jasper_bricks]",
                    "minecraft:quartz_block@1")
            .slot(PaletteSlot.LIGHT,
                    "nb:shroom_light",
                    "quark:blaze_lantern",
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "futuremc:blast_furnace|futuremc:smoker",
                    "minecraft:cauldron|minecraft:brewing_stand")),

    /** 3) Techno-Arcane Spire. */
    TECHNO_ARCANE_SPIRE("Techno-Arcane Spire", spec()
            .slot(PaletteSlot.WALL,
                    "thermalfoundation:rockwool@7|thermalfoundation:rockwool@8",
                    "mekanism:plasticblock@7|mekanism:plasticblock@8",
                    "immersiveengineering:sheetmetal[type=steel]|immersiveengineering:sheetmetal[type=steel]|immersiveengineering:sheetmetal[type=aluminum]",
                    "immersiveengineering:stone_decoration[type=concrete]|immersiveengineering:stone_decoration[type=concrete_tile]",
                    "quark:iron_plate[variant=iron_plate]|quark:iron_plate[variant=iron_plate]|quark:iron_plate[variant=rusty_iron_plate]",
                    "appliedenergistics2:sky_stone_brick|appliedenergistics2:smooth_sky_stone_block",
                    "minecraft:concrete@7|minecraft:concrete@8")
            .slot(PaletteSlot.PILLAR,
                    "immersiveengineering:metal_decoration0[type=heavy_engineering]",
                    "immersiveengineering:metal_decoration0[type=light_engineering]",
                    "appliedenergistics2:quartz_pillar",
                    "minecraft:quartz_block@2")
            .slot(PaletteSlot.FLOOR,
                    "mekanism:plasticblock@15",
                    "immersiveengineering:stone_decoration[type=concrete_tile]",
                    "immersiveengineering:sheetmetal[type=steel]",
                    "appliedenergistics2:sky_stone_brick",
                    "minecraft:concrete@15|minecraft:concrete@7")
            .slot(PaletteSlot.STAIRS,
                    "immersiveengineering:stone_decoration_stairs_concrete",
                    "immersiveengineering:stone_decoration_stairs_concrete_tile",
                    "quark:iron_plate_stairs",
                    "minecraft:quartz_stairs")
            .slot(PaletteSlot.ACCENT,
                    "enderio:block_fused_quartz",
                    "thermalfoundation:glass",
                    "immersiveengineering:metal_decoration0[type=rs_engineering]",
                    "quark:framed_glass",
                    "minecraft:stained_glass@3")
            .slot(PaletteSlot.LIGHT,
                    "quark:duskbound_lantern",
                    "quark:elder_sea_lantern",
                    "minecraft:sea_lantern")
            .slot(PaletteSlot.FURNITURE,
                    "minecraft:cauldron|minecraft:bookshelf|minecraft:bookshelf")),

    /** 4) Forge of the Ancient Titans. */
    TITAN_FORGE("Forge of the Ancient Titans", spec()
            .slot(PaletteSlot.WALL,
                    "immersiveengineering:stone_decoration[type=blastbrick]|immersiveengineering:stone_decoration[type=blastbrick]|immersiveengineering:stone_decoration[type=blastbrick_reinforced]",
                    "immersiveengineering:stone_decoration[type=cokebrick]",
                    "immersiveengineering:stone_decoration[type=alloybrick]",
                    "tconstruct:brownstone[type=brick]|tconstruct:brownstone[type=brick_small]",
                    "minecraft:brick_block")
            .slot(PaletteSlot.PILLAR,
                    "taiga:*basalt*",
                    "quark:basalt[variant=stone_basalt_smooth]",
                    "nb:polished_basalt",
                    "nb:smooth_basalt",
                    "minecraft:stonebrick@3")
            .slot(PaletteSlot.FLOOR,
                    "quark:world_stone_pavement[variant=stone_basalt_pavement]",
                    "immersiveengineering:stone_decoration[type=concrete_tile]",
                    "nb:polished_basalt",
                    "minecraft:stone@6|minecraft:stone@4")
            .slot(PaletteSlot.STAIRS,
                    "tconstruct:seared_stairs_brick",
                    "tconstruct:seared_stairs_brick_square",
                    "quark:stone_basalt_bricks_stairs",
                    "minecraft:brick_stairs")
            .slot(PaletteSlot.ACCENT,
                    "quark:magma_bricks",
                    "immersiveengineering:sheetmetal[type=copper]",
                    "immersiveengineering:stone_decoration[type=cokebrick]",
                    "minecraft:stained_hardened_clay@1")
            .slot(PaletteSlot.LIGHT,
                    "quark:blaze_lantern",
                    "nb:shroom_light",
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "minecraft:furnace|minecraft:cauldron|minecraft:crafting_table")),

    /** 5) Sky-Piercing Citadel (Citadel Above the Clouds). */
    SKY_CITADEL("Sky-Piercing Citadel", spec()
            .slot(PaletteSlot.WALL,
                    "quark:world_stone_bricks[variant=stone_marble_bricks]|quark:marble[variant=stone_marble_smooth]|minecraft:quartz_block@0",
                    "quark:world_stone_bricks[variant=stone_diorite_bricks]|minecraft:quartz_block@0",
                    "nb:quartz_bricks|minecraft:quartz_block@0",
                    "appliedenergistics2:quartz_block|minecraft:quartz_block@0",
                    "biomesoplenty:white_sandstone",
                    "atum:*alabaster*",
                    "minecraft:quartz_block@0")
            .slot(PaletteSlot.PILLAR,
                    "draconicevolution:infused_obsidian",
                    "quark:marble[variant=stone_marble_smooth]",
                    "appliedenergistics2:quartz_pillar",
                    "minecraft:purpur_pillar")
            .slot(PaletteSlot.FLOOR,
                    "futuremc:smooth_quartz",
                    "quark:world_stone_pavement[variant=stone_marble_pavement]",
                    "quark:world_stone_pavement[variant=stone_diorite_pavement]",
                    "minecraft:purpur_block")
            .slot(PaletteSlot.STAIRS,
                    "quark:stone_marble_bricks_stairs",
                    "quark:stone_diorite_bricks_stairs",
                    "minecraft:quartz_stairs")
            .slot(PaletteSlot.ACCENT,
                    "thermalfoundation:glass_alloy",
                    "quark:duskbound_block",
                    "enderio:block_fused_quartz",
                    "minecraft:end_bricks")
            .slot(PaletteSlot.LIGHT,
                    "quark:elder_sea_lantern",
                    "quark:duskbound_lantern",
                    "minecraft:sea_lantern")
            .slot(PaletteSlot.FURNITURE,
                    "minecraft:bookshelf|minecraft:end_rod")),

    /** 6) Gothic manor: dark slate and blackstone, dark oak, dim light. */
    GOTHIC_MANOR("Gothic Manor", spec()
            .slot(PaletteSlot.WALL,
                    "quark:world_stone_bricks[variant=stone_slate_bricks]|quark:world_stone_bricks[variant=stone_slate_bricks]|quark:slate[variant=stone_slate_smooth]",
                    "nb:black_stone_bricks|nb:black_stone_bricks|nb:polished_black_stone",
                    "quark:world_stone_bricks[variant=stone_basalt_bricks]|quark:world_stone_bricks[variant=stone_andesite_bricks]",
                    "minecraft:stonebrick@0|minecraft:stonebrick@2|minecraft:stonebrick@1")
            .slot(PaletteSlot.PILLAR,
                    "quark:basalt[variant=stone_basalt_smooth]",
                    "nb:polished_basalt",
                    "nb:smooth_basalt",
                    "minecraft:stonebrick@3")
            .slot(PaletteSlot.FLOOR,
                    "quark:world_stone_pavement[variant=stone_slate_pavement]",
                    "nb:polished_black_stone",
                    "minecraft:planks@5")
            .slot(PaletteSlot.STAIRS,
                    "quark:stone_slate_bricks_stairs",
                    "nb:black_stone_brick_stairs",
                    "minecraft:dark_oak_stairs")
            .slot(PaletteSlot.ACCENT,
                    "quark:world_stone_carved[variant=stone_slate_carved]",
                    "nb:chisled_polished_black_stone",
                    "nb:gilded_blackstone",
                    "minecraft:stonebrick@3")
            .slot(PaletteSlot.LIGHT,
                    "quark:blaze_lantern",
                    "nb:shroom_light",
                    "minecraft:glowstone")
            .slot(PaletteSlot.FURNITURE,
                    "cfm:*chair*|cfm:*table*|cfm:*cabinet*",
                    "minecraft:bookshelf|minecraft:bookshelf|minecraft:crafting_table"));

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
