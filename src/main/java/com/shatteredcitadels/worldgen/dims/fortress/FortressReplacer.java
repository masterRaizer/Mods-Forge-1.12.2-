package com.shatteredcitadels.worldgen.dims.fortress;

import com.shatteredcitadels.worldgen.dims.DimsConfig;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Installs {@link ShatteredNetherBridgeGen}; registered on {@code MinecraftForge.TERRAIN_GEN_BUS} by the common proxy. */
public final class FortressReplacer {

    private FortressReplacer() {
    }

    @SubscribeEvent
    public static void onInitMapGen(InitMapGenEvent event) {
        if (event.getType() == InitMapGenEvent.EventType.NETHER_BRIDGE && DimsConfig.replaceNetherFortress()) {
            event.setNewGen(new ShatteredNetherBridgeGen());
        }
    }
}
