package com.shatteredcitadels.loot;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.RandomChance;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraft.world.storage.loot.functions.SetMetadata;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixes items of installed mods into the citadel tables. Every candidate is looked up in
 * {@link ForgeRegistries#ITEMS}; items of missing mods are simply skipped, so a table never
 * references an unknown item. The low-floor "rare find" pool uses the configured chance.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ModLootInjector {

    /** {registry name, metadata (-1 = none), min count, max count, weight} */
    private static final Object[][] TIER_3_4 = {
            {"tconstruct:ingots", 0, 1, 3, 6}, {"tconstruct:ingots", 1, 1, 3, 6}, {"tconstruct:ingots", 4, 1, 2, 4},
            {"taiga:karmesine_ingot", -1, 1, 2, 4}, {"taiga:ovium_ingot", -1, 1, 2, 4}, {"taiga:jauxum_ingot", -1, 1, 2, 4},
            {"thermalfoundation:material", 128, 2, 5, 6}, {"thermalfoundation:material", 129, 2, 5, 6},
            {"thermalfoundation:material", 512, 1, 2, 3}, {"mekanism:ingot", 1, 2, 4, 5},
            {"mekanism:controlcircuit", 0, 1, 2, 4}, {"enderio:item_alloy_ingot", 0, 1, 3, 5},
            {"enderio:item_basic_capacitor", 0, 1, 2, 4},
            {"ironapples:iron_apple", -1, 1, 2, 4}
    };
    private static final Object[][] TIER_5_PLUS = {
            {"gb:globot", -1, 1, 3, 6}, {"gb:globot2", -1, 1, 2, 4},
            {"gb:globot3", -1, 1, 2, 2}, {"tconstruct:ingots", 2, 1, 2, 4},
            {"xat:poison_stone", -1, 1, 1, 1}, {"xat:dragons_eye", -1, 1, 1, 1}, {"xat:weightless_stone", -1, 1, 1, 1},
            {"artifacts:everlasting_beef", -1, 1, 1, 1}, {"artifacts:bottled_cloud", -1, 1, 1, 1},
            {"draconicevolution:draconium_ingot", -1, 1, 3, 3}
    };
    private static final Object[][] UNDERGROUND = {
            {"gb:globot", -1, 1, 3, 5}, {"tconstruct:ingots", 0, 1, 2, 4}, {"tconstruct:ingots", 1, 1, 2, 4},
            {"xat:poison_stone", -1, 1, 1, 1}, {"xat:dwarf_ring", -1, 1, 1, 1}, {"artifacts:night_vision_goggles", -1, 1, 1, 1},
            {"artifacts:lucky_horseshoe", -1, 1, 1, 1}
    };
    private static final Object[][] BOSS = {
            {"draconicevolution:draconium_ingot", -1, 2, 6, 6}, {"draconicevolution:wyvern_core", -1, 1, 1, 2},
            {"draconicevolution:draconic_core", -1, 1, 2, 3}, {"gb:globot3", -1, 2, 4, 4},
            {"gb:globot2", -1, 2, 5, 4}
    };
    private static final Object[][] BOSS_SKY = {
            {"draconicevolution:awakened_core", -1, 1, 1, 2}, {"draconicevolution:draconic_ingot", -1, 1, 3, 4},
            {"draconicevolution:wyvern_core", -1, 1, 2, 3}, {"gb:globot3", -1, 3, 6, 4}
    };
    /** Food of the pack's mods for pantries. */
    private static final Object[][] PANTRY = {
            {"modernbackports:sweet_berries", -1, 2, 6, 6}, {"modernbackports:honey_bottle", -1, 1, 2, 3},
            {"futuremc:sweet_berries", -1, 2, 6, 6}, {"futuremc:honey_bottle", -1, 1, 2, 3},
            {"ironapples:iron_apple", -1, 1, 1, 2},
            {"aquaculture:food", 4, 1, 3, 3}, {"aquaculture:food", 10, 1, 2, 2}, {"aquaculture:fish", 24, 1, 2, 2}, {"artifacts:everlasting_beef", -1, 1, 1, 1}
    };
    /** Personal trinkets for bedrooms. */
    private static final Object[][] BEDROOM = {
            {"xat:poison_stone", -1, 1, 1, 1}, {"xat:dwarf_ring", -1, 1, 1, 1}, {"xat:weightless_stone", -1, 1, 1, 1},
            {"artifacts:lucky_horseshoe", -1, 1, 1, 1}, {"artifacts:bottled_cloud", -1, 1, 1, 1},
            {"gb:globot", -1, 1, 2, 3}
    };
    /** Ores and ingots of the pack's tech mods for smelting workshops and vases. */
    private static final Object[][] SMELTERY = {
            {"tconstruct:ingots", 0, 1, 4, 6}, {"tconstruct:ingots", 1, 1, 4, 6}, {"tconstruct:ingots", 4, 1, 3, 4},
            {"thermalfoundation:material", 128, 2, 6, 6}, {"thermalfoundation:material", 129, 2, 6, 6},
            {"thermalfoundation:material", 131, 1, 4, 4}, {"mekanism:ingot", 1, 2, 5, 5}, {"mekanism:ingot", 0, 1, 3, 4},
            {"immersiveengineering:metal", 0, 1, 4, 4}, {"enderio:item_alloy_ingot", 0, 1, 3, 4},
            {"taiga:karmesine_ingot", -1, 1, 2, 2}, {"taiga:ovium_ingot", -1, 1, 2, 2}
    };

    /** Magic loot of the pack's mods for the guardian mage's pedestals and the deepest catacomb level. */
    private static final Object[][] MAGE = {
            {"artifacts:night_vision_goggles", -1, 1, 1, 2}, {"artifacts:lucky_horseshoe", -1, 1, 1, 2},
            {"artifacts:bottled_cloud", -1, 1, 1, 2}, {"artifacts:everlasting_beef", -1, 1, 1, 1},
            {"artifacts:magma_stone", -1, 1, 1, 2}, {"artifacts:obsidian_skull", -1, 1, 1, 2},
            {"xat:poison_stone", -1, 1, 1, 2}, {"xat:dragons_eye", -1, 1, 1, 1}, {"xat:weightless_stone", -1, 1, 1, 2},
            {"xat:dwarf_ring", -1, 1, 1, 2}, {"xat:fairy_ring", -1, 1, 1, 2}, {"xat:inertia_null_stone", -1, 1, 1, 1},
            {"mowziesmobs:ice_crystal", -1, 1, 1, 1}, {"mowziesmobs:wrought_helmet", -1, 1, 1, 1},
            {"mowziesmobs:barako_mask", -1, 1, 1, 1}, {"mowziesmobs:earth_talisman", -1, 1, 1, 1},
            {"gb:globot3", -1, 1, 3, 3}, {"gb:globot2", -1, 1, 3, 3},
            {"ironapples:diamond_apple", -1, 1, 1, 2}, {"ironapples:iron_apple", -1, 1, 2, 2}
    };

    /** Low-floor rare find (lowFloorRareLootChance). */
    private static final Object[][] RARE_FIND = {
            {"minecraft:diamond", -1, 1, 2, 6}, {"minecraft:emerald", -1, 1, 3, 6}, {"minecraft:golden_apple", -1, 1, 1, 4},
            {"gb:globot", -1, 1, 2, 3}, {"tconstruct:ingots", 0, 1, 1, 3}
    };

    private static final Set<String> MISSING_LOGGED = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>());

    private ModLootInjector() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        try {
            if (ModLootTables.FLOOR_TIER_1_2.equals(name)) {
                addPool(event, "shatteredcitadels_rare_find", RARE_FIND, 1,
                        new LootCondition[]{new RandomChance((float) ConfigCache.get().getLowFloorRareLootChance())}, true);
            } else if (ModLootTables.FLOOR_TIER_3_4.equals(name)) {
                addPool(event, "shatteredcitadels_modded", TIER_3_4, 2, new LootCondition[0], false);
            } else if (ModLootTables.FLOOR_TIER_5_PLUS.equals(name)) {
                addPool(event, "shatteredcitadels_modded", TIER_5_PLUS, 2, new LootCondition[0], false);
            } else if (ModLootTables.UNDERGROUND_CRYPT.equals(name)) {
                addPool(event, "shatteredcitadels_modded", UNDERGROUND, 1, new LootCondition[0], false);
            } else if (ModLootTables.BOSS_CHEST_NORMAL.equals(name)) {
                addPool(event, "shatteredcitadels_modded", BOSS, 2, new LootCondition[0], false);
            } else if (ModLootTables.BOSS_CHEST_SKY.equals(name)) {
                addPool(event, "shatteredcitadels_modded", BOSS_SKY, 3, new LootCondition[0], false);
            } else if (ModLootTables.PANTRY.equals(name)) {
                addPool(event, "shatteredcitadels_modded", PANTRY, 2, new LootCondition[0], false);
            } else if (ModLootTables.BEDROOM.equals(name)) {
                addPool(event, "shatteredcitadels_modded", BEDROOM, 1, new LootCondition[]{new RandomChance(0.35F)}, false);
            } else if (ModLootTables.SMELTERY.equals(name)) {
                addPool(event, "shatteredcitadels_modded", SMELTERY, 3, new LootCondition[0], false);
            } else if (ModLootTables.MAGE_PEDESTAL.equals(name) || ModLootTables.MAGE_PEDESTAL_FOLIO.equals(name)) {
                addPool(event, "shatteredcitadels_modded", MAGE, 1, new LootCondition[]{new RandomChance(0.6F)}, false);
            } else if (net.minecraft.world.storage.loot.LootTableList.CHESTS_STRONGHOLD_LIBRARY.equals(name)
                    || net.minecraft.world.storage.loot.LootTableList.CHESTS_STRONGHOLD_CORRIDOR.equals(name)
                    || net.minecraft.world.storage.loot.LootTableList.CHESTS_STRONGHOLD_CROSSING.equals(name)
                    || net.minecraft.world.storage.loot.LootTableList.CHESTS_DESERT_PYRAMID.equals(name)
                    || net.minecraft.world.storage.loot.LootTableList.CHESTS_JUNGLE_TEMPLE.equals(name)) {
                addPool(event, "shatteredcitadels_modded", UNDERGROUND, 1, new LootCondition[]{new RandomChance(0.5F)}, false);
            } else if (ModLootTables.CATACOMB_DEEP.equals(name)) {
                addPool(event, "shatteredcitadels_modded", UNDERGROUND, 1, new LootCondition[0], false);
                addPool(event, "shatteredcitadels_mage", MAGE, 1, new LootCondition[]{new RandomChance(0.3F)}, false);
            } else if (ModLootTables.VASE_MID.equals(name)) {
                addPool(event, "shatteredcitadels_modded", SMELTERY, 1, new LootCondition[]{new RandomChance(0.25F)}, false);
            } else if (ModLootTables.VASE_HIGH.equals(name)) {
                addPool(event, "shatteredcitadels_modded", TIER_5_PLUS, 1, new LootCondition[]{new RandomChance(0.3F)}, false);
            }
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Could not extend loot table {}; it stays as defined in JSON", name, e);
        }
    }

    private static void addPool(LootTableLoadEvent event, String poolName, Object[][] candidates, int rolls,
                                LootCondition[] conditions, boolean addEnchantedBook) {
        List<LootEntry> entries = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Object[] candidate : candidates) {
            ResourceLocation id = new ResourceLocation((String) candidate[0]);
            int meta = (Integer) candidate[1];
            String entryName = id + (meta >= 0 ? "#" + meta : "");
            Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
            if (item == null || item == Items.AIR) {
                // Worth knowing when the mod is installed: the name is wrong or the item is disabled.
                if (net.minecraftforge.fml.common.Loader.isModLoaded(id.getNamespace()) && MISSING_LOGGED.add(entryName)) {
                    ShatteredCitadelsMod.LOGGER.info("Loot: {} is not registered by {}; skipped", entryName, id.getNamespace());
                }
                continue;
            }
            if (!used.add(entryName)) {
                continue;
            }
            List<LootFunction> functions = new ArrayList<>();
            functions.add(new SetCount(new LootCondition[0], new RandomValueRange((Integer) candidate[2], (Integer) candidate[3])));
            if (meta >= 0) {
                functions.add(new SetMetadata(new LootCondition[0], new RandomValueRange(meta)));
            }
            entries.add(new LootEntryItem(item, (Integer) candidate[4], 0, functions.toArray(new LootFunction[0]),
                    new LootCondition[0], entryName));
        }
        if (addEnchantedBook) {
            // enchant_randomly picks from every registered enchantment, Apotheosis/RadEnchants included.
            entries.add(new LootEntryItem(Items.BOOK, 4, 0, new LootFunction[]{new EnchantRandomly(new LootCondition[0], null)},
                    new LootCondition[0], "shatteredcitadels:enchanted_book"));
        }
        if (entries.isEmpty()) {
            return;
        }
        event.getTable().addPool(new LootPool(entries.toArray(new LootEntry[0]), conditions,
                new RandomValueRange(rolls), new RandomValueRange(0), poolName));
    }
}
