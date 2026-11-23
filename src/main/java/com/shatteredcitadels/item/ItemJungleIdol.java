package com.shatteredcitadels.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/** Jungle idol: found in a temple's hidden caches; three of them on the sockets open its vault. */
public class ItemJungleIdol extends Item {

    public ItemJungleIdol() {
        setMaxStackSize(16);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    @SuppressWarnings("deprecation") // Forge deprecates it for callers; overriding is the 1.12 way
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.UNCOMMON;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_GREEN + I18n.format("item.shatteredcitadels.jungle_idol.tooltip"));
    }
}
