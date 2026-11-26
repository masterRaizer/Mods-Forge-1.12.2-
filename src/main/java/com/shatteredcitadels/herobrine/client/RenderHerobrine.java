package com.shatteredcitadels.herobrine.client;

import com.shatteredcitadels.Reference;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The plain player model in the mod's own skin, with eyes that glow in the dark. */
@SideOnly(Side.CLIENT)
public class RenderHerobrine<T extends EntityLiving> extends RenderBiped<T> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MODID, "textures/entity/herobrine.png");
    private static final ResourceLocation EYES = new ResourceLocation(Reference.MODID, "textures/entity/herobrine_eyes.png");

    public RenderHerobrine(RenderManager manager) {
        super(manager, new ModelPlayer(0.0F, false), 0.5F);
        addLayer(new Eyes());
    }

    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return TEXTURE;
    }

    /** The eyes drawn once more at full brightness, the way spider and enderman eyes are. */
    private final class Eyes implements LayerRenderer<T> {

        @Override
        public void doRenderLayer(T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
            bindTexture(EYES);
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(!entity.isInvisible());
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            getMainModel().render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            int light = entity.getBrightnessForRender();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }
}
