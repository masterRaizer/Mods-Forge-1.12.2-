package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Safety net for the no-cascading guarantee: if any chunk is loaded while one of our structures
 * is being generated, the call stack is logged (a few times per session) so the offending write
 * or read can be identified. It never changes behaviour.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class CascadeGuard {

    private static final int MAX_REPORTS = 5;

    private static int depth;
    private static int populatingX;
    private static int populatingZ;
    private static int reports;

    private CascadeGuard() {
    }

    static void enter(int chunkX, int chunkZ) {
        depth++;
        populatingX = chunkX;
        populatingZ = chunkZ;
    }

    static void exit() {
        depth = Math.max(0, depth - 1);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (depth == 0 || event.getWorld().isRemote || reports >= MAX_REPORTS) {
            return;
        }
        reports++;
        ShatteredCitadelsMod.LOGGER.warn("Chunk [{}, {}] loaded while generating a citadel in chunk [{}, {}]",
                event.getChunk().x, event.getChunk().z, populatingX, populatingZ, new Throwable("cascade trace"));
    }
}
