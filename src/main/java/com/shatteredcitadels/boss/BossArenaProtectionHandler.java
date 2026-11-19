package com.shatteredcitadels.boss;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arena anti-grief and boss combat events.
 *
 * <ul>
 *   <li>Explosions: every affected block inside a boss arena is removed from the list (walls
 *       survive blasts from inside or outside; explosion fire is placed only on listed blocks).</li>
 *   <li>Mobs breaking blocks (LivingDestroyBlockEvent) inside an arena are cancelled.</li>
 *   <li>Mob griefing is denied for bosses, minions and any mob inside an arena.</li>
 *   <li>Fake players (quarries, breakers) cannot break arena blocks.</li>
 *   <li>Fire (spread, lightning ignition, fire charges) appearing inside an arena is put out on
 *       the next tick, before it can burn anything.</li>
 * </ul>
 * Bosses take no fall or suffocation damage; the Arcane Shield reduces and reflects damage;
 * the boss's death notifies its altar.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class BossArenaProtectionHandler {

    private static final float SHIELD_DAMAGE_FACTOR = 0.25F;
    private static final float SHIELD_REFLECT_FACTOR = 0.30F;

    /** dimension -> fire positions to extinguish at the end of the tick. */
    private static final Map<Integer, Set<BlockPos>> PENDING_FIRE = new HashMap<>();

    private BossArenaProtectionHandler() {
    }

    /** @return true if the position lies inside any tracked boss arena of the world. */
    public static boolean isProtected(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null || !ConfigCache.get().isStrictArenaAntiGrief()) {
            return false;
        }
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(pos);
        if (citadel == null) {
            return false;
        }
        StructureBoundingBox arena = citadel.getBossArenaBounds();
        return arena != null && arena.isVecInside(pos);
    }

    // ------------------------------------------------------------------ block protection

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (!world.isRemote) {
            event.getAffectedBlocks().removeIf(pos -> isProtected(world, pos));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (isProtected(event.getEntityLiving().world, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.world.isRemote || !ConfigCache.get().isStrictArenaAntiGrief()) {
            return;
        }
        if (BossTags.isBoss(entity) || BossTags.isMinion(entity) || isProtected(entity.world, entity.getPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof FakePlayer && isProtected(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world.isRemote || event.getState().getMaterial() != Material.FIRE || !isProtected(world, event.getPos())) {
            return;
        }
        Set<BlockPos> pending = PENDING_FIRE.get(world.provider.getDimension());
        if (pending == null) {
            pending = new LinkedHashSet<>();
            PENDING_FIRE.put(world.provider.getDimension(), pending);
        }
        pending.add(event.getPos().toImmutable());
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        Set<BlockPos> pending = PENDING_FIRE.remove(event.world.provider.getDimension());
        if (pending == null) {
            return;
        }
        for (BlockPos pos : pending) {
            if (event.world.isBlockLoaded(pos) && event.world.getBlockState(pos).getMaterial() == Material.FIRE) {
                event.world.setBlockToAir(pos);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            PENDING_FIRE.remove(event.getWorld().provider.getDimension());
        }
    }

    // ------------------------------------------------------------------ boss combat

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (BossTags.isBoss(event.getEntityLiving()) || BossTags.isMinion(event.getEntityLiving())) {
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
        }
    }

    /** Minions never fight their boss (stray arrows, revenge targeting) and vice versa. */
    @SubscribeEvent
    public static void onSetTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        EntityLivingBase target = event.getTarget();
        if (target == null || entity.world.isRemote || !(entity instanceof EntityLiving)) {
            return;
        }
        boolean entitySide = BossTags.isBoss(entity) || BossTags.isMinion(entity);
        boolean targetSide = BossTags.isBoss(target) || BossTags.isMinion(target);
        if (entitySide && targetSide) {
            ((EntityLiving) entity).setAttackTarget(null);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        Entity source = event.getSource().getTrueSource();
        if (!entity.world.isRemote && (BossTags.isBoss(entity) || BossTags.isMinion(entity))
                && (BossTags.isBoss(source) || BossTags.isMinion(source)) && source != entity) {
            event.setCanceled(true);
            return;
        }
        if (!entity.world.isRemote && BossTags.isBoss(entity)
                && (event.getSource() == DamageSource.IN_WALL || event.getSource() == DamageSource.FALL
                || event.getSource() == DamageSource.CRAMMING || event.getSource() == DamageSource.DROWN)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        EntityLivingBase boss = event.getEntityLiving();
        if (boss.world.isRemote || !BossTags.isBoss(boss)) {
            return;
        }
        long shieldUntil = boss.getEntityData().getLong(BossAbilityController.SHIELD_UNTIL);
        if (boss.world.getTotalWorldTime() > shieldUntil) {
            return;
        }
        float original = event.getAmount();
        event.setAmount(original * SHIELD_DAMAGE_FACTOR);
        Entity attacker = event.getSource().getTrueSource();
        if (attacker instanceof EntityLivingBase && attacker != boss && !BossTags.isMinion(attacker)
                && !"thorns".equals(event.getSource().getDamageType())) {
            // Never reflect thorns damage: avoids ping-pong with Thorns armour.
            attacker.attackEntityFrom(DamageSource.causeThornsDamage(boss), original * SHIELD_REFLECT_FACTOR);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || !BossTags.isBoss(entity)) {
            return;
        }
        BlockPos altar = BossTags.altarOf(entity);
        if (altar == null || !entity.world.isBlockLoaded(altar)) {
            return;
        }
        TileEntity tile = entity.world.getTileEntity(altar);
        if (tile instanceof TileEntityBossAltar) {
            ((TileEntityBossAltar) tile).onBossDefeated(entity);
        }
    }

    /** Test/diagnostic helper: positions still waiting to be extinguished. */
    static List<BlockPos> pendingFire(int dimension) {
        Set<BlockPos> pending = PENDING_FIRE.get(dimension);
        return pending == null ? new ArrayList<BlockPos>() : new ArrayList<>(pending);
    }
}
