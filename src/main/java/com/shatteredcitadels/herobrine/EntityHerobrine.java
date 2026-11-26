package com.shatteredcitadels.herobrine;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import com.shatteredcitadels.advancement.CustomAdvancementTriggers;
import com.shatteredcitadels.boss.ModSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The end-game boss. Fights in melee and steps behind his target; from the second phase on he calls
 * lightning onto a spot marked beforehand, in the third he raises a bounded band of undead and
 * blinds the players around for a moment. Every strong attack is announced, nothing heals him and
 * he is never invulnerable. He owns the one boss bar of the fight.
 */
public class EntityHerobrine extends EntityMob {

    public static final String MINION_TAG = "ShatteredCitadelHerobrineMinion";
    private static final double LIGHTNING_RADIUS = 3.0D;
    private static final int DARKNESS_TICKS = 60;
    private static final int DARKNESS_COOLDOWN = 400;
    private static final double DARKNESS_RANGE = 16.0D;

    private final BossInfoServer bossInfo = new BossInfoServer(getDisplayName(), BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_6);
    private final List<UUID> minions = new ArrayList<>();
    private int phase = 1;
    private int actionCooldown = 80;
    private int darknessCooldown;
    private int lightningCountdown = -1;
    private BlockPos lightningTarget;
    private BlockPos altar;

    public EntityHerobrine(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        isImmuneToFire = true;
        experienceValue = 300;
        enablePersistence();
    }

    /** Remembers the lair altar that summoned him: his death marks that lair as defeated. */
    public void setAltar(BlockPos altar) {
        this.altar = altar == null ? null : altar.toImmutable();
    }

    public BlockPos getAltar() {
        return altar;
    }

