package com.shatteredcitadels.client;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.block.ModBlocks;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
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
        if (ModBlocks.BOSS_ALTAR_ITEM != null) {
            ModelLoader.setCustomModelResourceLocation(ModBlocks.BOSS_ALTAR_ITEM, 0,
                    new ModelResourceLocation(ModBlocks.BOSS_ALTAR_ITEM.getRegistryName(), "inventory"));
        }
        if (ModBlocks.TRIAL_SPAWNER_ITEM != null) {
            ModelLoader.setCustomModelResourceLocation(ModBlocks.TRIAL_SPAWNER_ITEM, 0,
                    new ModelResourceLocation(ModBlocks.TRIAL_SPAWNER_ITEM.getRegistryName(), "inventory"));
        }
    }
}
