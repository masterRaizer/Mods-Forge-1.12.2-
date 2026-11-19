package com.shatteredcitadels.boss;

import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.mob.MobEntry;
import com.shatteredcitadels.mob.MobTierRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Boss fight logic, ticked by the altar: three phases (100% / &lt;65% / &lt;30% health), abilities
 * announced by a one-second telegraph (particles + sound) before they hit. Nothing here breaks
 * or ignites blocks: lightning is effect-only and meteors are scripted impacts, not entities.
 */
public final class BossAbilityController {

    /** Persistent-data key: world time until which the Arcane Shield is up. */
    public static final String SHIELD_UNTIL = "ShatteredCitadelShieldUntil";
    public static final int MAX_MINIONS = 4;
    static final int TELEGRAPH_TICKS = 20;
    private static final double SHOCKWAVE_RADIUS = 7.0D;
    private static final int SHIELD_TICKS = 100;
    private static final int METEOR_DELAY = 20;

    enum Ability { SHOCKWAVE, BLINK, SUMMON, SHIELD, GRAVITY_STORM }

    private int phase;
    private int cooldown = 60;
    private Ability pending;
    private int telegraph;
    private final List<double[]> meteors = new ArrayList<>();

    /** Restores the phase stored on the boss, so a world reload does not replay phase intros. */
    public void attach(EntityLiving boss) {
        phase = Math.max(1, boss.getEntityData().getInteger(BossTags.PHASE));
    }

    public void reset() {
        pending = null;
        telegraph = 0;
        cooldown = 60;
        meteors.clear();
    }

    public int getPhase() {
        return phase;
    }

    public void tick(WorldServer world, EntityLiving boss, StructureBoundingBox arena, BlockPos altar,
                     List<EntityPlayerMP> players, int tier, boolean sky, ITextComponent bossName) {
        if (phase == 0) {
            attach(boss);
        }
        returnToCenterIfOutside(world, boss, arena, altar);
        if (players.isEmpty()) {
            return;
        }
        EntityLivingBase target = boss.getAttackTarget();
        if (!(target instanceof EntityPlayerMP) || !players.contains(target)) {
            boss.setAttackTarget(nearest(boss, players));
        }
        updatePhase(world, boss, players, bossName);
        tickMeteors(world, boss, players, tier);

        if (pending != null) {
            telegraph--;
            if (telegraph % 5 == 0) {
                telegraphFx(world, boss, pending);
            }
            if (telegraph <= 0) {
                Ability ability = pending;
                pending = null;
                execute(world, boss, arena, altar, players, tier, sky, ability);
                cooldown = phase == 1 ? 100 : (phase == 2 ? 70 : 50);
            }
            return;
        }
        if (--cooldown <= 0) {
            pending = choose(world.rand, sky);
            telegraph = TELEGRAPH_TICKS;
            world.playSound(null, boss.posX, boss.posY, boss.posZ, telegraphSound(pending), SoundCategory.HOSTILE, 1.5F, 0.8F);
        }
    }

    // ------------------------------------------------------------------ phases

