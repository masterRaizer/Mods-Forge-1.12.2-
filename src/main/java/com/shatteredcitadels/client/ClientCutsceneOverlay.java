package com.shatteredcitadels.client;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.network.NetworkHandler;
import com.shatteredcitadels.network.PacketBossCutscene;
import com.shatteredcitadels.network.PacketCutsceneSkip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client part of the boss entrance cutscene: cinematic letterbox bars, fading title lines,
 * the camera slowly turning to the altar, hidden HUD, and "sneak to skip". The server owns the
 * timeline; the client also stops on its own a little after the expected end as a safety net.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MODID, value = Side.CLIENT)
public final class ClientCutsceneOverlay {

    private static final long BAR_ANIMATION_MS = 600L;
    private static final long CAMERA_TURN_MS = 2500L;
    private static final long CLIENT_GRACE_MS = 2000L;
    private static final int LINES = 4;

    private static PacketBossCutscene active;
    private static long startMs;
    private static long durationMs;
    private static boolean skipSent;
    private static boolean sneakWasDown;

    private ClientCutsceneOverlay() {
    }

    public static void onPacket(PacketBossCutscene packet) {
        if (packet.isStart()) {
            active = packet;
            startMs = Minecraft.getSystemTime();
            durationMs = packet.getDurationTicks() * 50L;
            skipSent = false;
            // Ignore a sneak key that is already held when the cutscene starts.
            sneakWasDown = Minecraft.getMinecraft().gameSettings.keyBindSneak.isKeyDown();
        } else {
            active = null;
        }
    }

    public static boolean isActive() {
        return active != null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long elapsed = Minecraft.getSystemTime() - startMs;
        if (mc.player == null || elapsed > durationMs + CLIENT_GRACE_MS) {
            active = null;
            return;
        }
        boolean sneak = mc.gameSettings.keyBindSneak.isKeyDown();
        if (sneak && !sneakWasDown && !skipSent) {
            skipSent = true;
            NetworkHandler.sendToServer(new PacketCutsceneSkip());
        }
        sneakWasDown = sneak;
        if (elapsed < CAMERA_TURN_MS) {
            turnCamera(mc.player, active.getAltarPos(), elapsed / (float) CAMERA_TURN_MS);
        }
    }

    /** Eases the view towards the altar; the player can look around again afterwards. */
    private static void turnCamera(EntityPlayerSP player, BlockPos altar, float progress) {
        double dx = altar.getX() + 0.5D - player.posX;
        double dy = altar.getY() + 1.5D - (player.posY + player.getEyeHeight());
        double dz = altar.getZ() + 0.5D - player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float targetPitch = (float) -(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI));
        float ease = 0.08F + 0.12F * progress;
        player.rotationYaw += MathHelper.wrapDegrees(targetYaw - player.rotationYaw) * ease;
        player.rotationPitch += (targetPitch - player.rotationPitch) * ease;
    }

    @SubscribeEvent
    public static void onRenderPre(RenderGameOverlayEvent.Pre event) {
        if (active == null) {
            return;
        }
        switch (event.getType()) {
            case HOTBAR:
            case HEALTH:
            case FOOD:
            case ARMOR:
            case AIR:
            case EXPERIENCE:
            case CROSSHAIRS:
            case JUMPBAR:
                event.setCanceled(true);
                break;
            default:
                break;
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderGameOverlayEvent.Post event) {
        if (active == null || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = event.getResolution();
        int width = res.getScaledWidth();
        int height = res.getScaledHeight();
        long elapsed = Minecraft.getSystemTime() - startMs;
        float barIn = Math.min(1.0F, elapsed / (float) BAR_ANIMATION_MS);
        float barOut = Math.min(1.0F, Math.max(0.0F, (durationMs - elapsed) / (float) BAR_ANIMATION_MS));
        int bar = Math.round(height * 0.12F * Math.min(barIn, barOut));
        Gui.drawRect(0, 0, width, bar, 0xFF000000);
        Gui.drawRect(0, height - bar, width, height, 0xFF000000);

        float textAlpha = Math.min(Math.min(1.0F, (elapsed - 300L) / 700.0F), Math.max(0.0F, (durationMs - elapsed) / 800.0F));
        int a = Math.round(Math.max(0.0F, textAlpha) * 255.0F);
        if (a < 8) {
            return;
        }
        FontRenderer font = mc.fontRenderer;
        String title = I18n.format("shatteredcitadels.cutscene.title");
        String name = CitadelTextFormatter.name(active.getCitadelName());
        String line = I18n.format("shatteredcitadels.cutscene.line." + Math.floorMod(active.getCitadelName().hashCode(), LINES));
        String skip = I18n.format("shatteredcitadels.cutscene.skip");

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.translate(width / 2.0F, height * 0.30F, 0.0F);
        GlStateManager.scale(2.2F, 2.2F, 1.0F);
        font.drawStringWithShadow(title, -font.getStringWidth(title) / 2.0F, 0.0F, (a << 24) | (active.isSky() ? 0x9FD8FF : 0xE04040));
        GlStateManager.popMatrix();
        font.drawStringWithShadow(name, (width - font.getStringWidth(name)) / 2.0F, height * 0.30F + 26, (a << 24) | 0xFFD27A);
        font.drawStringWithShadow(line, (width - font.getStringWidth(line)) / 2.0F, height * 0.30F + 40, (a << 24) | 0xC8C8C8);
        if (bar > 10) {
            font.drawStringWithShadow(skip, width - font.getStringWidth(skip) - 8, height - bar / 2.0F - 4, (a << 24) | 0x909090);
        }
        GlStateManager.disableBlend();
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        active = null;
    }
}
