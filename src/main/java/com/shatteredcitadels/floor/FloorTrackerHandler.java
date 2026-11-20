package com.shatteredcitadels.floor;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.advancement.CustomAdvancementTriggers;
import com.shatteredcitadels.network.NetworkHandler;
import com.shatteredcitadels.network.PacketFloorTitleBanner;
import com.shatteredcitadels.network.PacketSyncFloorHud;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.SealedGates;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
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
 * Server-side floor tracking. Every {@value #CHECK_INTERVAL} ticks each player's citadel and
 * floor are resolved through the chunk index; packets are sent only when the visible state
 * changes (enter, floor change, wave/mob update, exit). The per-player cache is dropped on
 * logout, dimension change, respawn and server stop.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class FloorTrackerHandler {

    public static final int CHECK_INTERVAL = 10;

    /** Last state sent to each online player. Server thread only. */
    private static final Map<UUID, FloorHudState> SENT = new HashMap<>();

    private FloorTrackerHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.ticksExisted % CHECK_INTERVAL != 0) {
            return;
        }
        try {
            refresh(player, false);
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Floor tracking failed for {}", player.getName(), e);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.player.getUniqueID());
        if (event.player instanceof EntityPlayerMP) {
            // Clear the HUD at once; the next check in the new dimension re-evaluates.
            NetworkHandler.sendTo(new PacketSyncFloorHud(FloorHudState.NONE), (EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        SENT.remove(event.player.getUniqueID());
    }

    /** Called when the server stops, so an integrated server never keeps stale player state. */
    public static void clearAll() {
        SENT.clear();
    }

    /** @return number of cached players (diagnostics and leak checks). */
    public static int cachedPlayerCount() {
        return SENT.size();
    }

    // ------------------------------------------------------------------ tracking

    /** Re-evaluates one player; sends packets only if the state changed or {@code force} is set. */
    public static void refresh(EntityPlayerMP player, boolean force) {
        FloorHudState now = resolve(player);
        UUID id = player.getUniqueID();
        FloorHudState previous = SENT.get(id);
        if (!force && now.equals(previous)) {
            return;
        }
        SENT.put(id, now);
        NetworkHandler.sendTo(new PacketSyncFloorHud(now), player);
        if (now.isInside() && !now.sameFloorAs(previous)) {
            NetworkHandler.sendTo(new PacketFloorTitleBanner(now), player);
            if (previous == null || !previous.isInside() || !previous.getCitadelId().equals(now.getCitadelId())) {
                CustomAdvancementTriggers.enterStructure(player, structureType(now));
            }
            CustomAdvancementTriggers.floorReached(player, now.getFloorIndex());
        }
    }

    /**
     * Immediately re-syncs every player currently shown {@code citadelId}, e.g. after the wave
     * system changed a floor's state (stage 4).
     */
    public static void resyncCitadel(World world, UUID citadelId) {
        if (world == null || world.isRemote || citadelId == null) {
            return;
        }
        for (EntityPlayer player : world.playerEntities) {
            FloorHudState shown = SENT.get(player.getUniqueID());
            if (player instanceof EntityPlayerMP && shown != null && shown.isInside()
                    && citadelId.equals(shown.getCitadelId())) {
                refresh((EntityPlayerMP) player, false);
            }
        }
    }

    private static String structureType(FloorHudState state) {
        if (state.isSkyCitadel()) {
            return "sky_citadel";
        }
        if (state.isUndergroundOnly()) {
            return "crypt";
        }
        return "castle_dungeons".equals(state.getArchetypeKey()) ? "castle_dungeons" : "citadel";
    }

    private static FloorHudState resolve(EntityPlayer player) {
        CitadelWorldSavedData data = CitadelWorldSavedData.get(player.world);
        BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
        CitadelInstance citadel = data.getCitadelAt(pos);
        if (citadel == null) {
            return FloorHudState.NONE;
        }
        // Gates that waited in unloaded chunks open as soon as someone is inside again.
        SealedGates.reconcile(player.world, citadel);
        return FloorHudState.of(citadel, citadel.floorAt(MathHelper.floor(player.posY)));
    }

    /**
     * Live check (not the 10-tick cache) used by the wave system: is any non-spectator player
     * standing on the given floor of the given structure in this world?
     */
    public static boolean isAnyPlayerOnFloor(World world, UUID citadelId, int floorIndex) {
        return countPlayersOnFloor(world, citadelId, floorIndex) > 0;
    }

    public static int countPlayersOnFloor(World world, UUID citadelId, int floorIndex) {
        return playersOnFloor(world, citadelId, floorIndex).size();
    }

    /** @return living, non-spectator players standing on the floor right now. */
    public static List<EntityPlayerMP> playersOnFloor(World world, UUID citadelId, int floorIndex) {
        List<EntityPlayerMP> result = new ArrayList<>();
        if (world == null || world.isRemote || citadelId == null) {
            return result;
        }
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getById(citadelId);
        FloorData floor = citadel == null ? null : citadel.getFloor(floorIndex);
        if (floor == null) {
            return result;
        }
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP) || player.isSpectator() || !player.isEntityAlive()) {
                continue;
            }
            BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
            if (citadel.contains(pos) && floor.containsY(MathHelper.floor(player.posY))) {
                result.add((EntityPlayerMP) player);
            }
        }
        return result;
    }
}
