package com.shatteredcitadels.network;

import com.shatteredcitadels.Reference;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mod's single network channel. Messages are registered on both physical sides in
 * pre-init (discriminators must match); client-bound handlers delegate to the proxy, so the
 * dedicated server never touches client classes.
 */
public final class NetworkHandler {

    private static final int ID_SYNC_FLOOR_HUD = 0;
    private static final int ID_FLOOR_TITLE_BANNER = 1;
    private static final int ID_BOSS_CUTSCENE = 2;
    private static final int ID_CUTSCENE_SKIP = 3;

    private static SimpleNetworkWrapper channel;

    private NetworkHandler() {
    }

    public static synchronized void init() {
        if (channel != null) {
            return;
        }
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MODID);
        channel.registerMessage(PacketSyncFloorHud.Handler.class, PacketSyncFloorHud.class, ID_SYNC_FLOOR_HUD, Side.CLIENT);
        channel.registerMessage(PacketFloorTitleBanner.Handler.class, PacketFloorTitleBanner.class, ID_FLOOR_TITLE_BANNER, Side.CLIENT);
        channel.registerMessage(PacketBossCutscene.Handler.class, PacketBossCutscene.class, ID_BOSS_CUTSCENE, Side.CLIENT);
        channel.registerMessage(PacketCutsceneSkip.Handler.class, PacketCutsceneSkip.class, ID_CUTSCENE_SKIP, Side.SERVER);
    }

    /** Client only: send a client-to-server message. */
    public static void sendToServer(IMessage message) {
        if (channel != null) {
            channel.sendToServer(message);
        }
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        if (channel != null && player != null && player.connection != null) {
            channel.sendTo(message, player);
        }
    }
}
