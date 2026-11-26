package com.shatteredcitadels.tile;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.boss.ArenaSpots;
import com.shatteredcitadels.boss.BossAbilityController;
import com.shatteredcitadels.boss.BossCutsceneHandler;
import com.shatteredcitadels.boss.BossFactory;
import com.shatteredcitadels.boss.BossTags;
import com.shatteredcitadels.boss.CitadelBossDefeatedEvent;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.SealedGates;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Boss altar in the centre of a boss arena. DORMANT until a player enters the arena; then the
 * entrance cutscene (first visit, if enabled) or the proximity trigger; ACTIVE while the boss
 * lives (boss bar, abilities); DEFEATED after the kill (trophy chest placed once).
 *
 * <p>The boss bar is transient: after a world reload it is rebuilt on the first tick from the
 * persisted boss UUID, so the bar always matches the living boss.
 */
public class TileEntityBossAltar extends TileEntity implements ITickable {

    public static final ResourceLocation ID = new ResourceLocation(Reference.MODID, "boss_altar");

    public enum State { DORMANT, CUTSCENE, ACTIVE, DEFEATED }

    private static final int SLOW_INTERVAL = 10;
    private static final int RETRY_TICKS = 100;
    private static final int BOSS_LOST_TICKS = 600;
    private static final int EMPTY_ARENA_RESET_TICKS = 1200;
    private static final int CUTSCENE_SAFETY_MARGIN = 200;

    // persistent
    private UUID citadelId;
    private State state = State.DORMANT;
    private UUID bossUuid;
    private long cutsceneStartedAt;
    /** Players who died in the arena during the current fight (for the "flawless" advancement). */
    private final Set<UUID> fallenPlayers = new HashSet<>();

    // runtime
    private BossInfoServer bossInfo;
    private final BossAbilityController controller = new BossAbilityController();
    private int tickCounter;
    private long bossMissingSince = -1L;
    private long arenaEmptySince = -1L;
    private long nextSpawnAttempt;
    private boolean errorLogged;

    /** World generation: no markDirty() (see BlockWriter). */
    public void configure(UUID citadelId) {
        this.citadelId = citadelId;
    }

    public State getState() { return state; }
    public UUID getCitadelId() { return citadelId; }
    public UUID getBossUuid() { return bossUuid; }
    public boolean hasBossBar() { return bossInfo != null; }

    // ------------------------------------------------------------------ ticking

