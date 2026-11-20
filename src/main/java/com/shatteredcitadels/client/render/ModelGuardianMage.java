package com.shatteredcitadels.client.render;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/** A biped in a long robe (64x64 texture; robe UV at 0,32); the hat layer is drawn as a hood. */
public class ModelGuardianMage extends ModelBiped {

    public ModelGuardianMage() {
        super(0.0F, 0.0F, 64, 64);
        ModelRenderer robe = new ModelRenderer(this, 0, 32);
        robe.addBox(-4.5F, 10.0F, -2.5F, 9, 13, 5);
        bipedBody.addChild(robe);
    }
}
