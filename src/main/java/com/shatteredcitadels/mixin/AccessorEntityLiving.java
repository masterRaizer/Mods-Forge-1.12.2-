package com.shatteredcitadels.mixin;

import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the protected XP reward of a living mob. Used by the mod-mob scanner to
 * rank entities from installed mods. Callers must go through
 * {@link com.shatteredcitadels.util.EntityStats}, which falls back to reflection if this
 * non-required mixin could not be applied.
 */
@Mixin(EntityLiving.class)
public interface AccessorEntityLiving {

    @Accessor("experienceValue")
    int shatteredcitadels$getExperienceValue();
}