    @Override
    public void update() {
        if (world == null || world.isRemote || !(world instanceof WorldServer)) {
            return;
        }
        tickCounter++;
        try {
            if (state == State.ACTIVE) {
                tickActive((WorldServer) world);
            } else if (state != State.DEFEATED && tickCounter % SLOW_INTERVAL == 0) {
                tickWaiting((WorldServer) world);
            }
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                ShatteredCitadelsMod.LOGGER.error("Boss altar at {} failed; it keeps retrying", pos, e);
            }
        }
    }

    private void tickWaiting(WorldServer server) {
        CitadelInstance citadel = resolveCitadel();
        if (citadel == null || citadel.getBossArenaBounds() == null) {
            return;
        }
        FloorData top = citadel.getBossFloor();
        if (top != null && top.isBossDefeated()) {
            setState(State.DEFEATED);
            return;
        }
        List<EntityPlayerMP> players = playersInArena(server, citadel.getBossArenaBounds());
        ConfigCache.Snapshot config = ConfigCache.get();
        if (state == State.CUTSCENE) {
            // The boss never appears while a cutscene of this citadel is still running.
            if (BossCutsceneHandler.hasActiveCutscene(citadelId)) {
                if (server.getTotalWorldTime() - cutsceneStartedAt > config.getBossCutsceneTicks() + CUTSCENE_SAFETY_MARGIN) {
                    BossCutsceneHandler.cancelAll(citadelId);
                }
                return;
            }
            if (players.isEmpty()) {
                setState(State.DORMANT);
                return;
            }
            trySpawnBoss(server, citadel, players);
            return;
        }
        if (players.isEmpty()) {
            return;
        }
        if (config.isBossCutsceneEnabled()) {
            boolean started = false;
            for (EntityPlayerMP player : players) {
                if (!BossCutsceneHandler.hasSeen(player, citadelId) && BossCutsceneHandler.start(player, citadel, pos)) {
                    started = true;
                }
            }
            if (started) {
                cutsceneStartedAt = server.getTotalWorldTime();
                setState(State.CUTSCENE);
                return;
            }
        }
        // Cutscene disabled or already seen: direct trigger near the centre of the arena.
        int radius = config.getBossTriggerRadius();
        for (EntityPlayerMP player : players) {
            double dx = player.posX - (pos.getX() + 0.5D);
            double dz = player.posZ - (pos.getZ() + 0.5D);
            if (dx * dx + dz * dz <= radius * radius) {
                trySpawnBoss(server, citadel, players);
                return;
            }
        }
    }

    /** Hook called by {@link BossCutsceneHandler} when a cutscene of this citadel ends. */
    public void onCutsceneFinished() {
        if (state != State.CUTSCENE || !(world instanceof WorldServer) || BossCutsceneHandler.hasActiveCutscene(citadelId)) {
            return;
        }
        CitadelInstance citadel = resolveCitadel();
        if (citadel != null && citadel.getBossArenaBounds() != null) {
            List<EntityPlayerMP> players = playersInArena((WorldServer) world, citadel.getBossArenaBounds());
            if (players.isEmpty()) {
                setState(State.DORMANT);
            } else {
                trySpawnBoss((WorldServer) world, citadel, players);
            }
        }
    }

    private void trySpawnBoss(WorldServer server, CitadelInstance citadel, List<EntityPlayerMP> players) {
        if (server.getTotalWorldTime() < nextSpawnAttempt) {
            return;
        }
        EntityLiving boss = BossFactory.spawnBoss(server, citadel, pos, server.rand);
        if (boss == null) {
            nextSpawnAttempt = server.getTotalWorldTime() + RETRY_TICKS;
            ShatteredCitadelsMod.LOGGER.warn("Boss altar at {}: no valid boss spot in the arena, retrying", pos);
            return;
        }
        bossUuid = boss.getUniqueID();
        fallenPlayers.clear();
        controller.reset();
        controller.attach(boss);
        bossMissingSince = -1L;
        arenaEmptySince = -1L;
        setState(State.ACTIVE);
        ITextComponent name = BossFactory.bossName(citadel);
        ensureBossBar(name);
        ITextComponent line = new TextComponentTranslation("shatteredcitadels.boss.spawn." + server.rand.nextInt(3), name);
        line.getStyle().setColor(TextFormatting.DARK_RED).setItalic(true);
        for (EntityPlayerMP player : players) {
            player.sendMessage(line);
        }
        server.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0F, 0.8F);
    }

    private void tickActive(WorldServer server) {
        CitadelInstance citadel = resolveCitadel();
        if (citadel == null || citadel.getBossArenaBounds() == null) {
            return;
        }
        StructureBoundingBox arena = citadel.getBossArenaBounds();
        ITextComponent name = BossFactory.bossName(citadel);
        ensureBossBar(name);
        Entity entity = bossUuid == null ? null : server.getEntityFromUuid(bossUuid);
        EntityLiving boss = entity instanceof EntityLiving && entity.isEntityAlive() ? (EntityLiving) entity : null;
        List<EntityPlayerMP> players = playersInArena(server, arena);
        if (tickCounter % 5 == 0) {
            syncBossBar(players, boss);
        }
        if (boss == null) {
            FloorData top = citadel.getBossFloor();
            if (top != null && top.isBossDefeated()) {
                setState(State.DEFEATED);
                clearBossBar();
                return;
            }
            long now = server.getTotalWorldTime();
            if (bossMissingSince < 0) {
                bossMissingSince = now;
            } else if (now - bossMissingSince > BOSS_LOST_TICKS && !players.isEmpty()) {
                // Lost without a death (despawned by another mod, removed by a command): summon again.
                ShatteredCitadelsMod.LOGGER.warn("Boss of altar {} is gone without dying; summoning a new one", pos);
                bossMissingSince = -1L;
                trySpawnBoss(server, citadel, players);
            }
            return;
        }
        bossMissingSince = -1L;
        if (players.isEmpty()) {
            long now = server.getTotalWorldTime();
            if (arenaEmptySince < 0) {
                arenaEmptySince = now;
            } else if (now - arenaEmptySince > EMPTY_ARENA_RESET_TICKS) {
                // Everyone left: the guardian recovers instead of being whittled down from outside.
                boss.setHealth(boss.getMaxHealth());
                controller.reset();
                arenaEmptySince = -1L;
            }
        } else {
            arenaEmptySince = -1L;
        }
        controller.tick(server, boss, arena, pos, players, BossFactory.bossTier(citadel), citadel.isSkyCitadel(), name);
    }

    // ------------------------------------------------------------------ boss bar

    private void ensureBossBar(ITextComponent name) {
        if (bossInfo == null) {
            bossInfo = new BossInfoServer(name, BossInfo.Color.PURPLE, BossInfo.Overlay.NOTCHED_10);
            bossInfo.setDarkenSky(true);
            bossInfo.setCreateFog(false);
            // Hidden until the first sync knows whether the boss draws a bar of its own.
            bossInfo.setVisible(false);
        }
    }

    private void syncBossBar(List<EntityPlayerMP> inArena, EntityLiving boss) {
        if (bossInfo == null) {
            return;
        }
        if (boss != null) {
            // Bosses from other mods often bring their own bar: two bars for one boss is one too many.
            bossInfo.setVisible(!BossBars.hasOwnBar(boss));
            bossInfo.setPercent(Math.max(0.0F, Math.min(1.0F, boss.getHealth() / Math.max(1.0F, boss.getMaxHealth()))));
            bossInfo.setColor(controller.getPhase() >= 3 ? BossInfo.Color.RED
                    : controller.getPhase() == 2 ? BossInfo.Color.YELLOW : BossInfo.Color.PURPLE);
        }
        Set<EntityPlayerMP> current = new HashSet<>(bossInfo.getPlayers());
        for (EntityPlayerMP player : inArena) {
            if (!current.remove(player)) {
                bossInfo.addPlayer(player);
            }
        }
        for (EntityPlayerMP left : current) {
            bossInfo.removePlayer(left);
        }
    }

    private void clearBossBar() {
        if (bossInfo != null) {
            for (EntityPlayerMP player : new ArrayList<>(bossInfo.getPlayers())) {
                bossInfo.removePlayer(player);
            }
            bossInfo.setVisible(false);
            bossInfo = null;
        }
    }

    @Override
    public void onChunkUnload() {
        clearBossBar();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        clearBossBar();
    }

    // ------------------------------------------------------------------ defeat

    /**
     * Admin/debug: summon the boss now, skipping the cutscene. A defeated arena is re-armed first.
     *
     * @return null on success, otherwise the reason it could not be done
     */
    public String forceSummon() {
        if (!(world instanceof WorldServer)) {
            return "not a server world";
        }
        CitadelInstance citadel = resolveCitadel();
        if (citadel == null || citadel.getBossArenaBounds() == null) {
            return "the altar is not linked to a boss arena";
        }
        if (state == State.ACTIVE && bossUuid != null && ((WorldServer) world).getEntityFromUuid(bossUuid) != null) {
            return "a boss is already active";
        }
        FloorData bossFloor = citadel.getBossFloor();
        if (bossFloor != null) {
            bossFloor.setBossDefeated(false);
            CitadelWorldSavedData.get(world).markDirty();
        }
        BossCutsceneHandler.cancelAll(citadel.getCitadelId());
        nextSpawnAttempt = 0L;
        state = State.DORMANT;
        trySpawnBoss((WorldServer) world, citadel, playersInArena((WorldServer) world, citadel.getBossArenaBounds()));
        return state == State.ACTIVE ? null : "no free spot for the boss in the arena";
    }

    /** A player died in the arena while the boss was alive. */
    public void onPlayerFallen(UUID playerId) {
        if (state == State.ACTIVE && fallenPlayers.add(playerId)) {
            markDirty();
        }
    }

    public Set<UUID> getFallenPlayers() {
        return new HashSet<>(fallenPlayers);
    }

    /** Called by the protection handler when the boss dies. */
    public void onBossDefeated(EntityLivingBase boss) {
        if (state == State.DEFEATED || !(world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) world;
        CitadelInstance citadel = resolveCitadel();
        List<EntityPlayerMP> participants = citadel != null && citadel.getBossArenaBounds() != null
                ? playersInArena(server, citadel.getBossArenaBounds()) : new ArrayList<EntityPlayerMP>();
        setState(State.DEFEATED);
        clearBossBar();
        if (citadel != null) {
            FloorData top = citadel.getBossFloor();
            if (top != null) {
                top.setBossDefeated(true);
                top.setCleared(true);
                top.setRemainingMobs(0);
            }
            CitadelWorldSavedData.get(world).markDirty();
            SealedGates.openAll(world, citadel);
            removeMinions(server, citadel.getBossArenaBounds());
            ITextComponent line = new TextComponentTranslation("shatteredcitadels.boss.defeated", BossFactory.bossName(citadel));
            line.getStyle().setColor(TextFormatting.GOLD);
            for (EntityPlayerMP player : participants) {
                player.sendMessage(line);
            }
            placeTrophyChest(server, citadel);
            FloorTrackerHandler.resyncCitadel(world, citadel.getCitadelId());
            MinecraftForge.EVENT_BUS.post(new CitadelBossDefeatedEvent(citadel, boss, participants, getFallenPlayers()));
            ShatteredCitadelsMod.LOGGER.info("Boss of citadel {} defeated by {} player(s)", citadel.getCitadelId(), participants.size());
        }
        server.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        server.spawnParticle(EnumParticleTypes.TOTEM, pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D, 80, 1.0D, 1.0D, 1.0D, 0.3D);
    }

    private static void removeMinions(WorldServer server, StructureBoundingBox arena) {
        if (arena == null) {
            return;
        }
        for (EntityLiving minion : server.getEntitiesWithinAABB(EntityLiving.class, ArenaSpots.toBox(arena),
                e -> e != null && BossTags.isMinion(e))) {
            minion.setDead();
        }
    }

    /** Trophy chest on top of the altar; a Lootr chest (per-player loot) when Lootr is installed. */
    private void placeTrophyChest(WorldServer server, CitadelInstance citadel) {
        BlockPos chestPos = pos.up();
        if (!server.isAirBlock(chestPos) && server.getBlockState(chestPos).getBlock() != Blocks.AIR) {
            chestPos = chestPos.up();
        }
        IBlockState chest = Blocks.CHEST.getDefaultState();
        ResourceLocation lootr = new ResourceLocation("lootr", "lootr_chest");
        if (Loader.isModLoaded("lootr") && ForgeRegistries.BLOCKS.containsKey(lootr)) {
            Block block = ForgeRegistries.BLOCKS.getValue(lootr);
            if (block != null && block != Blocks.AIR) {
                chest = block.getDefaultState();
            }
        }
        server.setBlockState(chestPos, chest, 3);
        TileEntity tile = server.getTileEntity(chestPos);
        if (!(tile instanceof TileEntityLockableLoot) && chest.getBlock() != Blocks.CHEST) {
            // Unexpected Lootr tile type: fall back to a vanilla chest so the loot is never lost.
            server.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), 3);
            tile = server.getTileEntity(chestPos);
        }
        if (tile instanceof TileEntityLockableLoot) {
            ((TileEntityLockableLoot) tile).setLootTable(citadel.isSkyCitadel() ? ModLootTables.BOSS_CHEST_SKY
                    : ModLootTables.BOSS_CHEST_NORMAL, server.rand.nextLong());
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<EntityPlayerMP> playersInArena(WorldServer server, StructureBoundingBox arena) {
        return server.getEntitiesWithinAABB(EntityPlayerMP.class, ArenaSpots.toBox(arena),
                p -> p != null && p.isEntityAlive() && !p.isSpectator());
    }

    private CitadelInstance resolveCitadel() {
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        if (citadelId != null) {
            return data.getById(citadelId);
        }
        CitadelInstance citadel = data.getCitadelAt(pos);
        if (citadel != null && citadel.getBossArenaBounds() != null && citadel.getBossArenaBounds().isVecInside(pos)) {
            citadelId = citadel.getCitadelId();
            markDirty();
            return citadel;
        }
        return null;
    }

    private void setState(State next) {
        if (state != next) {
            state = next;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (citadelId != null) {
            compound.setUniqueId("citadelId", citadelId);
        }
        compound.setString("state", state.name());
        if (bossUuid != null) {
            compound.setUniqueId("bossUuid", bossUuid);
        }
        compound.setLong("cutsceneStartedAt", cutsceneStartedAt);
        NBTTagList fallen = new NBTTagList();
        for (UUID id : fallenPlayers) {
            fallen.appendTag(new NBTTagString(id.toString()));
        }
        compound.setTag("fallenPlayers", fallen);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        citadelId = compound.hasUniqueId("citadelId") ? compound.getUniqueId("citadelId") : null;
        State loaded = State.DORMANT;
        for (State s : State.values()) {
            if (s.name().equals(compound.getString("state"))) {
                loaded = s;
            }
        }
        state = loaded;
        bossUuid = compound.hasUniqueId("bossUuid") ? compound.getUniqueId("bossUuid") : null;
        cutsceneStartedAt = compound.getLong("cutsceneStartedAt");
        fallenPlayers.clear();
        NBTTagList fallen = compound.getTagList("fallenPlayers", Constants.NBT.TAG_STRING);
        for (int i = 0; i < fallen.tagCount(); i++) {
            try {
                fallenPlayers.add(UUID.fromString(fallen.getStringTagAt(i)));
            } catch (IllegalArgumentException e) {
                // Corrupt entry: ignore.
            }
        }
    }
}
