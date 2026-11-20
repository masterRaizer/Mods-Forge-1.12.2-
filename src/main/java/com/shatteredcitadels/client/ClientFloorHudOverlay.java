package com.shatteredcitadels.client;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.HudPosition;
import com.shatteredcitadels.floor.FloorHudState;
import com.shatteredcitadels.network.PacketFloorTitleBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the floor HUD: a fading title banner in the style of Traveler's Titles (placed below
 * the crosshair so it never covers Traveler's Titles or boss bars) and a compact plaque at the
 * configured corner showing floor, difficulty and wave progress.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MODID, value = Side.CLIENT)
public final class ClientFloorHudOverlay {

    private static final long FADE_IN_MS = 400L;
    private static final long FADE_OUT_MS = 700L;
    private static final int PLAQUE_MAX_NAME_WIDTH = 170;
    private static final int COLOR_TITLE = 0xFFD27A;
    private static final int COLOR_TEXT = 0xE6E6E6;
    private static final int COLOR_WAVE = 0xFF7A6A;
    private static final int COLOR_CLEARED = 0x7CFF8A;
    private static final int COLOR_IDLE = 0xA8A8A8;
    private static final int COLOR_LINE = 0xC8A050;

    private ClientFloorHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI || ClientCutsceneOverlay.isActive()) {
            return;
        }
        ConfigCache.Snapshot config = ConfigCache.get();
        ScaledResolution resolution = event.getResolution();
        if (config.isFloorBannerEnabled()) {
            drawBanner(mc, resolution, config);
        }
        if (config.isFloorHudEnabled() && !mc.gameSettings.showDebugInfo) {
            drawPlaque(mc, resolution, config);
        }
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientFloorState.reset();
    }

    // ------------------------------------------------------------------ banner

    private static void drawBanner(Minecraft mc, ScaledResolution resolution, ConfigCache.Snapshot config) {
        PacketFloorTitleBanner banner = ClientFloorState.getBanner();
        if (banner == null) {
            return;
        }
        long elapsed = ClientFloorState.getBannerElapsed();
        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = elapsed / (float) FADE_IN_MS;
        } else if (elapsed > ClientFloorState.BANNER_DURATION_MS - FADE_OUT_MS) {
            alpha = (ClientFloorState.BANNER_DURATION_MS - elapsed) / (float) FADE_OUT_MS;
        } else {
            alpha = 1.0F;
        }
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        if (a < 8) {
            // FontRenderer treats alpha below 4 as opaque; skip near-invisible frames.
            return;
        }
        FontRenderer font = mc.fontRenderer;
        String title = CitadelTextFormatter.name(banner.getCitadelName());
        String subtitle = CitadelTextFormatter.bannerSubtitle(banner.getFloorIndex(), banner.getSurfaceFloors(),
                banner.getDifficultyTier(), banner.hasCourtyard());
        String extra = banner.isBossFloor() ? CitadelTextFormatter.translate("shatteredcitadels.hud.banner.arena") : null;

        int centerX = resolution.getScaledWidth() / 2;
        int y = resolution.getScaledHeight() / 2 + 28 + config.getBannerOffsetY();
        float titleScale = 1.6F;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, y, 0.0F);
        GlStateManager.scale(titleScale, titleScale, 1.0F);
        font.drawStringWithShadow(title, -font.getStringWidth(title) / 2.0F, 0.0F, (a << 24) | COLOR_TITLE);
        GlStateManager.popMatrix();

        int lineY = y + Math.round(font.FONT_HEIGHT * titleScale) + 2;
        int lineHalf = Math.max(font.getStringWidth(subtitle), Math.round(font.getStringWidth(title) * titleScale)) / 2 + 6;
        Gui.drawRect(centerX - lineHalf, lineY, centerX + lineHalf, lineY + 1, ((a * 3 / 5) << 24) | COLOR_LINE);
        font.drawStringWithShadow(subtitle, centerX - font.getStringWidth(subtitle) / 2.0F, lineY + 4, (a << 24) | COLOR_TEXT);
        if (extra != null) {
            font.drawStringWithShadow(extra, centerX - font.getStringWidth(extra) / 2.0F, lineY + 16, (a << 24) | COLOR_WAVE);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ plaque

    private static void drawPlaque(Minecraft mc, ScaledResolution resolution, ConfigCache.Snapshot config) {
        FloorHudState state = ClientFloorState.getCurrent();
        if (!state.isInside()) {
            return;
        }
        FontRenderer font = mc.fontRenderer;
        List<String> lines = new ArrayList<>(3);
        List<Integer> colors = new ArrayList<>(3);
        String name = CitadelTextFormatter.name(state.getCitadelName());
        if (font.getStringWidth(name) > PLAQUE_MAX_NAME_WIDTH) {
            name = font.trimStringToWidth(name, PLAQUE_MAX_NAME_WIDTH - font.getStringWidth("...")) + "...";
        }
        lines.add(name);
        colors.add(COLOR_TITLE);
        lines.add(CitadelTextFormatter.plaqueFloor(state.getFloorIndex(), state.getSurfaceFloors(), state.getDifficultyTier(),
                state.hasCourtyard()));
        colors.add(COLOR_TEXT);
        String status = statusLine(state);
        if (status != null) {
            lines.add(status);
            colors.add(statusColor(state));
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int padding = 4;
        int boxWidth = width + padding * 2;
        int boxHeight = lines.size() * (font.FONT_HEIGHT + 1) + padding * 2 - 1;
        float scale = (float) config.getHudScale();
        HudPosition position = config.getHudPosition();
        int[] origin = position.place(resolution.getScaledWidth(), resolution.getScaledHeight(),
                Math.round(boxWidth * scale), Math.round(boxHeight * scale), config.getHudOffsetX(), config.getHudOffsetY());

        GlStateManager.pushMatrix();
        GlStateManager.translate(origin[0], origin[1], 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        Gui.drawRect(0, 0, boxWidth, boxHeight, 0x90000000);
        Gui.drawRect(0, 0, 1, boxHeight, 0xFF000000 | COLOR_LINE);
        int y = padding;
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), padding, y, 0xFF000000 | colors.get(i));
            y += font.FONT_HEIGHT + 1;
        }
        GlStateManager.popMatrix();
    }

    private static String statusLine(FloorHudState state) {
        if (state.isBossFloor() && state.isBossDefeated()) {
            return CitadelTextFormatter.translate("shatteredcitadels.hud.plaque.boss_defeated");
        }
        if (state.isBossFloor()) {
            return CitadelTextFormatter.translate("shatteredcitadels.hud.plaque.boss_alive");
        }
        if (state.isCleared()) {
            return CitadelTextFormatter.translate("shatteredcitadels.hud.plaque.cleared");
        }
        if (state.getActiveWave() > 0) {
            return CitadelTextFormatter.translate("shatteredcitadels.hud.plaque.waves", state.getActiveWave(),
                    Math.max(state.getActiveWave(), state.getTotalWaves()), state.getRemainingMobs());
        }
        if (state.getTotalWaves() > 0) {
            return CitadelTextFormatter.translate("shatteredcitadels.hud.plaque.idle", state.getTotalWaves());
        }
        return null;
    }

    private static int statusColor(FloorHudState state) {
        if (state.isCleared() || (state.isBossFloor() && state.isBossDefeated())) {
            return COLOR_CLEARED;
        }
        return state.getActiveWave() > 0 || state.isBossFloor() ? COLOR_WAVE : COLOR_IDLE;
    }
}
