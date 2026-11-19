package com.shatteredcitadels;

import com.shatteredcitadels.boss.BossCutsceneHandler;
import com.shatteredcitadels.command.CommandShatteredCitadels;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.mob.ModMobTierScanner;
import com.shatteredcitadels.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Reference.MODID,
        name = Reference.NAME,
        version = Reference.VERSION,
        acceptedMinecraftVersions = Reference.MC_VERSIONS,
        dependencies = Reference.DEPENDENCIES,
        useMetadata = true
)
public final class ShatteredCitadelsMod {

    /** Replaced with the FML-provided mod logger in pre-init; never null. */
    public static Logger LOGGER = LogManager.getLogger(Reference.MODID);

    @Mod.Instance(Reference.MODID)
    public static ShatteredCitadelsMod INSTANCE;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY, serverSide = Reference.SERVER_PROXY)
    public static CommonProxy PROXY;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        // @Config values are already loaded by Forge at this point; build the parsed cache.
        ConfigCache.rebuild();
        PROXY.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        PROXY.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        PROXY.postInit(event);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // Every mod has registered its entities now: resolve entity IDs from the config.
        ConfigCache.resolveRegistryEntries();
        PROXY.loadComplete(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandShatteredCitadels());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        FloorTrackerHandler.clearAll();
        ModMobTierScanner.reset();
        BossCutsceneHandler.clearAll();
    }
}
