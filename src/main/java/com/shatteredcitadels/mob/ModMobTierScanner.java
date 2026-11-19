package com.shatteredcitadels.mob;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.MobTiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies hostile mobs of every installed mod into tiers 1-5 and an underground pool.
 *
 * <p>Post-init: cheap class-based pre-filter (hostile, living, not a boss/flyer/water/slime).
 * First server world load: each candidate is instantiated once (never spawned) to read its
 * attributes. Every instantiation is isolated: exceptions, linkage errors, null or non-living
 * results skip that mob only. Blacklisted mobs never enter a pool; manual overrides win.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ModMobTierScanner {

    private static final List<EntityEntry> CANDIDATES = new ArrayList<>();
    private static boolean scannedThisSession;

    private ModMobTierScanner() {
    }

    /** Post-init: collect candidate entity types without instantiating anything. */
    public static synchronized void prefilter() {
        CANDIDATES.clear();
        for (EntityEntry entry : ForgeRegistries.ENTITIES.getValuesCollection()) {
            try {
                if (isCandidateClass(entry.getEntityClass())) {
                    CANDIDATES.add(entry);
                }
            } catch (RuntimeException | LinkageError e) {
                ShatteredCitadelsMod.LOGGER.debug("Skipping entity {} during pre-filter", entry.getRegistryName(), e);
            }
        }
        ShatteredCitadelsMod.LOGGER.info("Mob scanner pre-filter: {} hostile candidates", CANDIDATES.size());
    }

    static boolean isCandidateClass(Class<? extends Entity> cls) {
        if (cls == null || Modifier.isAbstract(cls.getModifiers()) || !EntityLiving.class.isAssignableFrom(cls)) {
            return false;
        }
        if (!IMob.class.isAssignableFrom(cls) && !EntityMob.class.isAssignableFrom(cls)) {
            return false;
        }
        return !EntitySlime.class.isAssignableFrom(cls) && !EntityFlying.class.isAssignableFrom(cls)
                && !EntityWaterMob.class.isAssignableFrom(cls) && !EntityGuardian.class.isAssignableFrom(cls)
                && !EntityShulker.class.isAssignableFrom(cls) && !IEntityMultiPart.class.isAssignableFrom(cls)
                && !EntityDragon.class.isAssignableFrom(cls) && !EntityWither.class.isAssignableFrom(cls)
                && !EntityTameable.class.isAssignableFrom(cls);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0 || scannedThisSession) {
            return;
        }
        scan(world);
    }

    /** Server stopped: the next server (integrated) rescans with its own world. */
    public static synchronized void reset() {
        scannedThisSession = false;
    }

    /** Runs the full classification and publishes the result. Never throws. */
    public static synchronized MobTierRegistry scan(World world) {
        scannedThisSession = true;
        long start = System.nanoTime();
        ConfigCache.Snapshot config = ConfigCache.get();
        Map<Integer, List<MobEntry>> tiers = new HashMap<>();
        for (int t = MobTiers.MIN_TIER; t <= MobTiers.MAX_TIER; t++) {
            tiers.put(t, new ArrayList<MobEntry>());
        }
        List<MobEntry> underground = new ArrayList<>();
        Map<ResourceLocation, Integer> manual = config.getManualMobTierOverrides();
        int failed = 0;

        if (config.isAutoModMobScannerEnabled()) {
            for (EntityEntry entry : new ArrayList<>(CANDIDATES)) {
                ResourceLocation id = entry.getRegistryName();
                if (id == null || config.isBlacklisted(id) || manual.containsKey(id)) {
                    continue;
                }
                MobEntry mob = classify(entry, world, -1, false);
                if (mob == null) {
                    failed++;
                    continue;
                }
                add(tiers, underground, mob);
            }
        }
        for (Map.Entry<ResourceLocation, Integer> override : manual.entrySet()) {
            ResourceLocation id = override.getKey();
            if (config.isBlacklisted(id) || !ForgeRegistries.ENTITIES.containsKey(id)) {
                continue;
            }
            EntityEntry entry = ForgeRegistries.ENTITIES.getValue(id);
            MobEntry mob = entry == null ? null : classify(entry, world, MobTiers.clamp(override.getValue()), true);
            if (mob != null) {
                add(tiers, underground, mob);
            }
        }
        addVanillaFallbacks(tiers, underground, config);

        MobTierRegistry registry = new MobTierRegistry(tiers, underground, true);
        MobTierRegistry.publish(registry);
        StringBuilder summary = new StringBuilder();
        for (int t = MobTiers.MIN_TIER; t <= MobTiers.MAX_TIER; t++) {
            summary.append(" T").append(t).append('=').append(registry.getTier(t).size());
        }
        ShatteredCitadelsMod.LOGGER.info("Mob scan finished in {} ms:{} underground={} skipped/failed={}",
                (System.nanoTime() - start) / 1_000_000L, summary, registry.getUnderground().size(), failed);
        return registry;
    }

    /**
     * Instantiates the mob once to read its attributes.
     *
     * @param forcedTier tier from a manual override, or -1 to derive it from the power score
     * @return the classified mob, or null if it cannot be used (exception, null, boss, non-living)
     */
    static MobEntry classify(EntityEntry entry, World world, int forcedTier, boolean manual) {
        ResourceLocation id = entry.getRegistryName();
        try {
            Entity entity = entry.newInstance(world);
            if (!(entity instanceof EntityLiving)) {
                return null;
            }
            EntityLiving living = (EntityLiving) entity;
            if (!manual && !living.isNonBoss()) {
                return null;
            }
            double power = MobPower.score(attribute(living, SharedMonsterAttributes.MAX_HEALTH),
                    attribute(living, SharedMonsterAttributes.ATTACK_DAMAGE),
                    attribute(living, SharedMonsterAttributes.ARMOR));
            int tier = forcedTier > 0 ? forcedTier : MobPower.tierFor(power);
            boolean underground = MobPower.looksUnderground(id.getPath()) || living instanceof EntityCaveSpider
                    || living instanceof EntityPigZombie || living instanceof EntityBlaze
                    || living instanceof EntityWitherSkeleton || living.isImmuneToFire();
            return new MobEntry(id, tier, power, underground, false, manual);
        } catch (VirtualMachineError e) {
            if (e instanceof StackOverflowError) {
                ShatteredCitadelsMod.LOGGER.warn("Mob {} overflowed the stack while being classified; skipped", id);
                return null;
            }
            throw e;
        } catch (Throwable t) {
            ShatteredCitadelsMod.LOGGER.warn("Mob {} could not be classified ({}); skipped", id, t.toString());
            return null;
        }
    }

    private static double attribute(EntityLiving living, IAttribute attribute) {
        IAttributeInstance instance = living.getEntityAttribute(attribute);
        return instance == null ? 0.0D : instance.getAttributeValue();
    }

    private static void add(Map<Integer, List<MobEntry>> tiers, List<MobEntry> underground, MobEntry mob) {
        tiers.get(MobTiers.clamp(mob.getTier())).add(mob);
        if (mob.isUnderground()) {
            underground.add(mob);
        }
    }

    private static void addVanillaFallbacks(Map<Integer, List<MobEntry>> tiers, List<MobEntry> underground,
                                            ConfigCache.Snapshot config) {
        for (int t = MobTiers.MIN_TIER; t <= MobTiers.MAX_TIER; t++) {
            List<MobEntry> list = tiers.get(t);
            if (list.size() >= 2) {
                continue;
            }
            for (String raw : MobTierRegistry.VANILLA_TIERS[t - 1]) {
                ResourceLocation id = new ResourceLocation(raw);
                if (!config.isBlacklisted(id) && ForgeRegistries.ENTITIES.containsKey(id) && !contains(list, id)) {
                    list.add(new MobEntry(id, t, 0.0D, false, true, false));
                }
            }
        }
        if (underground.size() < 3) {
            for (int i = 0; i < MobTierRegistry.VANILLA_UNDERGROUND.length; i++) {
                ResourceLocation id = new ResourceLocation(MobTierRegistry.VANILLA_UNDERGROUND[i]);
                if (!config.isBlacklisted(id) && ForgeRegistries.ENTITIES.containsKey(id) && !contains(underground, id)) {
                    underground.add(new MobEntry(id, MobTiers.clamp(1 + i), 0.0D, true, true, false));
                }
            }
        }
    }

    private static boolean contains(List<MobEntry> list, ResourceLocation id) {
        for (MobEntry entry : list) {
            if (entry.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
