package com.shatteredcitadels.worldgen.stronghold;

import com.shatteredcitadels.config.ConfigCache;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Installs {@link ShatteredStrongholdGen}; registered on {@code MinecraftForge.TERRAIN_GEN_BUS} by the common proxy. */
public final class StrongholdReplacer {

    private StrongholdReplacer() {
    }

    @SubscribeEvent
    public static void onInitMapGen(InitMapGenEvent event) {
        if (event.getType() == InitMapGenEvent.EventType.STRONGHOLD && ConfigCache.get().isReplaceStrongholds()) {
            event.setNewGen(new ShatteredStrongholdGen());
        }
    }
}
