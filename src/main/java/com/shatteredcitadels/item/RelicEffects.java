package com.shatteredcitadels.item;

import com.shatteredcitadels.Reference;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;

/**
 * Server-side effects of the relics. Attribute modifiers and flight are granted while a relic is
 * active and removed again when it is not, so unequipping never leaves stale bonuses behind.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class RelicEffects {

    private static final UUID CROWN_DAMAGE = UUID.fromString("c7a1f3e2-5b6d-4a8c-9e0f-1d2c3b4a5f61");
    private static final UUID CROWN_SPEED = UUID.fromString("c7a1f3e2-5b6d-4a8c-9e0f-1d2c3b4a5f62");
    private static final String AEGIS_FLIGHT = "ShatteredCitadelsAegisFlight";
    private static final String AEGIS_BOLT = "ShatteredCitadelsAegisBolt";
    private static final int AEGIS_BOLT_COOLDOWN = 400;
    private static final float AEGIS_DAMAGE_FACTOR = 0.6F;
    private static final float AEGIS_BOLT_DAMAGE = 8.0F;
    private static final int TICK_INTERVAL = 10;
    private static final int REPAIR_INTERVAL = 40;

    private RelicEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % TICK_INTERVAL != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (ModItems.CROWN_SHARD == null) {
            return;
        }
        boolean crown = RelicEquipment.isActive(player, ModItems.CROWN_SHARD);
        modifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, CROWN_DAMAGE, "Crown Shard damage", 0.30D, crown);
        modifier(player, SharedMonsterAttributes.ATTACK_SPEED, CROWN_SPEED, "Crown Shard speed", 0.40D, crown);
        if (crown) {
            player.removePotionEffect(MobEffects.SLOWNESS);
            player.removePotionEffect(MobEffects.WITHER);
        }
        updateFlight(player, RelicEquipment.isActive(player, ModItems.CLOUDSPIRE_AEGIS));
        if (RelicEquipment.isActive(player, ModItems.GOBLIN_FORGE_HEART)) {
            player.addPotionEffect(new PotionEffect(MobEffects.HASTE, 220, 2, true, false));
            if (player.ticksExisted % REPAIR_INTERVAL == 0) {
                repairAll(player);
            }
        }
    }

    /** Operation 1 = multiply base: +30% damage, +40% attack speed. */
    private static void modifier(EntityPlayer player, IAttribute attribute, UUID id, String name, double amount, boolean active) {
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (active && existing == null) {
            instance.applyModifier(new AttributeModifier(id, name, amount, 1).setSaved(false));
        } else if (!active && existing != null) {
            instance.removeModifier(id);
        }
    }

    /** Creative-style flight while the Aegis is active; revoked only if the Aegis granted it. */
    private static void updateFlight(EntityPlayerMP player, boolean aegis) {
        NBTTagCompound data = player.getEntityData();
        boolean granted = data.getBoolean(AEGIS_FLIGHT);
        if (aegis && !player.capabilities.allowFlying) {
            player.capabilities.allowFlying = true;
            data.setBoolean(AEGIS_FLIGHT, true);
            player.sendPlayerAbilities();
        } else if (!aegis && granted) {
            data.removeTag(AEGIS_FLIGHT);
            if (!player.isCreative() && !player.isSpectator()) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.fallDistance = 0.0F;
                player.sendPlayerAbilities();
            }
        }
    }

    /** One durability point per item every 2 seconds: armour, off-hand and the whole inventory. */
    private static void repairAll(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            repair(stack);
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            repair(stack);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            repair(stack);
        }
    }

    private static void repair(ItemStack stack) {
        if (!stack.isEmpty() && stack.isItemStackDamageable() && stack.isItemDamaged()) {
            stack.setItemDamage(Math.max(0, stack.getItemDamage() - 1));
        }
    }

    @SubscribeEvent
    public static void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer) || event.getEntityLiving().world.isRemote
                || ModItems.CROWN_SHARD == null) {
            return;
        }
        Potion potion = event.getPotionEffect().getPotion();
        if ((potion == MobEffects.SLOWNESS || potion == MobEffects.WITHER)
                && RelicEquipment.isActive((EntityPlayer) event.getEntityLiving(), ModItems.CROWN_SHARD)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer) || event.getEntityLiving().world.isRemote
                || ModItems.CLOUDSPIRE_AEGIS == null) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (!RelicEquipment.isActive(player, ModItems.CLOUDSPIRE_AEGIS)) {
            return;
        }
        event.setAmount(event.getAmount() * AEGIS_DAMAGE_FACTOR);
        Entity attacker = event.getSource().getTrueSource();
        long now = player.world.getTotalWorldTime();
        if (attacker instanceof EntityLivingBase && attacker != player && attacker.isEntityAlive()
                && now - player.getEntityData().getLong(AEGIS_BOLT) >= AEGIS_BOLT_COOLDOWN) {
            player.getEntityData().setLong(AEGIS_BOLT, now);
            // effectOnly = true: visual only, no fire, never breaks or ignites blocks.
            player.world.addWeatherEffect(new EntityLightningBolt(player.world, attacker.posX, attacker.posY, attacker.posZ, true));
            attacker.attackEntityFrom(DamageSource.LIGHTNING_BOLT, AEGIS_BOLT_DAMAGE);
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        EntityPlayer killer = event.getAttackingPlayer();
        if (killer != null && !killer.world.isRemote && ModItems.GOBLIN_FORGE_HEART != null
                && RelicEquipment.isActive(killer, ModItems.GOBLIN_FORGE_HEART)) {
            event.setDroppedExperience(event.getDroppedExperience() * 2);
        }
    }
}
