package com.shatteredcitadels.structure;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/** What happens around a catacomb sanctum: titles for the players and the mage's defeat. */
public final class MageSanctum {

    private static final double ANNOUNCE_RADIUS = 48.0D;

    private MageSanctum() {
    }

    /**
     * The mage is dead: the sanctum floor counts as cleared and its boss as defeated (a side boss,
     * so the structure is not conquered by it), and the pedestal gates open.
     */
    public static void onMageDefeated(WorldServer world, UUID structureId, int floorIndex, BlockPos where) {
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        CitadelInstance rec = data.getById(structureId);
        FloorData floor = rec == null ? null : rec.getFloor(floorIndex);
        if (floor == null) {
            return;
        }
        floor.setCleared(true);
        floor.setBossDefeated(true);
        data.markDirty();
        SealedGates.open(world, rec, floorIndex);
        ShatteredCitadelsMod.LOGGER.info("Guardian mage of {} defeated", structureId);
        for (EntityPlayerMP player : world.getPlayers(EntityPlayerMP.class,
                p -> p != null && p.getDistanceSq(where) < ANNOUNCE_RADIUS * ANNOUNCE_RADIUS)) {
            title(player, "shatteredcitadels.mage.defeated", "shatteredcitadels.mage.defeated_subtitle");
        }
    }

    public static void title(EntityPlayerMP player, String titleKey, String subtitleKey) {
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TIMES, null, 10, 60, 20));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, new TextComponentTranslation(subtitleKey)));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentTranslation(titleKey)));
    }
}
