package com.shatteredcitadels.item;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Wearable relic. With Baubles installed it is a bauble (amulet/body/charm) and works only when
 * equipped there; FML strips the IBauble interface and the Baubles methods when Baubles is
 * absent, and the relic then works while carried in the inventory (see {@link RelicEquipment}).
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class ItemRelic extends Item implements IBauble {

    private final RelicKind kind;

    public ItemRelic(RelicKind kind) {
        this.kind = kind;
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.COMBAT);
    }

    public RelicKind getKind() {
        return kind;
    }

    @Override
    @SuppressWarnings("deprecation") // Forge deprecates it for callers; overriding is the 1.12 way
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.valueOf(kind.getBaubleSlot());
    }

    @Optional.Method(modid = "baubles")
    @Override
    public boolean willAutoSync(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        String base = "item.shatteredcitadels." + kind.key() + ".tooltip.";
        for (int i = 0; I18n.hasKey(base + i); i++) {
            tooltip.add(I18n.format(base + i));
        }
        tooltip.add(I18n.format(RelicEquipment.isBaublesLoaded() ? "shatteredcitadels.relic.wear_bauble"
                : "shatteredcitadels.relic.carry"));
    }
}
