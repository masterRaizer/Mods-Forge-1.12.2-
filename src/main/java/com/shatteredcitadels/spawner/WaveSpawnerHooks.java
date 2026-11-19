package com.shatteredcitadels.spawner;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Single entry point every generator uses to place spawners; the implementation is swappable. */
public final class WaveSpawnerHooks {

    private static final IWaveSpawnerPlacer FALLBACK = new VanillaFallbackSpawnerPlacer();
    private static volatile IWaveSpawnerPlacer placer = FALLBACK;

    private WaveSpawnerHooks() {
    }

    public static void setPlacer(IWaveSpawnerPlacer newPlacer) {
        placer = newPlacer == null ? FALLBACK : newPlacer;
        ShatteredCitadelsMod.LOGGER.info("Wave spawner placer set to {}", placer.getClass().getName());
    }

    public static IWaveSpawnerPlacer getPlacer() {
        return placer;
    }

    /** Places a spawner, falling back to the vanilla placer if the active one fails. */
    public static boolean place(World world, BlockPos pos, SpawnerContext context) {
        IWaveSpawnerPlacer active = placer;
        try {
            if (active.placeSpawner(world, pos, context)) {
                return true;
            }
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Spawner placer {} failed at {}, using fallback",
                    active.getClass().getName(), pos, e);
        }
        return active != FALLBACK && FALLBACK.placeSpawner(world, pos, context);
    }
}
