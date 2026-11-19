package com.shatteredcitadels.item;

import baubles.api.BaublesApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;

/**
 * The only class that calls the Baubles API. It is loaded exclusively behind
 * {@link RelicEquipment#isBaublesLoaded()}, so it never resolves without Baubles.
 */
final class BaublesCompat {

    private BaublesCompat() {
    }

    static boolean isEquipped(EntityPlayer player, Item item) {
        return BaublesApi.isBaubleEquipped(player, item) >= 0;
    }
}
