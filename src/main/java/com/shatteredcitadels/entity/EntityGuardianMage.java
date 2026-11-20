package com.shatteredcitadels.entity;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.entity.ai.EntityAIMageCast;
import com.shatteredcitadels.entity.ai.EntityAIMageKite;
import com.shatteredcitadels.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guardian of a catacomb sanctum. Keeps its distance, casts fireball bursts, slowing bolts,
 * warned lightning and skeleton guards, blinks between the sanctum's bookshelf anchors, and once,
 * below half health, shields itself until the four guards of that phase are dead. It never leaves
 * the sanctum and never despawns.
 */
public class EntityGuardianMage extends EntityMob {

    private static final DataParameter<Boolean> SHIELDED = EntityDataManager.createKey(EntityGuardianMage.class, DataSerializers.BOOLEAN);
    public static final String GUARD_TAG = "ShatteredCitadelMageGuard";
    private static final int SHIELD_GUARDS = 4;

    private final BossInfoServer bossInfo = new BossInfoServer(getDisplayName(), BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS);
    private StructureBoundingBox area;
    private final List<BlockPos> anchors = new ArrayList<>();
    private UUID structureId;
    private int floorIndex;
    private boolean shieldUsed;
    private final List<UUID> shieldGuards = new ArrayList<>();
    private final List<UUID> guards = new ArrayList<>();
    private int ticksInMelee;
    private int ticksSinceTeleport;
    private int nextTeleportAt = 200;
    private int lightningCountdown = -1;
    private BlockPos lightningTarget;

