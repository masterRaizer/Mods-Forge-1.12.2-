package com.shatteredcitadels.tile;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.mob.MobEntry;
import com.shatteredcitadels.mob.MobTierRegistry;
import com.shatteredcitadels.mob.ModMobTierScanner;
import com.shatteredcitadels.spawner.FloorCombatState;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.WaveMobFactory;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Wave spawner of one citadel floor.
 *
 * <p>Strict floor isolation: every {@value #CHECK_INTERVAL} ticks it asks the floor tracker
 * whether a player stands on <em>its</em> floor; otherwise it does nothing at all. When active
 * it runs 2-4 scripted waves of a per-floor mob combination, respects the per-floor entity cap,
 * tracks its mobs by UUID (with timeouts for unloaded, escaped or stuck mobs so a wave can
 * never hang) and, after the last wave, drops the floor's loot and removes itself.
 */
public class TileEntityTrialSpawner extends TileEntity implements ITickable {

    public static final ResourceLocation ID = new ResourceLocation(Reference.MODID, "trial_spawner");

    static final int CHECK_INTERVAL = 20;
    private static final int ACTIVATION_DELAY = 40;
    private static final int MAX_SPAWNS_PER_CHECK = 2;
    /** Checks (seconds) with a player present but no possible spawn before the wave is skipped. */
    private static final int STALL_LIMIT_CHECKS = 60;
    /** A tracked mob that cannot be found for this long is considered gone (unloaded/removed). */
    static final int MISSING_GRACE_TICKS = 200;
    private static final int STANDALONE_RADIUS = 12;

    // ------------------------------------------------------------------ persistent state
    private UUID citadelId;
    private int floorIndex;
    private int difficultyTier = 1;
    private boolean underground;
    private final List<ResourceLocation> assignedMobCombo = new ArrayList<>();
    private int currentWave;
    private int totalWaves;
    private int cooldownTicks;
    private int toSpawnInWave;
    private boolean waveInProgress;
    private final Map<UUID, TrackedMob> aliveSpawnedMobs = new LinkedHashMap<>();
    private boolean isExhausted;
    private boolean floorResolved;

    // ------------------------------------------------------------------ runtime state
    private int tickCounter;
    private int stallChecks;
    private boolean wasActive;
    private boolean destroyed;
    private boolean errorLogged;

    /** Called by the placer during world generation. No markDirty(): see BlockWriter. */
    public void configure(SpawnerContext context) {
        this.citadelId = context.getStructureId();
        this.floorIndex = context.getFloorIndex();
        this.difficultyTier = context.getTier();
        this.underground = context.getFloorIndex() < 0 || context.getRoomTag() == RoomTag.GOBLIN_DEN
                || context.getRoomTag() == RoomTag.UNDERGROUND_LAIR;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void update() {
        if (world == null || world.isRemote || destroyed) {
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        try {
            check();
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                ShatteredCitadelsMod.LOGGER.error("Trial spawner at {} failed; it will keep retrying", pos, e);
            }
        }
    }

    private void check() {
        if (isExhausted) {
            selfDestruct();
            return;
        }
        CitadelInstance citadel = resolveCitadel();
        FloorData floor = citadel == null ? null : citadel.getFloor(floorIndex);
        boolean active = citadel != null
                ? FloorTrackerHandler.isAnyPlayerOnFloor(world, citadelId, floorIndex)
                : isStandalonePlayerNear();
        if (!active) {
            // Strict floor isolation: a player on another floor never wakes this spawner.
            wasActive = false;
            return;
        }
        if (!wasActive) {
            wasActive = true;
            if (currentWave == 0 && cooldownTicks <= 0) {
                cooldownTicks = ACTIVATION_DELAY;
            }
        }
        if (floor != null && !floorResolved) {
            floorResolved = true;
            difficultyTier = Math.max(difficultyTier, floor.getDifficultyTier());
            markDirty();
        }
        if (totalWaves <= 0) {
            ConfigCache.Snapshot config = ConfigCache.get();
            totalWaves = floor != null && floor.getTotalWaves() > 0 ? floor.getTotalWaves() : config.rollWaveCount(world.rand);
            markDirty();
        }
        if (assignedMobCombo.isEmpty()) {
            assignCombo();
        }
        cleanupTracked(citadel, floor);

        if (waveInProgress) {
            if (toSpawnInWave > 0) {
                spawnSome(citadel, floor);
            }
            if (toSpawnInWave == 0 && aliveSpawnedMobs.isEmpty()) {
                waveInProgress = false;
                ShatteredCitadelsMod.LOGGER.debug("Trial spawner {} finished wave {}/{}", pos, currentWave, totalWaves);
                if (currentWave >= totalWaves) {
                    isExhausted = true;
                    markDirty();
                    selfDestruct();
                    return;
                }
                cooldownTicks = ConfigCache.get().getWaveCooldownTicks();
                markDirty();
                publish(citadel);
            }
        } else if (currentWave >= totalWaves) {
            isExhausted = true;
            markDirty();
            selfDestruct();
        } else if (cooldownTicks <= 0) {
            startWave(citadel);
            spawnSome(citadel, floor);
        } else if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_MOB, pos.getX() + 0.5D, pos.getY() + 1.2D,
                    pos.getZ() + 0.5D, 4, 0.3D, 0.3D, 0.3D, 0.0D);
        }
    }

    private void startWave(CitadelInstance citadel) {
        currentWave++;
        waveInProgress = true;
        stallChecks = 0;
        int players = citadel != null ? FloorTrackerHandler.countPlayersOnFloor(world, citadelId, floorIndex) : 1;
        int extraPlayers = Math.min(3, Math.max(0, players - 1));
        toSpawnInWave = Math.min(12, 2 + currentWave + mobTier() / 2 + extraPlayers);
        world.playSound(null, pos, SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.0F, 0.9F);
        ShatteredCitadelsMod.LOGGER.debug("Trial spawner {} starts wave {}/{} with {} mobs", pos, currentWave, totalWaves,
                toSpawnInWave);
        markDirty();
        publish(citadel);
    }

    private void spawnSome(CitadelInstance citadel, FloorData floor) {
        ConfigCache.Snapshot config = ConfigCache.get();
        int onFloor = countFloorMobs(citadel, floor);
        int budget = Math.min(MAX_SPAWNS_PER_CHECK, Math.min(toSpawnInWave, config.getMaxActiveMobsPerFloor() - onFloor));
        if (budget <= 0) {
            // The entity cap is reached: wait, this is not a stall.
            return;
        }
        int spawned = 0;
        int depth = Math.abs(floorIndex);
        for (int i = 0; i < budget; i++) {
            ResourceLocation mobId = assignedMobCombo.get(world.rand.nextInt(assignedMobCombo.size()));
            EntityLiving mob = WaveMobFactory.spawn(world, pos, citadel, floor, mobId, difficultyTier, depth, citadelId,
                    floorIndex, world.rand);
            if (mob != null) {
                aliveSpawnedMobs.put(mob.getUniqueID(), new TrackedMob(world.getTotalWorldTime()));
                toSpawnInWave--;
                spawned++;
            }
        }
        if (spawned > 0) {
            stallChecks = 0;
            markDirty();
            publish(citadel);
        } else if (++stallChecks >= STALL_LIMIT_CHECKS) {
            ShatteredCitadelsMod.LOGGER.warn("Trial spawner {} could not spawn for {} s; skipping the rest of wave {}",
                    pos, STALL_LIMIT_CHECKS, currentWave);
            toSpawnInWave = 0;
            stallChecks = 0;
            markDirty();
        }
    }

    /**
     * Anti-stall: drops mobs that died without an event, were unloaded or removed, left the floor,
     * got stuck inside blocks or simply lived longer than the configured timeout.
     */
    void cleanupTracked(CitadelInstance citadel, FloorData floor) {
        if (aliveSpawnedMobs.isEmpty() || !(world instanceof WorldServer)) {
            return;
        }
        long now = world.getTotalWorldTime();
        int timeout = ConfigCache.get().getStuckMobTimeoutTicks();
        boolean changed = false;
        Iterator<Map.Entry<UUID, TrackedMob>> it = aliveSpawnedMobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrackedMob> entry = it.next();
            TrackedMob tracked = entry.getValue();
            Entity entity = ((WorldServer) world).getEntityFromUuid(entry.getKey());
            if (entity == null || entity.isDead) {
                if (entity != null || tracked.missingSince >= 0 && now - tracked.missingSince > MISSING_GRACE_TICKS) {
                    it.remove();
                    changed = true;
                } else if (tracked.missingSince < 0) {
                    tracked.missingSince = now;
                }
                continue;
            }
            tracked.missingSince = -1L;
            boolean outside = !isOnOwnFloor(entity, citadel, floor);
            boolean stuck = entity.isEntityInsideOpaqueBlock() || outside;
            if (stuck) {
                if (tracked.stuckSince < 0) {
                    tracked.stuckSince = now;
                }
            } else {
                tracked.stuckSince = -1L;
            }
            boolean stuckTooLong = tracked.stuckSince >= 0 && now - tracked.stuckSince > Math.max(CHECK_INTERVAL * 10, timeout / 4);
            boolean aliveTooLong = now - tracked.spawnedAt > timeout;
            if (stuckTooLong || aliveTooLong) {
                entity.setDead();
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            markDirty();
            publish(citadel);
        }
    }

    private boolean isOnOwnFloor(Entity entity, CitadelInstance citadel, FloorData floor) {
        if (citadel == null || floor == null) {
            return entity.getDistanceSq(pos) <= (STANDALONE_RADIUS + 8) * (STANDALONE_RADIUS + 8);
        }
        int y = MathHelper.floor(entity.posY);
        return citadel.contains(entity.getPosition()) && y >= floor.getMinY() - 1 && y <= floor.getMaxY() + 1;
    }

    private int countFloorMobs(CitadelInstance citadel, FloorData floor) {
        AxisAlignedBB area;
        if (citadel != null && floor != null) {
            StructureBoundingBox b = citadel.getTotalBounds();
            area = new AxisAlignedBB(b.minX, floor.getMinY(), b.minZ, b.maxX + 1, floor.getMaxY() + 1, b.maxZ + 1);
        } else {
            area = new AxisAlignedBB(pos).grow(STANDALONE_RADIUS);
        }
        String tag = WaveMobFactory.floorTag(citadelId, floorIndex);
        return world.getEntitiesWithinAABB(EntityLiving.class, area,
                e -> e != null && e.isEntityAlive() && WaveMobFactory.belongsTo(e, tag)).size();
    }

    private boolean isStandalonePlayerNear() {
        for (EntityPlayer player : world.playerEntities) {
            if (!player.isSpectator() && Math.abs(player.posY - pos.getY()) < 4
                    && player.getDistanceSq(pos) < STANDALONE_RADIUS * STANDALONE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private CitadelInstance resolveCitadel() {
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        if (citadelId != null) {
            return data.getById(citadelId);
        }
        // Placed by an admin or by an older generator: adopt the structure it stands in.
        CitadelInstance citadel = data.getCitadelAt(pos);
        if (citadel != null) {
            FloorData floor = citadel.floorAt(pos.getY());
            if (floor != null) {
                citadelId = citadel.getCitadelId();
                floorIndex = floor.getIndex();
                underground = floorIndex < 0;
                markDirty();
                return citadel;
            }
        }
        return null;
    }

    private void assignCombo() {
        MobTierRegistry registry = MobTierRegistry.get();
        if (!registry.isScanned()) {
            registry = ModMobTierScanner.scan(world);
        }
        long seed = world.getSeed() ^ (citadelId == null ? pos.toLong() : citadelId.getMostSignificantBits()
                ^ citadelId.getLeastSignificantBits()) ^ (floorIndex * 0x9E3779B97F4A7C15L);
        // Same seed for every spawner of the floor: one themed combination per citadel floor.
        Random random = new Random(seed);
        int count = 2 + random.nextInt(3);
        for (MobEntry entry : registry.pickCombo(random, mobTier(), underground, count)) {
            assignedMobCombo.add(entry.getId());
        }
        markDirty();
    }

    private int mobTier() {
        return MobTiers.clamp(difficultyTier);
    }

    private void publish(CitadelInstance citadel) {
        if (citadel != null) {
            FloorCombatState.refresh(world, citadel, floorIndex);
        }
    }

    // ------------------------------------------------------------------ events

    /** Called by the death handler; returns true if the mob belonged to this spawner. */
    public boolean onTrackedMobDeath(UUID mobId) {
        if (aliveSpawnedMobs.remove(mobId) == null) {
            return false;
        }
        markDirty();
        publish(resolveCitadel());
        return true;
    }

    private void selfDestruct() {
        if (destroyed || !(world instanceof WorldServer)) {
            return;
        }
        destroyed = true;
        WorldServer server = (WorldServer) world;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        server.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, x, y, z, 3, 0.4D, 0.4D, 0.4D, 0.0D);
        server.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, 40, 0.6D, 0.6D, 0.6D, 0.1D);
        server.spawnParticle(EnumParticleTypes.END_ROD, x, y, z, 30, 0.4D, 0.8D, 0.4D, 0.05D);
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 0.8F);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6F, 1.4F);
        dropLoot(server);
        CitadelInstance citadel = resolveCitadel();
        ShatteredCitadelsMod.LOGGER.info("Trial spawner {} (floor {}) completed {} waves and dissolved", pos, floorIndex,
                totalWaves);
        world.setBlockToAir(pos);
        publish(citadel);
    }

    private void dropLoot(WorldServer server) {
        int tier = Math.max(1, Math.min(6, difficultyTier));
        LootTable table = server.getLootTableManager().getLootTableFromLocation(lootTable(tier));
        if (table == LootTable.EMPTY_LOOT_TABLE) {
            table = server.getLootTableManager().getLootTableFromLocation(LootTableList.CHESTS_SIMPLE_DUNGEON);
        }
        EntityPlayer nearest = server.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 16.0D, false);
        LootContext.Builder builder = new LootContext.Builder(server);
        if (nearest != null) {
            builder.withLuck(nearest.getLuck());
        }
        List<ItemStack> items = table.generateLootForPools(server.rand, builder.build());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                EntityItem item = new EntityItem(server, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D, stack);
                item.motionX = (server.rand.nextDouble() - 0.5D) * 0.2D;
                item.motionY = 0.25D;
                item.motionZ = (server.rand.nextDouble() - 0.5D) * 0.2D;
                server.spawnEntity(item);
            }
        }
        int xp = 10 * tier + 5 * totalWaves;
        while (xp > 0) {
            int orb = EntityXPOrb.getXPSplit(xp);
            xp -= orb;
            server.spawnEntity(new EntityXPOrb(server, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, orb));
        }
    }

    public static ResourceLocation lootTable(int tier) {
        return new ResourceLocation(Reference.MODID, "spawner/tier_" + Math.max(1, Math.min(6, tier)));
    }

    // ------------------------------------------------------------------ accessors

    public UUID getCitadelId() { return citadelId; }
    public int getFloorIndex() { return floorIndex; }
    public int getDifficultyTier() { return difficultyTier; }
    public List<ResourceLocation> getAssignedMobCombo() { return Collections.unmodifiableList(assignedMobCombo); }
    public int getCurrentWave() { return currentWave; }
    public int getTotalWaves() { return totalWaves; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getAliveSpawnedMobs() { return aliveSpawnedMobs.size(); }
    public boolean isExhausted() { return isExhausted; }
    public boolean isWaveInProgress() { return waveInProgress; }

    /** @return mobs still alive plus mobs still to come in the current wave. */
    public int getRemainingMobs() {
        return aliveSpawnedMobs.size() + Math.max(0, toSpawnInWave);
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (citadelId != null) {
            compound.setUniqueId("citadelId", citadelId);
        }
        compound.setInteger("floorIndex", floorIndex);
        compound.setInteger("difficultyTier", difficultyTier);
        compound.setBoolean("underground", underground);
        NBTTagList combo = new NBTTagList();
        for (ResourceLocation id : assignedMobCombo) {
            combo.appendTag(new NBTTagString(id.toString()));
        }
        compound.setTag("assignedMobCombo", combo);
        compound.setInteger("currentWave", currentWave);
        compound.setInteger("totalWaves", totalWaves);
        compound.setInteger("cooldownTicks", cooldownTicks);
        compound.setInteger("toSpawnInWave", toSpawnInWave);
        compound.setBoolean("waveInProgress", waveInProgress);
        NBTTagList alive = new NBTTagList();
        for (Map.Entry<UUID, TrackedMob> entry : aliveSpawnedMobs.entrySet()) {
            NBTTagCompound mob = new NBTTagCompound();
            mob.setUniqueId("id", entry.getKey());
            mob.setLong("spawnedAt", entry.getValue().spawnedAt);
            alive.appendTag(mob);
        }
        compound.setTag("aliveSpawnedMobs", alive);
        compound.setBoolean("isExhausted", isExhausted);
        compound.setBoolean("floorResolved", floorResolved);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        citadelId = compound.hasUniqueId("citadelId") ? compound.getUniqueId("citadelId") : null;
        floorIndex = compound.getInteger("floorIndex");
        difficultyTier = Math.max(1, compound.getInteger("difficultyTier"));
        underground = compound.getBoolean("underground");
        assignedMobCombo.clear();
        NBTTagList combo = compound.getTagList("assignedMobCombo", Constants.NBT.TAG_STRING);
        for (int i = 0; i < combo.tagCount(); i++) {
            String id = combo.getStringTagAt(i);
            if (id.indexOf(':') > 0) {
                assignedMobCombo.add(new ResourceLocation(id));
            }
        }
        currentWave = Math.max(0, compound.getInteger("currentWave"));
        totalWaves = Math.max(0, compound.getInteger("totalWaves"));
        cooldownTicks = Math.max(0, compound.getInteger("cooldownTicks"));
        toSpawnInWave = Math.max(0, compound.getInteger("toSpawnInWave"));
        waveInProgress = compound.getBoolean("waveInProgress");
        aliveSpawnedMobs.clear();
        NBTTagList alive = compound.getTagList("aliveSpawnedMobs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < alive.tagCount(); i++) {
            NBTTagCompound mob = alive.getCompoundTagAt(i);
            if (mob.hasUniqueId("id")) {
                aliveSpawnedMobs.put(mob.getUniqueId("id"), new TrackedMob(mob.getLong("spawnedAt")));
            }
        }
        isExhausted = compound.getBoolean("isExhausted");
        floorResolved = compound.getBoolean("floorResolved");
    }

    /** Spawn time and anti-stall timestamps of one wave mob. */
    static final class TrackedMob {
        final long spawnedAt;
        long missingSince = -1L;
        long stuckSince = -1L;

        TrackedMob(long spawnedAt) {
            this.spawnedAt = spawnedAt;
        }
    }
}
