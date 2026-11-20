package com.shatteredcitadels.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/** The guardian mage's slowing bolt: flies straight, Slowness II for 4 s and a little magic damage. */
public class EntityMageBolt extends EntityThrowable {

    private static final int LIFETIME = 100;

    public EntityMageBolt(World world) {
        super(world);
    }

    public EntityMageBolt(World world, EntityLivingBase thrower) {
        super(world, thrower);
    }

    @Override
    protected float getGravityVelocity() {
        return 0.0F;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            for (int i = 0; i < 2; i++) {
                world.spawnParticle(EnumParticleTypes.SPELL_WITCH, posX, posY, posZ, 0.0D, 0.0D, 0.0D);
            }
        } else if (ticksExisted > LIFETIME) {
            setDead();
        }
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if (world.isRemote) {
            return;
        }
        if (result.entityHit instanceof EntityLivingBase && result.entityHit != getThrower()) {
            EntityLivingBase hit = (EntityLivingBase) result.entityHit;
            hit.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, getThrower()), 2.0F);
            hit.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1));
        }
        world.setEntityState(this, (byte) 3);
        setDead();
    }
}
