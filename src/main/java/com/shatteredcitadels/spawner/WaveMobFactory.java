package com.shatteredcitadels.spawner;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.FloorData;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Random;
import java.util.UUID;

/**
 * Creates, scales, buffs and spawns one wave mob next to a trial spawner. Spawning goes straight
 * through {@link World#spawnEntity(Entity)}, so spawn-control mods (CustomMobSpawner) and
 * spawn-check events cannot veto scripted waves.
 */
public final class WaveMobFactory {

    /** Forge persistent-data key: "citadelUUID|floorIndex". */
    public static final String TAG_FLOOR = "ShatteredCitadelFloor";
    /** Forge persistent-data key: packed position of the owning spawner. */
    public static final String TAG_SPAWNER = "ShatteredCitadelSpawner";
    /** Scoreboard tag on every wave mob (useful for commands). */
    public static final String SCOREBOARD_TAG = "shatteredcitadels_wave";

    private static final UUID HEALTH_MODIFIER = UUID.fromString("5c1a7d2e-3b8f-4e61-9a0d-7f2c4b1e6a01");
    private static final UUID DAMAGE_MODIFIER = UUID.fromString("5c1a7d2e-3b8f-4e61-9a0d-7f2c4b1e6a02");
    private static final int BUFF_DURATION = 1_000_000;
    private static final int SPOT_ATTEMPTS = 16;
    private static final int SPOT_RADIUS = 5;

    private WaveMobFactory() {
    }

    public static String floorTag(UUID citadelId, int floorIndex) {
        return (citadelId == null ? "none" : citadelId.toString()) + "|" + floorIndex;
    }

