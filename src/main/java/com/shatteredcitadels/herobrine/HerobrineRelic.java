package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.item.RelicEquipment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The Eye of Herobrine, the relic the boss drops: sight in the dark, no blindness, and harder blows
 * against the undead.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class HerobrineRelic {

    static final float UNDEAD_FACTOR = 1.25F;
    private static final int TICK_INTERVAL = 40;

    private HerobrineRelic() {
    }

    /** @return the damage of a blow after the relic had its say */
    static float damage(float amount, boolean wearerHasRelic, boolean victimUndead) {
        return wearerHasRelic && victimUndead ? amount * UNDEAD_FACTOR : amount;
    }

    private static boolean wears(EntityPlayer player) {
        Item eye = HerobrineContent.eye();
        return eye != null && RelicEquipment.isActive(player, eye);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || !(event.player instanceof EntityPlayerMP)
                || event.player.ticksExisted % TICK_INTERVAL != 0 || !wears(event.player)) {
            return;
        }
        event.player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 300, 0, true, false));
        event.player.removePotionEffect(MobEffects.BLINDNESS);
    }

    @SubscribeEvent
    public static void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer && !event.getEntityLiving().world.isRemote
                && event.getPotionEffect().getPotion() == MobEffects.BLINDNESS
                && wears((EntityPlayer) event.getEntityLiving())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getTrueSource();
        if (attacker instanceof EntityPlayer && !attacker.world.isRemote) {
            boolean undead = event.getEntityLiving().getCreatureAttribute() == EnumCreatureAttribute.UNDEAD;
            if (undead) {
                event.setAmount(damage(event.getAmount(), wears((EntityPlayer) attacker), true));
            }
        }
    }
}
