package com.shatteredcitadels.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Architect's Blade: very high melee damage; right click releases a spatial rift slash (cone in
 * front of the player, 6 blocks) every 8 seconds that heals the wielder for 25% of the damage
 * dealt. Never touches blocks and never hits other players or their pets.
 */
public class ItemArchitectBlade extends ItemSword {

    public static final ToolMaterial MATERIAL = EnumHelper.addToolMaterial("SHATTEREDCITADELS_ARCHITECT", 4, 4096, 12.0F, 11.0F, 22);
    public static final int COOLDOWN_TICKS = 160;
    private static final double RANGE = 6.0D;
    private static final float SLASH_DAMAGE = 14.0F;
    private static final float LIFESTEAL = 0.25F;

    public ItemArchitectBlade() {
        super(MATERIAL);
        setCreativeTab(CreativeTabs.COMBAT);
    }

    @Override
    @SuppressWarnings("deprecation") // Forge deprecates it for callers; overriding is the 1.12 way
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.getCooldownTracker().hasCooldown(this)) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        player.getCooldownTracker().setCooldown(this, COOLDOWN_TICKS);
        player.swingArm(hand);
        if (!world.isRemote) {
            riftSlash((WorldServer) world, player);
            stack.damageItem(2, player);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    private static void riftSlash(WorldServer world, EntityPlayer player) {
        Vec3d look = player.getLookVec();
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        flat = flat.lengthSquared() < 1.0E-4D ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();
        AxisAlignedBB area = player.getEntityBoundingBox().grow(RANGE, 2.0D, RANGE);
        float dealt = 0.0F;
        for (EntityLivingBase target : world.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            if (target == player || !target.isEntityAlive() || target instanceof EntityPlayer
                    || target instanceof EntityArmorStand
                    || (target instanceof IEntityOwnable && ((IEntityOwnable) target).getOwner() == player)) {
                continue;
            }
            Vec3d to = new Vec3d(target.posX - player.posX, 0.0D, target.posZ - player.posZ);
            double distance = to.length();
            if (distance > RANGE || (distance > 1.0D && to.normalize().dotProduct(flat) < 0.35D)) {
                continue;
            }
            float before = target.getHealth();
            if (target.attackEntityFrom(DamageSource.causePlayerDamage(player), SLASH_DAMAGE)) {
                dealt += Math.max(0.0F, before - target.getHealth());
                target.knockBack(player, 0.6F, -flat.x, -flat.z);
            }
        }
        if (dealt > 0.0F) {
            player.heal(dealt * LIFESTEAL);
        }
        for (int i = -3; i <= 3; i++) {
            double angle = Math.atan2(flat.z, flat.x) + i * 0.25D;
            for (double r = 1.5D; r <= RANGE; r += 1.5D) {
                world.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, player.posX + Math.cos(angle) * r,
                        player.posY + 1.0D, player.posZ + Math.sin(angle) * r, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        world.spawnParticle(EnumParticleTypes.PORTAL, player.posX, player.posY + 1.0D, player.posZ, 40, 1.5D, 0.5D, 1.5D, 0.6D);
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 1.2F, 0.6F);
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT,
                SoundCategory.PLAYERS, 0.6F, 0.5F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        for (int i = 0; I18n.hasKey("item.shatteredcitadels.architect_blade.tooltip." + i); i++) {
            tooltip.add(I18n.format("item.shatteredcitadels.architect_blade.tooltip." + i));
        }
    }
}
