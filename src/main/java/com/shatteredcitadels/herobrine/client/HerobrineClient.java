package com.shatteredcitadels.herobrine.client;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.herobrine.EntityHerobrine;
import com.shatteredcitadels.herobrine.EntityHerobrineApparition;
import com.shatteredcitadels.herobrine.HerobrineContent;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client side of the Herobrine part: the relic model and the two renderers. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MODID, value = Side.CLIENT)
public final class HerobrineClient {

    private HerobrineClient() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        Item eye = HerobrineContent.eye();
        if (eye != null && eye.getRegistryName() != null) {
            ModelLoader.setCustomModelResourceLocation(eye, 0, new ModelResourceLocation(eye.getRegistryName(), "inventory"));
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityHerobrine.class, RenderHerobrine::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityHerobrineApparition.class, RenderHerobrine::new);
    }
}
