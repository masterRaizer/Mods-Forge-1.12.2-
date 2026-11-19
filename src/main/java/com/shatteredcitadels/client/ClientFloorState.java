package com.shatteredcitadels.client;

import com.shatteredcitadels.floor.FloorHudState;
import com.shatteredcitadels.network.PacketFloorTitleBanner;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client copy of the floor HUD state and the active banner. Written on the client thread. */
@SideOnly(Side.CLIENT)
public final class ClientFloorState {

    /** Banner lifetime in milliseconds (fade in, hold, fade out). */
    public static final long BANNER_DURATION_MS = 3500L;

    private static volatile FloorHudState current = FloorHudState.NONE;
    private static volatile PacketFloorTitleBanner banner;
    private static volatile long bannerStart;

    private ClientFloorState() {
    }

    public static FloorHudState getCurrent() {
        return current;
    }

    public static void apply(FloorHudState state) {
        current = state == null ? FloorHudState.NONE : state;
    }

    public static void showBanner(PacketFloorTitleBanner message) {
        banner = message;
        bannerStart = Minecraft.getSystemTime();
    }

    /** @return the banner still on screen, or null once it has faded out. */
    public static PacketFloorTitleBanner getBanner() {
        PacketFloorTitleBanner active = banner;
        if (active != null && Minecraft.getSystemTime() - bannerStart > BANNER_DURATION_MS) {
            banner = null;
            return null;
        }
        return active;
    }

    public static long getBannerElapsed() {
        return Minecraft.getSystemTime() - bannerStart;
    }

    /** Forget everything, e.g. when leaving a server. */
    public static void reset() {
        current = FloorHudState.NONE;
        banner = null;
    }
}
