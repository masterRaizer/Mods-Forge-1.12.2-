package com.shatteredcitadels.client;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.item.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Item model binding for the trial spawner (client only). */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MODID, value = Side.CLIENT)
public final class ClientModels {

    private ClientModels() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        for (Item item : ModItems.all()) {
            if (item != null) {
                ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
            }
        }
        if (ModBlocks.BOSS_ALTAR_ITEM != null) {
            ModelLoader.setCustomModelResourceLocation(ModBlocks.BOSS_ALTAR_ITEM, 0,
                    new ModelResourceLocation(ModBlocks.BOSS_ALTAR_ITEM.getRegistryName(), "inventory"));
        }
        if (ModBlocks.SEALED_GATE_ITEM != null) {
            ModelLoader.setCustomModelResourceLocation(ModBlocks.SEALED_GATE_ITEM, 0,
                    new ModelResourceLocation(ModBlocks.SEALED_GATE_ITEM.getRegistryName(), "inventory"));
        }
        if (ModBlocks.VASE_ITEM != null) {
            for (BlockVase.Variant variant : BlockVase.Variant.values()) {
                ModelLoader.setCustomModelResourceLocation(ModBlocks.VASE_ITEM, variant.ordinal(),
                        new ModelResourceLocation(ModBlocks.VASE_ITEM.getRegistryName(), "variant=" + variant.getName()));
            }
        }
        for (Item item : new Item[]{ModItems.JUNGLE_IDOL, ModBlocks.IDOL_SOCKET_ITEM, ModBlocks.VAULT_BRICK_ITEM}) {
            if (item != null) {
                ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
            }
        }
        if (ModBlocks.TRIAL_SPAWNER_ITEM != null) {
            ModelLoader.setCustomModelResourceLocation(ModBlocks.TRIAL_SPAWNER_ITEM, 0,
                    new ModelResourceLocation(ModBlocks.TRIAL_SPAWNER_ITEM.getRegistryName(), "inventory"));
        }
    }
}
