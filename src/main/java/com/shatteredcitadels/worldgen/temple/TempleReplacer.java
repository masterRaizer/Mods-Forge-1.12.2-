package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.config.ConfigCache;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Installs {@link ScatteredFeatureGen}; registered on {@code MinecraftForge.TERRAIN_GEN_BUS} by the common proxy. */
public final class TempleReplacer {

    private TempleReplacer() {
    }

    @SubscribeEvent
    public static void onInitMapGen(InitMapGenEvent event) {
        if (event.getType() == InitMapGenEvent.EventType.SCATTERED_FEATURE && ConfigCache.get().isReplaceDesertTemples()) {
            event.setNewGen(new ScatteredFeatureGen());
        }
    }
}