    /**
     * @param depth floor depth used for scaling (floor index, or |index| for crypt levels)
     * @return the spawned entity, or null if no safe spot or the mob could not be created
     */
    public static EntityLiving spawn(World world, BlockPos spawnerPos, CitadelInstance citadel, FloorData floor,
                                     ResourceLocation mobId, int difficultyTier, int depth, UUID citadelId,
                                     int floorIndex, Random random) {
        if (mobId == null || !ForgeRegistries.ENTITIES.containsKey(mobId)) {
            return null;
        }
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(mobId);
        if (entry == null) {
            return null;
        }
        BlockPos spot = findSpot(world, spawnerPos, citadel, floor, random);
        if (spot == null) {
            return null;
        }
        Entity entity;
        try {
            entity = entry.newInstance(world);
        } catch (VirtualMachineError e) {
            if (e instanceof StackOverflowError) {
                return null;
            }
            throw e;
        } catch (Throwable t) {
            ShatteredCitadelsMod.LOGGER.warn("Wave mob {} could not be created: {}", mobId, t.toString());
            return null;
        }
        if (!(entity instanceof EntityLiving)) {
            return null;
        }
        EntityLiving mob = (EntityLiving) entity;
        mob.setLocationAndAngles(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        AxisAlignedBB box = mob.getEntityBoundingBox();
        if (!world.getCollisionBoxes(mob, box).isEmpty() || world.containsAnyLiquid(box)) {
            return null;
        }
        try {
            mob.onInitialSpawn(world.getDifficultyForLocation(spot), null);
        } catch (VirtualMachineError e) {
            if (!(e instanceof StackOverflowError)) {
                throw e;
            }
        } catch (Throwable t) {
            // Some mods assume natural spawning here; the mob is still usable without it.
            ShatteredCitadelsMod.LOGGER.debug("onInitialSpawn of {} failed: {}", mobId, t.toString());
        }
        mob.enablePersistence();
        mob.getEntityData().setString(TAG_FLOOR, floorTag(citadelId, floorIndex));
        mob.getEntityData().setLong(TAG_SPAWNER, spawnerPos.toLong());
        mob.addTag(SCOREBOARD_TAG);

        ConfigCache.Snapshot config = ConfigCache.get();
        // Vanilla stand-ins on hard floors are reinforced so they keep up with modded tiers.
        boolean reinforced = "minecraft".equals(mobId.getNamespace()) && difficultyTier >= 4;
        double healthScale = config.waveMobHealthScale(depth) * (reinforced ? 1.25D : 1.0D);
        double damageScale = config.waveMobDamageScale(depth) * (reinforced ? 1.15D : 1.0D);
        scale(mob, SharedMonsterAttributes.MAX_HEALTH, HEALTH_MODIFIER, healthScale);
        scale(mob, SharedMonsterAttributes.ATTACK_DAMAGE, DAMAGE_MODIFIER, damageScale);
        mob.setHealth(mob.getMaxHealth());
        applyBuffs(mob, difficultyTier, citadel, floorIndex < 0);

        if (!world.spawnEntity(mob)) {
            return null;
        }
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.FLAME, mob.posX, mob.posY + 0.5D, mob.posZ,
                    12, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        return mob;
    }

    /** @return a position on the spawner's level with a solid floor and two free blocks, or null. */
    static BlockPos findSpot(World world, BlockPos spawnerPos, CitadelInstance citadel, FloorData floor, Random random) {
        int y = spawnerPos.getY();
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            int dx = random.nextInt(SPOT_RADIUS * 2 + 1) - SPOT_RADIUS;
            int dz = random.nextInt(SPOT_RADIUS * 2 + 1) - SPOT_RADIUS;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                continue;
            }
            BlockPos pos = new BlockPos(spawnerPos.getX() + dx, y, spawnerPos.getZ() + dz);
            if (!world.isBlockLoaded(pos) || (citadel != null && !citadel.contains(pos))) {
                continue;
            }
            if (floor != null && (y <= floor.getMinY() || y + 1 > floor.getMaxY())) {
                continue;
            }
            BlockPos below = pos.down();
            IBlockState ground = world.getBlockState(below);
            if (!ground.isSideSolid(world, below, EnumFacing.UP) || isBlocked(world, pos) || isBlocked(world, pos.up())) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private static boolean isBlocked(World world, BlockPos pos) {
        Material material = world.getBlockState(pos).getMaterial();
        return material.blocksMovement() || material.isLiquid() || material == Material.FIRE || material == Material.WEB;
    }

    private static void scale(EntityLiving mob, IAttribute attribute, UUID id, double factor) {
        IAttributeInstance instance = mob.getEntityAttribute(attribute);
        if (instance == null || factor <= 1.0D || instance.getModifier(id) != null) {
            return;
        }
        instance.applyModifier(new AttributeModifier(id, "Shattered Citadel floor scaling", factor - 1.0D, 2));
    }

    private static void applyBuffs(EntityLiving mob, int difficulty, CitadelInstance citadel, boolean underground) {
        if (difficulty >= 3) {
            buff(MobEffects.RESISTANCE, mob, difficulty >= 6 ? 1 : 0);
        }
        if (difficulty >= 4) {
            buff(MobEffects.STRENGTH, mob, 0);
        }
        if (difficulty >= 5) {
            buff(MobEffects.SPEED, mob, 0);
            buff(optional("potioncore", "magic_shield"), mob, 0);
        }
        if (difficulty >= 6) {
            buff(MobEffects.REGENERATION, mob, 0);
        }
        CitadelArchetype archetype = citadel == null ? null : citadel.getArchetype();
        if (archetype == CitadelArchetype.OBSIDIAN_BASTILLE || archetype == CitadelArchetype.TITAN_FORGE || underground) {
            buff(MobEffects.FIRE_RESISTANCE, mob, 0);
        }
        if (archetype == CitadelArchetype.SKY_CITADEL) {
            buff(MobEffects.JUMP_BOOST, mob, 1);
        }
    }

    private static Potion optional(String modid, String name) {
        ResourceLocation id = new ResourceLocation(modid, name);
        return ForgeRegistries.POTIONS.containsKey(id) ? ForgeRegistries.POTIONS.getValue(id) : null;
    }

    private static void buff(Potion potion, EntityLiving mob, int amplifier) {
        if (potion != null && mob.isPotionApplicable(new PotionEffect(potion, BUFF_DURATION, amplifier))) {
            mob.addPotionEffect(new PotionEffect(potion, BUFF_DURATION, amplifier, true, false));
        }
    }

    /** @return true if the entity carries the floor tag of the given floor. */
    public static boolean belongsTo(Entity entity, String floorTag) {
        return entity.getEntityData().getString(TAG_FLOOR).equals(floorTag);
    }
}
