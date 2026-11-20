package com.shatteredcitadels.client.render;

import com.shatteredcitadels.entity.EntityMageBolt;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

/** The bolt is drawn by its own particles; the entity itself has no model. */
public class RenderMageBolt extends Render<EntityMageBolt> {

    public RenderMageBolt(RenderManager manager) {
        super(manager);
    }

    @Override
    public void doRender(EntityMageBolt entity, double x, double y, double z, float entityYaw, float partialTicks) {
        // particles only
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityMageBolt entity) {
        return null;
    }
}
