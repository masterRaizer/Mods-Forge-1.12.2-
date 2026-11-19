package com.shatteredcitadels.proxy;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.client.ClientFloorState;
import com.shatteredcitadels.network.PacketFloorTitleBanner;
import com.shatteredcitadels.network.PacketSyncFloorHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-only proxy. Loaded exclusively through {@code @SidedProxy} on the physical client,
 * so the dedicated server never resolves {@link Minecraft}.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        super.loadComplete(event);
        Language language = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage();
        ShatteredCitadelsMod.LOGGER.debug("Client ready, language: {}",
                language == null ? "unknown" : language.getLanguageCode());
    }

    /** Network thread -> client thread: never touch client state from the netty thread. */
    @Override
    public void handleFloorHudSync(PacketSyncFloorHud message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientFloorState.apply(message.getState()));
    }

    @Override
    public void handleFloorBanner(PacketFloorTitleBanner message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientFloorState.showBanner(message));
    }

    @Override
    public boolean isPhysicalClient() {
        return true;
    }
}
