package com.shatteredcitadels.proxy;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.core.LateMixinLoader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Logic shared by the dedicated server and the client. Must never reference client-only classes.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ShatteredCitadelsMod.LOGGER.info("Pre-initialising {} (side: {})",
                event.getModMetadata().name, event.getSide());
    }

    public void init(FMLInitializationEvent event) {
        ShatteredCitadelsMod.LOGGER.debug("Common init complete");
    }

    public void postInit(FMLPostInitializationEvent event) {
        ShatteredCitadelsMod.LOGGER.debug("Common post-init complete");
    }

    public void loadComplete(FMLLoadCompleteEvent event) {
        ShatteredCitadelsMod.LOGGER.info("Mixin configurations queued by the late loader: {}",
                LateMixinLoader.getQueuedConfigs());
    }

    /** @return true only on the physical client. */
    public boolean isPhysicalClient() {
        return false;
    }
}
