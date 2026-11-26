package com.shatteredcitadels.herobrine;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The figure that watches from afar. It stands still, looks at the nearest player and is gone as soon
 * as the player comes close, looks at it for a moment or hits it. It does no harm, changes no block,
 * is never written to disk and lives half a minute at most.
 */
public class EntityHerobrineApparition extends EntityLiving {

    private static final double WATCH_RANGE = 96.0D;
    private int age;
    private int lookedAtTicks;

    public EntityHerobrineApparition(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        isImmuneToFire = true;
        experienceValue = 0;
        setSilent(true);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote) {
            return;
        }
        age++;
        EntityPlayer player = world.getClosestPlayerToEntity(this, WATCH_RANGE);
        double distance = Double.MAX_VALUE;
        if (player != null) {
            distance = getDistance(player);
            faceEntity(player, 360.0F, 360.0F);
            rotationYawHead = rotationYaw;
            renderYawOffset = rotationYaw;
            Vec3d toFigure = new Vec3d(posX - player.posX, posY + getEyeHeight() - (player.posY + player.getEyeHeight()),
                    posZ - player.posZ).normalize();
            boolean seen = ApparitionRule.isLookedAt(player.getLookVec().dotProduct(toFigure), distance)
                    && player.canEntityBeSeen(this);
            lookedAtTicks = seen ? lookedAtTicks + 1 : 0;
        }
        if (player == null || ApparitionRule.shouldVanish(age, distance, lookedAtTicks)) {
            vanish();
        }
    }

    private void vanish() {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL, posX, posY + 1.0D, posZ, 10, 0.2D, 0.6D, 0.2D, 0.0D);
        }
        setDead();
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!world.isRemote && source != DamageSource.OUT_OF_WORLD) {
            vanish();
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithNearbyEntities() {
        // Pushes nobody.
    }

    @Override
    protected boolean canDespawn() {
        return true;
    }

    @Override
    protected boolean canDropLoot() {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        // Never saved: a figure left in an unloading chunk is simply gone.
        return false;
    }

    @Override
    public boolean writeToNBTAtomically(NBTTagCompound compound) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }
}
