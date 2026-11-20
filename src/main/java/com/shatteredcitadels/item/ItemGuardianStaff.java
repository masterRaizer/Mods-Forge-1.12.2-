package com.shatteredcitadels.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/** Dropped by the guardian mage: right click hurls a small fireball. */
public class ItemGuardianStaff extends Item {

    public static final int COOLDOWN_TICKS = 60;
    public static final int DURABILITY = 250;

    public ItemGuardianStaff() {
        setMaxStackSize(1);
        setMaxDamage(DURABILITY);
        setCreativeTab(CreativeTabs.COMBAT);
        setFull3D();
    }

    /** The Guardian Folio shortens the cooldown by a quarter. */
    public static int cooldownFor(int base, boolean folio) {
        return folio ? base * 3 / 4 : base;
    }

    public static int cooldownFor(EntityPlayer player) {
        return cooldownFor(COOLDOWN_TICKS, RelicEquipment.isActive(player, ModItems.GUARDIAN_FOLIO));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.getCooldownTracker().hasCooldown(this)) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        if (!world.isRemote) {
            Vec3d look = player.getLookVec();
            EntitySmallFireball fireball = new EntitySmallFireball(world, player, look.x, look.y, look.z);
            fireball.setPosition(player.posX + look.x * 1.2D, player.posY + player.getEyeHeight() - 0.1D + look.y * 1.2D,
                    player.posZ + look.z * 1.2D);
            // A straight, fast shot instead of the blaze's wobble.
            fireball.accelerationX = look.x * 0.1D;
            fireball.accelerationY = look.y * 0.1D;
            fireball.accelerationZ = look.z * 0.1D;
            world.spawnEntity(fireball);
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS,
                    0.8F, 1.0F + world.rand.nextFloat() * 0.2F);
            stack.damageItem(1, player);
        }
        player.getCooldownTracker().setCooldown(this, cooldownFor(player));
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == Items.BLAZE_ROD || super.getIsRepairable(toRepair, repair);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        for (int i = 0; I18n.hasKey("item.shatteredcitadels.guardian_staff.tooltip." + i); i++) {
            tooltip.add(I18n.format("item.shatteredcitadels.guardian_staff.tooltip." + i));
        }
    }
}
