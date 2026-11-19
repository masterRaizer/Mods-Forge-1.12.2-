package com.shatteredcitadels.lore;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.advancement.CustomAdvancementTriggers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Remembers which lore chapters a player has owned (persisted across deaths) and fires the
 * {@code lore_collected} criterion. Works for books from any source: chests, Lootr, trades.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class LoreTracker {

    private static final String COLLECTED = "ShatteredCitadelsLoreChapters";
    private static final int INTERVAL = 40;

    private LoreTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % INTERVAL != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        int mask = collectedMask(player);
        int updated = mask;
        for (ItemStack stack : player.inventory.mainInventory) {
            int chapter = LoreBookGenerator.chapterOf(stack);
            if (chapter > 0) {
                updated |= 1 << (chapter - 1);
            }
        }
        if (updated != mask) {
            NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            persisted.setInteger(COLLECTED, updated);
            player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
            CustomAdvancementTriggers.loreCollected(player, Integer.bitCount(updated));
        }
    }

    public static int collectedMask(EntityPlayer player) {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getInteger(COLLECTED);
    }
}
