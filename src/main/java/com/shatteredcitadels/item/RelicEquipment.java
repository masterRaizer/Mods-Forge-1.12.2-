package com.shatteredcitadels.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/** Answers "does this player currently benefit from relic X". */
public final class RelicEquipment {

    private static Boolean baublesLoaded;

    private RelicEquipment() {
    }

    public static boolean isBaublesLoaded() {
        if (baublesLoaded == null) {
            baublesLoaded = Loader.isModLoaded("baubles");
        }
        return baublesLoaded;
    }

    /** Baubles installed: equipped in a bauble slot. Otherwise: anywhere in the inventory. */
    public static boolean isActive(EntityPlayer player, Item relic) {
        if (player == null || relic == null) {
            return false;
        }
        if (isBaublesLoaded()) {
            try {
                return BaublesCompat.isEquipped(player, relic);
            } catch (RuntimeException | LinkageError e) {
                return carries(player, relic);
            }
        }
        return carries(player, relic);
    }

    public static boolean carries(EntityPlayer player, Item item) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }
}
