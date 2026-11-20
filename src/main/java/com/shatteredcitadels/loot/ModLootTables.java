package com.shatteredcitadels.loot;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.lore.LootFunctionLoreBook;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;

/**
 * Every chest of the mod gets one of these tables through
 * {@code TileEntityLockableLoot.setLootTable(...)}. Because the loot is generated on first open
 * from the table, Lootr turns these chests into per-player chests automatically.
 */
public final class ModLootTables {

    public static final ResourceLocation FLOOR_TIER_1_2 = id("chests/floor_tier_1_2");
    public static final ResourceLocation FLOOR_TIER_3_4 = id("chests/floor_tier_3_4");
    public static final ResourceLocation FLOOR_TIER_5_PLUS = id("chests/floor_tier_5_plus");
    public static final ResourceLocation UNDERGROUND_CRYPT = id("chests/underground_crypt");
    public static final ResourceLocation BOSS_CHEST_NORMAL = id("chests/boss_chest_normal");
    public static final ResourceLocation BOSS_CHEST_SKY = id("chests/boss_chest_sky_citadel");
    public static final ResourceLocation PANTRY = id("chests/pantry");
    public static final ResourceLocation BEDROOM = id("chests/bedroom");
    public static final ResourceLocation SMELTERY = id("chests/smeltery");
    public static final ResourceLocation CATACOMB_DEEP = id("chests/catacomb_deep");
    public static final ResourceLocation MAGE_PEDESTAL = id("chests/mage_pedestal");
    public static final ResourceLocation MAGE_PEDESTAL_FOLIO = id("chests/mage_pedestal_folio");
    public static final ResourceLocation VASE_LOW = id("vases/low");
    public static final ResourceLocation VASE_MID = id("vases/mid");
    public static final ResourceLocation VASE_HIGH = id("vases/high");

    private static boolean registered;

    private ModLootTables() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Reference.MODID, path);
    }

    /** Pre-init: the custom loot function must exist before any table is parsed. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        LootFunctionManager.registerFunction(new LootFunctionLoreBook.Serializer());
        for (ResourceLocation table : new ResourceLocation[]{FLOOR_TIER_1_2, FLOOR_TIER_3_4, FLOOR_TIER_5_PLUS,
                UNDERGROUND_CRYPT, CATACOMB_DEEP, MAGE_PEDESTAL, MAGE_PEDESTAL_FOLIO, BOSS_CHEST_NORMAL, BOSS_CHEST_SKY, PANTRY, BEDROOM, SMELTERY,
                VASE_LOW, VASE_MID, VASE_HIGH}) {
            LootTableList.register(table);
        }
    }

    /** @param difficulty floor difficulty 1-6 */
    public static ResourceLocation forFloor(int difficulty) {
        return difficulty <= 2 ? FLOOR_TIER_1_2 : difficulty <= 4 ? FLOOR_TIER_3_4 : FLOOR_TIER_5_PLUS;
    }

    /** @param difficulty floor difficulty 1-6, plus 2 for gilded vases */
    public static ResourceLocation forVase(int difficulty) {
        return difficulty <= 2 ? VASE_LOW : difficulty <= 4 ? VASE_MID : VASE_HIGH;
    }
}
