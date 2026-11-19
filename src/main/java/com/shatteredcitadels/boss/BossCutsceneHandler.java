package com.shatteredcitadels.boss;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.network.NetworkHandler;
import com.shatteredcitadels.network.PacketBossCutscene;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the boss entrance cutscene. The server owns the timeline: it ends after the
 * configured length, when the player skips (sneak), or is cancelled when the player leaves the
 * arena, changes dimension or logs out. Every end - normal, skipped or cancelled - marks the
 * cutscene as seen and calls the altar hook, so the fight always starts and never starts early.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class BossCutsceneHandler {

    private static final String SEEN_TAG = "ShatteredCitadelsSeenCutscenes";
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private BossCutsceneHandler() {
    }

    /** @return true if the cutscene was started for this player. */
    public static boolean start(EntityPlayerMP player, CitadelInstance citadel, BlockPos altarPos) {
        if (ACTIVE.containsKey(player.getUniqueID()) || citadel.getBossArenaBounds() == null) {
            return false;
        }
        int ticks = ConfigCache.get().getBossCutsceneTicks();
        long now = player.world.getTotalWorldTime();
        ACTIVE.put(player.getUniqueID(), new Active(citadel.getCitadelId(), altarPos, player.dimension,
                now + ticks, citadel.getBossArenaBounds()));
        // Keep the player in place while the camera turns to the altar.
        player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, ticks + 10, 6, false, false));
        NetworkHandler.sendTo(PacketBossCutscene.start(citadel.getDisplayName(), citadel.getArchetypeKey(), ticks,
                altarPos, citadel.isSkyCitadel()), player);
        MinecraftForge.EVENT_BUS.post(new BossCutsceneEvent.Started(player, citadel.getCitadelId(), altarPos));
        return true;
    }

    public static boolean hasActiveCutscene(UUID citadelId) {
        if (citadelId == null) {
            return false;
        }
        for (Active active : ACTIVE.values()) {
            if (citadelId.equals(active.citadelId)) {
                return true;
            }
        }
        return false;
    }

    /** Client asked to skip (sneak). Runs on the server thread. */
    public static void skip(EntityPlayerMP player) {
        finish(player.getUniqueID(), false);
    }

    /** Safety valve used by the altar if a cutscene overstays its length. */
    public static void cancelAll(UUID citadelId) {
        for (UUID playerId : new ArrayList<>(ACTIVE.keySet())) {
            Active active = ACTIVE.get(playerId);
            if (active != null && citadelId.equals(active.citadelId)) {
                finish(playerId, true);
            }
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ seen tracking

    public static boolean hasSeen(EntityPlayer player, UUID citadelId) {
        if (citadelId == null) {
            return true;
        }
        NBTTagList list = persisted(player).getTagList(SEEN_TAG, Constants.NBT.TAG_STRING);
        String id = citadelId.toString();
        for (int i = 0; i < list.tagCount(); i++) {
            if (id.equals(list.getStringTagAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void markSeen(EntityPlayer player, UUID citadelId) {
        if (citadelId == null || hasSeen(player, citadelId)) {
            return;
        }
        NBTTagCompound persisted = persisted(player);
        NBTTagList list = persisted.getTagList(SEEN_TAG, Constants.NBT.TAG_STRING);
        list.appendTag(new NBTTagString(citadelId.toString()));
        persisted.setTag(SEEN_TAG, list);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(ACTIVE.keySet())) {
            Active active = ACTIVE.get(playerId);
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player == null || player.dimension != active.dimension || !player.isEntityAlive()
                    || !ArenaSpots.isInside(active.arena, player)) {
                finish(playerId, true);
            } else if (player.world.getTotalWorldTime() >= active.endTick) {
                finish(playerId, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (ACTIVE.containsKey(event.player.getUniqueID())) {
            finish(event.player.getUniqueID(), true);
        }
    }

    private static void finish(UUID playerId, boolean cancelled) {
        Active active = ACTIVE.remove(playerId);
        if (active == null) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) {
            markSeen(player, active.citadelId);
            player.removePotionEffect(MobEffects.SLOWNESS);
            NetworkHandler.sendTo(PacketBossCutscene.stop(), player);
            MinecraftForge.EVENT_BUS.post(new BossCutsceneEvent.Finished(player, active.citadelId, active.altarPos, cancelled));
        }
        WorldServer world = server == null ? null : server.getWorld(active.dimension);
        if (world != null && world.isBlockLoaded(active.altarPos)) {
            TileEntity tile = world.getTileEntity(active.altarPos);
            if (tile instanceof TileEntityBossAltar) {
                ((TileEntityBossAltar) tile).onCutsceneFinished();
            }
        }
        ShatteredCitadelsMod.LOGGER.debug("Boss cutscene for {} {}", playerId, cancelled ? "cancelled" : "finished");
    }

    private static final class Active {
        final UUID citadelId;
        final BlockPos altarPos;
        final int dimension;
        final long endTick;
        final StructureBoundingBox arena;

        Active(UUID citadelId, BlockPos altarPos, int dimension, long endTick, StructureBoundingBox arena) {
            this.citadelId = citadelId;
            this.altarPos = altarPos;
            this.dimension = dimension;
            this.endTick = endTick;
            this.arena = arena;
        }
    }
}
