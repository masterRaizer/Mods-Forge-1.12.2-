package com.shatteredcitadels.proxy;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.compat.castledungeons.CastleConfigPatcher;
import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import com.shatteredcitadels.core.LateMixinLoader;
import com.shatteredcitadels.network.NetworkHandler;
import com.shatteredcitadels.network.PacketFloorTitleBanner;
import com.shatteredcitadels.network.PacketSyncFloorHud;
import com.shatteredcitadels.mob.ModMobTierScanner;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.spawner.TrialSpawnerPlacer;
import com.shatteredcitadels.spawner.WaveSpawnerHooks;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import net.minecraft.world.storage.loot.LootTableList;
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
        // Same discriminators on both physical sides.
        NetworkHandler.init();
    }

    public void init(FMLInitializationEvent event) {
        GameRegistry.registerWorldGenerator(WORLD_GENERATOR, CitadelWorldGenerator.WEIGHT);
        // Citadels, crypts and adopted Castle Dungeons castles now place trial spawners.
        WaveSpawnerHooks.setPlacer(new TrialSpawnerPlacer());
        for (int tier = 1; tier <= 6; tier++) {
            LootTableList.register(TileEntityTrialSpawner.lootTable(tier));
        }
    }

    public void postInit(FMLPostInitializationEvent event) {
        ModMobTierScanner.prefilter();
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

    /** Client-bound packet; never sent to a dedicated server, so nothing to do here. */
    public void handleFloorHudSync(PacketSyncFloorHud message) {
    }

    /** Client-bound packet; never sent to a dedicated server, so nothing to do here. */
    public void handleFloorBanner(PacketFloorTitleBanner message) {
    }

    /** @return true only on the physical client. */
    public boolean isPhysicalClient() {
        return false;
    }
}
