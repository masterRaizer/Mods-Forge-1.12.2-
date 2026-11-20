package com.shatteredcitadels.entity.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.Vec3d;

/** Keeps the caster between {@code min} and {@code max} blocks from its target, strafing in between. */
public class EntityAIMageKite extends EntityAIBase {

    private final EntityCreature mage;
    private final double speed;
    private final double minSq;
    private final double maxSq;
    private int strafeTimer;
    private boolean strafeClockwise;

    public EntityAIMageKite(EntityCreature mage, double speed, double min, double max) {
        this.mage = mage;
        this.speed = speed;
        this.minSq = min * min;
        this.maxSq = max * max;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mage.getAttackTarget();
        return target != null && target.isEntityAlive();
    }

    @Override
    public void resetTask() {
        mage.getMoveHelper().strafe(0.0F, 0.0F);
        mage.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = mage.getAttackTarget();
        if (target == null) {
            return;
        }
        mage.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        double distSq = mage.getDistanceSq(target);
        if (distSq < minSq) {
            if (mage.getNavigator().noPath()) {
                Vec3d away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(mage, 10, 4, target.getPositionVector());
                if (away != null) {
                    mage.getNavigator().tryMoveToXYZ(away.x, away.y, away.z, speed * 1.2D);
                }
            }
        } else if (distSq > maxSq) {
            mage.getNavigator().tryMoveToEntityLiving(target, speed);
        } else {
            mage.getNavigator().clearPath();
            if (++strafeTimer >= 30) {
                strafeTimer = 0;
                strafeClockwise = mage.getRNG().nextBoolean();
            }
            mage.getMoveHelper().strafe(0.0F, strafeClockwise ? 0.5F : -0.5F);
            mage.faceEntity(target, 30.0F, 30.0F);
        }
    }
}