    public int getPhase() {
        return phase;
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, true));
        tasks.addTask(5, new EntityAIWanderAvoidWater(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 24.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(HerobrineSettings.health());
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(HerobrineSettings.damage());
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(HerobrineSettings.armor());
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8D);
    }

    @Override
    public boolean isNonBoss() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
        // Never takes fall damage: he teleports a lot.
    }

    @Override
    public void onStruckByLightning(EntityLightningBolt bolt) {
        // His own weapon does not hurt him.
    }

    @Override
    public void onUpdate() {
        if (!world.isRemote && world.getDifficulty() == EnumDifficulty.PEACEFUL && !isDead) {
            // Peaceful removes monsters: the lair gets its altar back instead of being lost for good.
            rearmAltar();
            removeMinions();
        }
        super.onUpdate();
    }

    private void rearmAltar() {
        if (altar == null) {
            return;
        }
        HerobrineSavedData.get(world).rearm(altar);
        if (world.isBlockLoaded(altar) && world.getBlockState(altar).getBlock() == Blocks.OBSIDIAN) {
            world.setBlockState(altar, Blocks.NETHERRACK.getDefaultState());
        }
        altar = null;
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        bossInfo.setPercent(getHealth() / getMaxHealth());
        int now = HerobrinePhases.advance(phase, getHealth() / getMaxHealth());
        if (now != phase) {
            phase = now;
            playSound(ModSounds.HEROBRINE_PHASE, 1.5F, 1.0F);
            particles(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1.0D, posZ, 40, 0.4D, 0.8D, 0.4D, 0.02D);
        }
        if (darknessCooldown > 0) {
            darknessCooldown--;
        }
        tickLightning();
        EntityLivingBase target = getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }
        if (lightningCountdown >= 0) {
            return;
        }
        HerobrineSpellCycle.Action action = HerobrineSpellCycle.next(rand, phase, aliveMinions(),
                HerobrineSettings.maxMinions(), darknessCooldown <= 0);
        switch (action) {
            case LIGHTNING:
                startLightning(target);
                break;
            case SUMMON:
                summonMinions(target);
                break;
            case DARKNESS:
                castDarkness();
                break;
            default:
                teleportBehind(target);
                break;
        }
        actionCooldown = HerobrineSpellCycle.cooldownAfter(action, phase);
    }

    // ------------------------------------------------------------------ actions

    private void particles(EnumParticleTypes type, double x, double y, double z, int count, double dx, double dy, double dz,
                           double speed) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(type, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Steps to a free spot two or three blocks behind the target; stays put when there is none. */
    private void teleportBehind(EntityLivingBase target) {
        Vec3d look = target.getLookVec();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        double bx = length < 1.0E-3D ? 0.0D : -look.x / length;
        double bz = length < 1.0E-3D ? 1.0D : -look.z / length;
        double fromX = posX;
        double fromY = posY;
        double fromZ = posZ;
        for (int attempt = 0; attempt < 6; attempt++) {
            double distance = 2.5D + rand.nextDouble();
            double x = target.posX + bx * distance + (attempt == 0 ? 0.0D : rand.nextDouble() * 4.0D - 2.0D);
            double z = target.posZ + bz * distance + (attempt == 0 ? 0.0D : rand.nextDouble() * 4.0D - 2.0D);
            if (attemptTeleport(x, target.posY + 1.0D, z)) {
                world.playSound(null, fromX, fromY, fromZ, ModSounds.HEROBRINE_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
                playSound(ModSounds.HEROBRINE_TELEPORT, 1.0F, 1.0F);
                getNavigator().clearPath();
                faceEntity(target, 360.0F, 360.0F);
                return;
            }
        }
    }

    private void startLightning(EntityLivingBase target) {
        lightningTarget = new BlockPos(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        lightningCountdown = HerobrineSettings.lightningWarningTicks();
        world.playSound(null, lightningTarget, ModSounds.HEROBRINE_LIGHTNING, SoundCategory.HOSTILE, 2.0F, 1.0F);
        swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
    }

    /** The warning ring, then the bolt: whoever left the ring in time takes nothing. */
    private void tickLightning() {
        if (lightningCountdown < 0 || lightningTarget == null) {
            return;
        }
        double x = lightningTarget.getX() + 0.5D;
        double y = lightningTarget.getY();
        double z = lightningTarget.getZ() + 0.5D;
        if (lightningCountdown > 0) {
            if (lightningCountdown % 4 == 0) {
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2.0D * i / 16.0D;
                    particles(EnumParticleTypes.SPELL_WITCH, x + Math.cos(angle) * LIGHTNING_RADIUS, y + 0.2D,
                            z + Math.sin(angle) * LIGHTNING_RADIUS, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                particles(EnumParticleTypes.CRIT_MAGIC, x, y + 1.5D, z, 6, 0.2D, 1.5D, 0.2D, 0.0D);
            }
            lightningCountdown--;
            return;
        }
        lightningCountdown = -1;
        world.addWeatherEffect(new EntityLightningBolt(world, x, y, z, !HerobrineSettings.griefing()));
        float damage = (float) HerobrineSettings.lightningDamage();
        AxisAlignedBB box = new AxisAlignedBB(x - LIGHTNING_RADIUS, y - 2.0D, z - LIGHTNING_RADIUS,
                x + LIGHTNING_RADIUS, y + 4.0D, z + LIGHTNING_RADIUS);
        for (EntityLivingBase victim : world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (victim == this || victim.getEntityData().getBoolean(MINION_TAG)
                    || victim.getDistanceSq(x, victim.posY, z) > LIGHTNING_RADIUS * LIGHTNING_RADIUS) {
                continue;
            }
            victim.attackEntityFrom(DamageSource.LIGHTNING_BOLT, damage);
        }
        lightningTarget = null;
    }

    private void castDarkness() {
        darknessCooldown = DARKNESS_COOLDOWN;
        playSound(SoundEvents.AMBIENT_CAVE, 2.0F, 0.6F);
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(DARKNESS_RANGE))) {
            if (!player.isSpectator() && !player.isCreative()) {
                player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, DARKNESS_TICKS, 0, false, true));
            }
        }
    }

    private int aliveMinions() {
        if (!(world instanceof WorldServer)) {
            return minions.size();
        }
        int alive = 0;
        for (Iterator<UUID> it = minions.iterator(); it.hasNext(); ) {
            Entity entity = ((WorldServer) world).getEntityFromUuid(it.next());
            if (entity != null && entity.isEntityAlive()) {
                alive++;
            } else {
                it.remove();
            }
        }
        return alive;
    }

    private void summonMinions(EntityLivingBase target) {
        int count = HerobrineSpellCycle.summonCount(aliveMinions(), HerobrineSettings.maxMinions());
        boolean any = false;
        for (int i = 0; i < count; i++) {
            BlockPos spot = minionSpot();
            if (spot == null) {
                continue;
            }
            EntityMob minion = rand.nextBoolean() ? new EntityZombie(world) : new EntitySkeleton(world);
            minion.setLocationAndAngles(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, rand.nextFloat() * 360.0F, 0.0F);
            minion.onInitialSpawn(world.getDifficultyForLocation(spot), null);
            if (minion instanceof EntityZombie) {
                ((EntityZombie) minion).setChild(false);
            }
            // A helmet keeps them from burning by day; nothing they wear is dropped.
            minion.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                minion.setDropChance(slot, 0.0F);
            }
            minion.getEntityData().setBoolean(MINION_TAG, true);
            minion.setAttackTarget(target);
            if (world.spawnEntity(minion)) {
                minions.add(minion.getUniqueID());
                particles(EnumParticleTypes.SMOKE_LARGE, minion.posX, minion.posY + 1.0D, minion.posZ, 12, 0.3D, 0.6D, 0.3D, 0.01D);
                any = true;
            }
        }
        if (any) {
            playSound(ModSounds.HEROBRINE_SUMMON, 1.5F, 1.0F);
        }
    }

    /** @return a free standing place within four blocks, or null */
    private BlockPos minionSpot() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = (int) Math.floor(posX) + rand.nextInt(9) - 4;
            int z = (int) Math.floor(posZ) + rand.nextInt(9) - 4;
            for (int y = (int) Math.floor(posY) + 2; y >= (int) Math.floor(posY) - 3; y--) {
                pos.setPos(x, y, z);
                if (!world.isBlockLoaded(pos)) {
                    break;
                }
                if (world.getBlockState(pos.down()).isSideSolid(world, pos.down(), net.minecraft.util.EnumFacing.UP)
                        && !world.getBlockState(pos).getMaterial().blocksMovement()
                        && !world.getBlockState(pos).getMaterial().isLiquid()
                        && !world.getBlockState(pos.up()).getMaterial().blocksMovement()) {
                    return pos.toImmutable();
                }
            }
        }
        return null;
    }

    private void removeMinions() {
        if (!(world instanceof WorldServer)) {
            return;
        }
        for (UUID id : minions) {
            Entity entity = ((WorldServer) world).getEntityFromUuid(id);
            if (entity instanceof EntityLiving && entity.isEntityAlive()) {
                particles(EnumParticleTypes.SMOKE_LARGE, entity.posX, entity.posY + 1.0D, entity.posZ, 12, 0.3D, 0.6D, 0.3D, 0.01D);
                entity.setDead();
            }
        }
        minions.clear();
    }

    // ------------------------------------------------------------------ death and loot

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!world.isRemote) {
            removeMinions();
            if (altar != null) {
                HerobrineSavedData.get(world).advance(altar, HerobrineSavedData.DEFEATED);
            }
            for (EntityPlayerMP fighter : bossInfo.getPlayers()) {
                CustomAdvancementTriggers.herobrineDefeated(fighter);
            }
        }
    }

    @Override
    protected void dropFewItems(boolean wasRecentlyHit, int lootingModifier) {
        if (HerobrineContent.eye() != null) {
            entityDropItem(new ItemStack(HerobrineContent.eye()), 0.5F);
        }
        entityDropItem(new ItemStack(Items.DIAMOND, 3 + rand.nextInt(3 + Math.max(0, lootingModifier))), 0.5F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HEROBRINE_DEATH;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void addTrackingPlayer(EntityPlayerMP player) {
        super.addTrackingPlayer(player);
        bossInfo.addPlayer(player);
    }

    @Override
    public void removeTrackingPlayer(EntityPlayerMP player) {
        super.removeTrackingPlayer(player);
        bossInfo.removePlayer(player);
    }

    @Override
    public void setCustomNameTag(String name) {
        super.setCustomNameTag(name);
        bossInfo.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("HbPhase", phase);
        compound.setInteger("HbDarkness", darknessCooldown);
        if (altar != null) {
            compound.setLong("HbAltar", altar.toLong());
        }
        NBTTagList list = new NBTTagList();
        for (UUID id : minions) {
            list.appendTag(NBTUtil.createUUIDTag(id));
        }
        compound.setTag("HbMinions", list);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        phase = HerobrinePhases.advance(compound.getInteger("HbPhase"), 1.0F);
        darknessCooldown = Math.max(0, compound.getInteger("HbDarkness"));
        altar = compound.hasKey("HbAltar", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(compound.getLong("HbAltar")) : null;
        minions.clear();
        NBTTagList list = compound.getTagList("HbMinions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            minions.add(NBTUtil.getUUIDFromTag(list.getCompoundTagAt(i)));
        }
        if (hasCustomName()) {
            bossInfo.setName(getDisplayName());
        }
    }
}
