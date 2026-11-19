package com.shatteredcitadels.proxy;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.compat.castledungeons.CastleConfigPatcher;
import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import com.shatteredcitadels.core.LateMixinLoader;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Logic shared by the dedicated server and the client. Must never reference client-only classes.
 */
public class CommonProxy {

    public static final CitadelWorldGenerator WORLD_GENERATOR = new CitadelWorldGenerator();

    public void preInit(FMLPreInitializationEvent event) {
        ShatteredCitadelsMod.LOGGER.info("Pre-initialising {} (side: {})",
                event.getModMetadata().name, event.getSide());
    }

    public void init(FMLInitializationEvent event) {
        GameRegistry.registerWorldGenerator(WORLD_GENERATOR, CitadelWorldGenerator.WEIGHT);
    }

    public void postInit(FMLPostInitializationEvent event) {
        // Ordered before Castle Dungeons (before:castle_dungeons): its post-init reads these files.
        if (CastleDungeonsIntegration.isLoaded()) {
            CastleConfigPatcher.patch(Loader.instance().getConfigDir());
        }
    }

    public void loadComplete(FMLLoadCompleteEvent event) {
        ModPaletteRegistry.init();
        ShatteredCitadelsMod.LOGGER.info("Mixin configurations queued by the late loader: {}",
                LateMixinLoader.getQueuedConfigs());
        if (CastleDungeonsIntegration.isLoaded()) {
            ShatteredCitadelsMod.LOGGER.info("Castle Dungeons integration: {}", CastleDungeonsIntegration.describeStatus());
        }
    }

    /** @return true only on the physical client. */
    public boolean isPhysicalClient() {
        return false;
    }
}