    public EntityGuardianMage(World world) {
        super(world);
        setSize(0.6F, 1.95F);
        isImmuneToFire = true;
        experienceValue = 120;
        enablePersistence();
        setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ModItems.GUARDIAN_STAFF != null
                ? new ItemStack(ModItems.GUARDIAN_STAFF) : new ItemStack(Items.BLAZE_ROD));
        setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
    }

    /** Called once by the sanctum sigil when it summons the mage. */
    public void setHomeArea(StructureBoundingBox area, List<BlockPos> anchors, UUID structureId, int floorIndex) {
        this.area = area;
        this.anchors.clear();
        this.anchors.addAll(anchors);
        this.structureId = structureId;
        this.floorIndex = floorIndex;
    }

    public UUID getStructureId() { return structureId; }
    public int getFloorIndex() { return floorIndex; }
    public boolean isShielded() { return dataManager.get(SHIELDED); }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(SHIELDED, Boolean.FALSE);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIMageKite(this, 1.0D, 8.0D, 12.0D));
        tasks.addTask(3, new EntityAIMageCast(this));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(ConfigCache.get().getMageHealth());
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(ConfigCache.get().getMageArmor());
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.28D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6D);
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
        // Levitates down; never takes fall damage.
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

    // ------------------------------------------------------------------ ticking

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote && isShielded()) {
            for (int i = 0; i < 4; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                world.spawnParticle(EnumParticleTypes.SPELL_WITCH, posX + Math.cos(angle) * 1.1D, posY + rand.nextDouble() * 2.0D,
                        posZ + Math.sin(angle) * 1.1D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        bossInfo.setPercent(getHealth() / getMaxHealth());
        ticksSinceTeleport++;
        EntityLivingBase target = getAttackTarget();
        ticksInMelee = target != null && getDistanceSq(target) < 9.0D ? ticksInMelee + 1 : 0;
        if (MageSpellCycle.shouldTeleport(ticksInMelee, ticksSinceTeleport, nextTeleportAt) && target != null) {
            blink();
        }
        if (ticksExisted % 20 == 0 && area != null && !area.isVecInside(getPosition())) {
            blinkTo(anchorNearest((area.minX + area.maxX) / 2, (area.minZ + area.maxZ) / 2));
        }
        if (!shieldUsed && getHealth() < getMaxHealth() / 2.0F) {
            shieldUsed = true;
            shieldGuards.addAll(summonGuards(SHIELD_GUARDS, target));
            dataManager.set(SHIELDED, Boolean.TRUE);
            playSound(SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, 2.0F, 0.8F);
        }
        if (isShielded() && ticksExisted % 10 == 0 && countAlive(shieldGuards) == 0) {
            dataManager.set(SHIELDED, Boolean.FALSE);
            playSound(SoundEvents.BLOCK_GLASS_BREAK, 2.0F, 0.6F);
        }
        tickLightning();
    }

    // ------------------------------------------------------------------ spells

    public int aliveGuards() {
        return countAlive(guards);
    }

    public int maxGuards() {
        return ConfigCache.get().getMageMaxGuards();
    }

    public void cast(MageSpellCycle.Spell spell, EntityLivingBase target) {
        switch (spell) {
            case FIREBALLS:
                double dx = target.posX - posX;
                double dy = target.getEntityBoundingBox().minY + target.height / 2.0F - (posY + height / 2.0F);
                double dz = target.posZ - posZ;
                float spread = MathHelper.sqrt(MathHelper.sqrt(getDistanceSq(target))) * 0.3F;
                for (int i = 0; i < 3; i++) {
                    EntitySmallFireball fireball = new EntitySmallFireball(world, this, dx + rand.nextGaussian() * spread, dy,
                            dz + rand.nextGaussian() * spread);
                    fireball.posY = posY + height / 2.0F + 0.5D;
                    world.spawnEntity(fireball);
                }
                playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5F, 0.8F);
                break;
            case SLOW_BOLT:
                EntityMageBolt bolt = new EntityMageBolt(world, this);
                double bx = target.posX - posX;
                double by = target.posY + target.getEyeHeight() - 0.5D - bolt.posY;
                double bz = target.posZ - posZ;
                bolt.shoot(bx, by, bz, 1.4F, 1.0F);
                world.spawnEntity(bolt);
                playSound(SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, 1.5F, 1.2F);
                break;
            case LIGHTNING:
                lightningTarget = target.getPosition();
                lightningCountdown = ConfigCache.get().getMageLightningWarningTicks();
                playSound(SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK, 1.5F, 1.0F);
                break;
            default:
                guards.addAll(summonGuards(MageSpellCycle.GUARDS_PER_SUMMON, target));
                playSound(SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, 1.5F, 1.0F);
                break;
        }
    }

    /** Warning circle, then a strike that hurts but never sets the library on fire. */
    private void tickLightning() {
        if (lightningCountdown < 0 || lightningTarget == null || !(world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) world;
        if (lightningCountdown > 0) {
            if (lightningCountdown % 4 == 0) {
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2 * i / 16;
                    server.spawnParticle(EnumParticleTypes.SPELL_WITCH, lightningTarget.getX() + 0.5D + Math.cos(angle) * 1.5D,
                            lightningTarget.getY() + 0.1D, lightningTarget.getZ() + 0.5D + Math.sin(angle) * 1.5D, 1, 0, 0, 0, 0.0D);
                }
            }
            lightningCountdown--;
            return;
        }
        lightningCountdown = -1;
        double x = lightningTarget.getX() + 0.5D;
        double z = lightningTarget.getZ() + 0.5D;
        server.addWeatherEffect(new EntityLightningBolt(world, x, lightningTarget.getY(), z, true));
        AxisAlignedBB hit = new AxisAlignedBB(lightningTarget).grow(1.5D, 2.0D, 1.5D);
        for (EntityLivingBase victim : world.getEntitiesWithinAABB(EntityLivingBase.class, hit,
                e -> e != null && e != this && !e.getEntityData().getBoolean(GUARD_TAG))) {
            victim.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, this), 8.0F);
            victim.setFire(3);
        }
    }

    private List<UUID> summonGuards(int count, EntityLivingBase target) {
        List<UUID> summoned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EntitySkeleton guard = new EntitySkeleton(world);
            double angle = Math.PI * 2 * i / count + rand.nextDouble();
            double gx = posX + Math.cos(angle) * 2.5D;
            double gz = posZ + Math.sin(angle) * 2.5D;
            guard.setLocationAndAngles(gx, posY, gz, rand.nextFloat() * 360.0F, 0.0F);
            if (!world.getCollisionBoxes(guard, guard.getEntityBoundingBox()).isEmpty()) {
                guard.setLocationAndAngles(posX, posY, posZ, rand.nextFloat() * 360.0F, 0.0F);
            }
            guard.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            guard.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            guard.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                guard.setDropChance(slot, 0.0F);
            }
            guard.getEntityData().setBoolean(GUARD_TAG, true);
            guard.enablePersistence();
            if (target != null) {
                guard.setAttackTarget(target);
            }
            world.spawnEntity(guard);
            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, guard.posX, guard.posY + 1.0D, guard.posZ,
                        12, 0.3D, 0.6D, 0.3D, 0.01D);
            }
            summoned.add(guard.getUniqueID());
        }
        return summoned;
    }

    private int countAlive(List<UUID> ids) {
        if (!(world instanceof WorldServer)) {
            return 0;
        }
        int alive = 0;
        for (java.util.Iterator<UUID> it = ids.iterator(); it.hasNext(); ) {
            Entity e = ((WorldServer) world).getEntityFromUuid(it.next());
            if (e != null && e.isEntityAlive()) {
                alive++;
            } else {
                it.remove();
            }
        }
        return alive;
    }

    // ------------------------------------------------------------------ teleports

    private void blink() {
        ticksSinceTeleport = 0;
        ticksInMelee = 0;
        int min = ConfigCache.get().getMageTeleportMinTicks();
        int max = ConfigCache.get().getMageTeleportMaxTicks();
        nextTeleportAt = min + rand.nextInt(Math.max(1, max - min + 1));
        if (!anchors.isEmpty()) {
            BlockPos here = getPosition();
            List<BlockPos> options = new ArrayList<>();
            for (BlockPos anchor : anchors) {
                if (anchor.distanceSq(here) > 16.0D) {
                    options.add(anchor);
                }
            }
            blinkTo(options.isEmpty() ? anchors.get(rand.nextInt(anchors.size())) : options.get(rand.nextInt(options.size())));
        } else if (area != null) {
            for (int i = 0; i < 16; i++) {
                double x = area.minX + rand.nextInt(area.maxX - area.minX + 1) + 0.5D;
                double z = area.minZ + rand.nextInt(area.maxZ - area.minZ + 1) + 0.5D;
                if (attemptTeleport(x, posY, z)) {
                    return;
                }
            }
        }
    }

    private BlockPos anchorNearest(int x, int z) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos anchor : anchors) {
            double d = anchor.distanceSq(x, anchor.getY(), z);
            if (d < bestDist) {
                bestDist = d;
                best = anchor;
            }
        }
        return best;
    }

    private void blinkTo(BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 1.0D, posZ, 40, 0.4D, 0.8D, 0.4D, 0.2D);
        }
        world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.8F);
        getNavigator().clearPath();
        setPositionAndUpdate(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ damage & death

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source)) {
            return false;
        }
        Entity attacker = source.getTrueSource();
        if (attacker == this || attacker != null && attacker.getEntityData().getBoolean(GUARD_TAG)) {
            return false;
        }
        if (isShielded() && source != DamageSource.OUT_OF_WORLD) {
            if (!world.isRemote) {
                playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 0.8F);
            }
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!world.isRemote && structureId != null && world instanceof WorldServer) {
            com.shatteredcitadels.structure.MageSanctum.onMageDefeated((WorldServer) world, structureId, floorIndex, getPosition());
        }
    }

    @Override
    protected void dropFewItems(boolean wasRecentlyHit, int lootingModifier) {
        if (ModItems.GUARDIAN_STAFF != null) {
            entityDropItem(new ItemStack(ModItems.GUARDIAN_STAFF), 0.5F);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_EVOCATION_ILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_EVOCATION_ILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOCATION_ILLAGER_DEATH;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (area != null) {
            compound.setTag("MageArea", area.toNBTTagIntArray());
        }
        NBTTagList list = new NBTTagList();
        for (BlockPos anchor : anchors) {
            list.appendTag(NBTUtil.createPosTag(anchor));
        }
        compound.setTag("MageAnchors", list);
        if (structureId != null) {
            compound.setUniqueId("MageStructure", structureId);
        }
        compound.setInteger("MageFloor", floorIndex);
        compound.setBoolean("MageShieldUsed", shieldUsed);
        compound.setBoolean("MageShielded", isShielded());
        NBTTagList shield = new NBTTagList();
        for (UUID id : shieldGuards) {
            shield.appendTag(NBTUtil.createUUIDTag(id));
        }
        compound.setTag("MageShieldGuards", shield);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        int[] box = compound.getIntArray("MageArea");
        area = box.length == 6 ? new StructureBoundingBox(box) : null;
        anchors.clear();
        NBTTagList list = compound.getTagList("MageAnchors", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            anchors.add(NBTUtil.getPosFromTag(list.getCompoundTagAt(i)));
        }
        structureId = compound.hasUniqueId("MageStructure") ? compound.getUniqueId("MageStructure") : null;
        floorIndex = compound.getInteger("MageFloor");
        shieldUsed = compound.getBoolean("MageShieldUsed");
        dataManager.set(SHIELDED, compound.getBoolean("MageShielded"));
        shieldGuards.clear();
        NBTTagList shield = compound.getTagList("MageShieldGuards", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < shield.tagCount(); i++) {
            shieldGuards.add(NBTUtil.getUUIDFromTag(shield.getCompoundTagAt(i)));
        }
        if (hasCustomName()) {
            bossInfo.setName(getDisplayName());
        }
    }
}