    private void updatePhase(WorldServer world, EntityLiving boss, List<EntityPlayerMP> players, ITextComponent name) {
        float ratio = boss.getHealth() / Math.max(1.0F, boss.getMaxHealth());
        int wanted = ratio < 0.30F ? 3 : (ratio < 0.65F ? 2 : 1);
        if (wanted <= phase) {
            return;
        }
        phase = wanted;
        boss.getEntityData().setInteger(BossTags.PHASE, phase);
        if (phase == 2) {
            boss.addPotionEffect(new PotionEffect(MobEffects.SPEED, 1_000_000, 0, true, false));
        } else {
            boss.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 1_000_000, 1, true, false));
        }
        world.playSound(null, boss.posX, boss.posY, boss.posZ, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 1.2F, 0.7F);
        world.spawnParticle(EnumParticleTypes.SPELL_WITCH, boss.posX, boss.posY + 1.0D, boss.posZ, 80, 1.5D, 1.5D, 1.5D, 0.3D);
        ITextComponent line = new TextComponentTranslation("shatteredcitadels.boss.phase" + phase, name.createCopy());
        line.getStyle().setColor(phase == 2 ? TextFormatting.GOLD : TextFormatting.RED).setItalic(true);
        for (EntityPlayerMP player : players) {
            player.sendMessage(line);
        }
        cooldown = Math.min(cooldown, 30);
    }

    private Ability choose(Random random, boolean sky) {
        List<Ability> options = new ArrayList<>();
        options.add(Ability.SHOCKWAVE);
        options.add(Ability.BLINK);
        if (phase >= 2) {
            options.add(Ability.SUMMON);
            options.add(Ability.SHIELD);
            if (sky) {
                options.add(Ability.GRAVITY_STORM);
            }
        }
        if (phase >= 3) {
            options.add(Ability.SHOCKWAVE);
            if (sky) {
                options.add(Ability.GRAVITY_STORM);
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    // ------------------------------------------------------------------ abilities

    private void execute(WorldServer world, EntityLiving boss, StructureBoundingBox arena, BlockPos altar,
                         List<EntityPlayerMP> players, int tier, boolean sky, Ability ability) {
        switch (ability) {
            case SHOCKWAVE:
                shockwave(world, boss, players, tier);
                break;
            case BLINK:
                blink(world, boss, arena, players);
                break;
            case SUMMON:
                summon(world, boss, arena, altar, tier);
                break;
            case SHIELD:
                shield(world, boss, arena, players, tier);
                break;
            case GRAVITY_STORM:
            default:
                gravityStorm(world, boss, arena, players);
                break;
        }
    }

    /** 1) AoE knock-back and damage in a 7-block radius. */
    private void shockwave(WorldServer world, EntityLiving boss, List<EntityPlayerMP> players, int tier) {
        float damage = 4.0F + 1.5F * tier;
        for (EntityPlayerMP player : players) {
            double distance = player.getDistance(boss);
            if (distance > SHOCKWAVE_RADIUS) {
                continue;
            }
            player.attackEntityFrom(DamageSource.causeMobDamage(boss), damage);
            Vec3d away = new Vec3d(player.posX - boss.posX, 0.0D, player.posZ - boss.posZ);
            Vec3d dir = away.lengthSquared() < 1.0E-4D ? new Vec3d(1.0D, 0.0D, 0.0D) : away.normalize();
            double strength = 1.8D * (1.0D - distance / (SHOCKWAVE_RADIUS + 1.0D)) + 0.4D;
            player.motionX += dir.x * strength;
            player.motionY = Math.max(player.motionY, 0.5D);
            player.motionZ += dir.z * strength;
            player.velocityChanged = true;
        }
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2.0D * i / 36.0D;
            for (double r = 1.5D; r <= SHOCKWAVE_RADIUS; r += 1.5D) {
                world.spawnParticle(EnumParticleTypes.CLOUD, boss.posX + Math.cos(angle) * r, boss.posY + 0.2D,
                        boss.posZ + Math.sin(angle) * r, 1, 0.0D, 0.05D, 0.0D, 0.02D);
            }
        }
        world.playSound(null, boss.posX, boss.posY, boss.posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2F, 0.6F);
    }

    /** 2) Teleport behind a player, strictly inside the arena, then strike. */
    private void blink(WorldServer world, EntityLiving boss, StructureBoundingBox arena, List<EntityPlayerMP> players) {
        EntityLivingBase target = boss.getAttackTarget() instanceof EntityPlayerMP ? boss.getAttackTarget()
                : players.get(world.rand.nextInt(players.size()));
        Vec3d look = target.getLookVec();
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        flat = flat.lengthSquared() < 1.0E-4D ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();
        int y = MathHelper.floor(target.posY);
        double[][] offsets = {
                {-flat.x * 2.2D, -flat.z * 2.2D}, {-flat.z * 2.0D, flat.x * 2.0D}, {flat.z * 2.0D, -flat.x * 2.0D},
                {-flat.x * 1.4D, -flat.z * 1.4D}
        };
        for (double[] offset : offsets) {
            double x = MathHelper.floor(target.posX + offset[0]) + 0.5D;
            double z = MathHelper.floor(target.posZ + offset[1]) + 0.5D;
            if (ArenaSpots.isValid(world, boss, arena, x, y, z)) {
                world.spawnParticle(EnumParticleTypes.PORTAL, boss.posX, boss.posY + 1.0D, boss.posZ, 40, 0.5D, 1.0D, 0.5D, 0.5D);
                boss.setPositionAndUpdate(x, y, z);
                boss.faceEntity(target, 180.0F, 180.0F);
                world.spawnParticle(EnumParticleTypes.PORTAL, x, y + 1.0D, z, 40, 0.5D, 1.0D, 0.5D, 0.5D);
                world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.6F);
                try {
                    boss.attackEntityAsMob(target);
                } catch (RuntimeException e) {
                    target.attackEntityFrom(DamageSource.causeMobDamage(boss), 6.0F);
                }
                return;
            }
        }
    }

    /** 3) 2-3 minions, never more than {@value #MAX_MINIONS} alive. */
    private void summon(WorldServer world, EntityLiving boss, StructureBoundingBox arena, BlockPos altar, int tier) {
        int alive = countMinions(world, arena);
        int wanted = Math.min(2 + world.rand.nextInt(2), MAX_MINIONS - alive);
        if (wanted <= 0) {
            return;
        }
        List<MobEntry> combo = MobTierRegistry.get().pickCombo(world.rand, MobTiers.clamp(tier - 1), false, 2);
        int floorY = MathHelper.floor(boss.posY);
        for (int i = 0; i < wanted && countMinions(world, arena) < MAX_MINIONS; i++) {
            EntityLiving minion = createMinion(world, combo.get(world.rand.nextInt(combo.size())));
            if (minion == null) {
                continue;
            }
            double[] spot = ArenaSpots.findAround(world, minion, arena, boss.posX, floorY, boss.posZ, 5, world.rand, 24);
            if (spot == null) {
                continue;
            }
            minion.setLocationAndAngles(spot[0], spot[1], spot[2], world.rand.nextFloat() * 360.0F, 0.0F);
            try {
                minion.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(minion)), null);
            } catch (RuntimeException e) {
                // Optional for summoned minions.
            }
            minion.enablePersistence();
            BossTags.markMinion(minion, null, altar);
            if (world.spawnEntity(minion)) {
                world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, spot[0], spot[1] + 0.5D, spot[2], 20, 0.3D, 0.6D, 0.3D, 0.02D);
            }
        }
    }

    private static EntityLiving createMinion(WorldServer world, MobEntry mob) {
        if (!ForgeRegistries.ENTITIES.containsKey(mob.getId())) {
            return null;
        }
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(mob.getId());
        try {
            Entity entity = entry == null ? null : entry.newInstance(world);
            return entity instanceof EntityLiving ? (EntityLiving) entity : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static int countMinions(WorldServer world, StructureBoundingBox arena) {
        return world.getEntitiesWithinAABB(EntityLiving.class, ArenaSpots.toBox(arena),
                e -> e != null && e.isEntityAlive() && BossTags.isMinion(e)).size();
    }

    /** 4) Arcane Shield: damage reduction + reflection (see the protection handler) and effect-only lightning. */
    private void shield(WorldServer world, EntityLiving boss, StructureBoundingBox arena, List<EntityPlayerMP> players, int tier) {
        boss.getEntityData().setLong(SHIELD_UNTIL, world.getTotalWorldTime() + SHIELD_TICKS);
        world.playSound(null, boss.posX, boss.posY, boss.posZ, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.HOSTILE, 1.5F, 1.2F);
        world.spawnParticle(EnumParticleTypes.END_ROD, boss.posX, boss.posY + 1.0D, boss.posZ, 60, 1.0D, 1.2D, 1.0D, 0.05D);
        int bolts = 2 + world.rand.nextInt(2);
        for (int i = 0; i < bolts; i++) {
            EntityPlayerMP victim = players.get(world.rand.nextInt(players.size()));
            double x = victim.posX + world.rand.nextGaussian();
            double z = victim.posZ + world.rand.nextGaussian();
            // effectOnly = true: no fire, no block damage, no vanilla lightning damage.
            world.addWeatherEffect(new EntityLightningBolt(world, x, victim.posY, z, true));
            for (EntityPlayerMP player : players) {
                if (player.getDistanceSq(x, player.posY, z) <= 2.5D * 2.5D) {
                    player.attackEntityFrom(DamageSource.causeIndirectMagicDamage(boss, boss), 3.0F + tier);
                }
            }
        }
    }

    /** 5) Uber ability of the Citadel Above the Clouds: levitation, pull and meteor impacts. */
    private void gravityStorm(WorldServer world, EntityLiving boss, StructureBoundingBox arena, List<EntityPlayerMP> players) {
        for (EntityPlayerMP player : players) {
            player.addPotionEffect(new PotionEffect(MobEffects.LEVITATION, 30, 1, false, true));
            Vec3d pull = new Vec3d(boss.posX - player.posX, 0.0D, boss.posZ - player.posZ);
            if (pull.lengthSquared() > 1.0E-4D) {
                pull = pull.normalize();
                player.motionX += pull.x * 0.8D;
                player.motionZ += pull.z * 0.8D;
                player.velocityChanged = true;
            }
            int impacts = 2 + world.rand.nextInt(2);
            for (int i = 0; i < impacts; i++) {
                double x = clamp(player.posX + world.rand.nextGaussian() * 2.5D, arena.minX + 1.5D, arena.maxX - 0.5D);
                double z = clamp(player.posZ + world.rand.nextGaussian() * 2.5D, arena.minZ + 1.5D, arena.maxZ - 0.5D);
                meteors.add(new double[]{x, arena.minY + 1.0D, z, METEOR_DELAY});
            }
        }
        world.playSound(null, boss.posX, boss.posY, boss.posZ, SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.HOSTILE, 0.8F, 0.5F);
    }

    private void tickMeteors(WorldServer world, EntityLiving boss, List<EntityPlayerMP> players, int tier) {
        Iterator<double[]> it = meteors.iterator();
        while (it.hasNext()) {
            double[] m = it.next();
            m[3]--;
            if (m[3] > 0) {
                // Telegraph: a falling column of embers above the impact point.
                world.spawnParticle(EnumParticleTypes.FLAME, m[0], m[1] + m[3] * 0.4D, m[2], 3, 0.15D, 0.1D, 0.15D, 0.0D);
                world.spawnParticle(EnumParticleTypes.LAVA, m[0], m[1] + 0.1D, m[2], 1, 0.4D, 0.0D, 0.4D, 0.0D);
                continue;
            }
            it.remove();
            world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, m[0], m[1], m[2], 2, 0.3D, 0.2D, 0.3D, 0.0D);
            world.playSound(null, m[0], m[1], m[2], SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.9F, 1.1F);
            for (EntityPlayerMP player : players) {
                if (player.getDistanceSq(m[0], m[1], m[2]) <= 2.5D * 2.5D) {
                    player.attackEntityFrom(DamageSource.causeMobDamage(boss).setExplosion(), 6.0F + tier);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Pushed out of the arena (or fell): back to the altar, on a validated spot. */
    void returnToCenterIfOutside(WorldServer world, EntityLiving boss, StructureBoundingBox arena, BlockPos altar) {
        if (ArenaSpots.isInside(arena, boss) && boss.posY >= arena.minY + 0.5D) {
            return;
        }
        double x = altar.getX() + 0.5D;
        double z = altar.getZ() + 0.5D;
        int y = altar.getY() + 1;
        double[] spot = ArenaSpots.isValid(world, boss, arena, x, y, z) ? new double[]{x, y, z}
                : ArenaSpots.findAround(world, boss, arena, x, y, z, 6, world.rand, 48);
        if (spot != null) {
            boss.fallDistance = 0.0F;
            boss.setPositionAndUpdate(spot[0], spot[1], spot[2]);
            world.spawnParticle(EnumParticleTypes.PORTAL, spot[0], spot[1] + 1.0D, spot[2], 60, 0.6D, 1.0D, 0.6D, 0.6D);
        }
    }

    private static void telegraphFx(WorldServer world, EntityLiving boss, Ability ability) {
        EnumParticleTypes type;
        switch (ability) {
            case SHOCKWAVE:
                type = EnumParticleTypes.CRIT_MAGIC;
                break;
            case BLINK:
                type = EnumParticleTypes.PORTAL;
                break;
            case SUMMON:
                type = EnumParticleTypes.SMOKE_LARGE;
                break;
            case SHIELD:
                type = EnumParticleTypes.END_ROD;
                break;
            case GRAVITY_STORM:
            default:
                type = EnumParticleTypes.SPELL_WITCH;
                break;
        }
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0D * i / 16.0D;
            world.spawnParticle(type, boss.posX + Math.cos(angle) * 1.5D, boss.posY + 0.3D, boss.posZ + Math.sin(angle) * 1.5D,
                    1, 0.0D, 0.1D, 0.0D, 0.02D);
        }
    }

    private static SoundEvent telegraphSound(Ability ability) {
        switch (ability) {
            case SHOCKWAVE:
                return SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK;
            case BLINK:
                return SoundEvents.ENTITY_ENDERMEN_STARE;
            case SUMMON:
                return SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON;
            case SHIELD:
                return SoundEvents.EVOCATION_ILLAGER_CAST_SPELL;
            case GRAVITY_STORM:
            default:
                return SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE;
        }
    }

    private static EntityPlayerMP nearest(EntityLiving boss, List<EntityPlayerMP> players) {
        EntityPlayerMP best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EntityPlayerMP player : players) {
            double d = player.getDistanceSq(boss);
            if (d < bestDistance) {
                bestDistance = d;
                best = player;
            }
        }
        return best;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
