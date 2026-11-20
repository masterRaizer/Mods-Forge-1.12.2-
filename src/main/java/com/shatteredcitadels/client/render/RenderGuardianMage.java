package com.shatteredcitadels.client.render;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.entity.EntityGuardianMage;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderGuardianMage extends RenderBiped<EntityGuardianMage> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MODID, "textures/entity/guardian_mage.png");

    public RenderGuardianMage(RenderManager manager) {
        super(manager, new ModelGuardianMage(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityGuardianMage entity) {
        return TEXTURE;
    }
}
