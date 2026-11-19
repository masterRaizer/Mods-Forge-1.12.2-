package com.shatteredcitadels.util;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Side-safe helpers that read combat-relevant stats from any living mob, vanilla or modded.
 * Never throws: unknown or broken entities report neutral values.
 */
public final class EntityStats {

    /** SRG name of {@code EntityLiving#experienceValue} in MCP stable_39. */
    private static final String EXPERIENCE_VALUE_SRG = "field_70728_aV";

    private static volatile Field experienceField;
    private static volatile boolean experienceFieldFailed;

    private EntityStats() {
    }

    /**
     * @return XP dropped on death, or 0 if unavailable. Uses cached reflection: EntityLiving is
     * loaded before late mixins apply, so it cannot be targeted from the late mixin config.
     */
    public static int getExperienceValue(EntityLiving entity) {
        if (entity == null) {
            return 0;
        }
        Field field = resolveExperienceField();
        if (field == null) {
            return 0;
        }
        try {
            return field.getInt(entity);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            return 0;
        }
    }

    /** @return base value of an attribute, or {@code fallback} if the mob does not have it. */
    public static double getBaseAttribute(EntityLiving entity, IAttribute attribute,
                                          double fallback) {
        if (entity == null || attribute == null) {
            return fallback;
        }
        IAttributeInstance instance = entity.getEntityAttribute(attribute);
        return instance == null ? fallback : instance.getBaseValue();
    }

    public static double getMaxHealth(EntityLiving entity) {
        return getBaseAttribute(entity, SharedMonsterAttributes.MAX_HEALTH, 0.0D);
    }

    public static double getAttackDamage(EntityLiving entity) {
        return getBaseAttribute(entity, SharedMonsterAttributes.ATTACK_DAMAGE, 0.0D);
    }

    public static double getArmor(EntityLiving entity) {
        return getBaseAttribute(entity, SharedMonsterAttributes.ARMOR, 0.0D);
    }

    private static Field resolveExperienceField() {
        Field field = experienceField;
        if (field != null || experienceFieldFailed) {
            return field;
        }
        synchronized (EntityStats.class) {
            if (experienceField == null && !experienceFieldFailed) {
                try {
                    experienceField = ObfuscationReflectionHelper.findField(EntityLiving.class, EXPERIENCE_VALUE_SRG);
                } catch (RuntimeException e) {
                    experienceFieldFailed = true;
                    ShatteredCitadelsMod.LOGGER.warn("Cannot access EntityLiving#experienceValue; XP-based mob ranking disabled", e);
                }
            }
            return experienceField;
        }
    }
}
