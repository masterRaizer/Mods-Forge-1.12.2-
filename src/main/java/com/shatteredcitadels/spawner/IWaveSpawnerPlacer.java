package com.shatteredcitadels.spawner;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Places a spawner for a citadel floor. Stage 4 registers the wave spawner block implementation
 * through {@link WaveSpawnerHooks#setPlacer(IWaveSpawnerPlacer)}; until then
 * {@link VanillaFallbackSpawnerPlacer} is used.
 *
 * <p>Called during chunk population: implementations must write only at {@code pos}, with
 * block update flag 2, and must not load chunks.
 */
public interface IWaveSpawnerPlacer {

    /** @return true if a spawner was placed at {@code pos}. */
    boolean placeSpawner(World world, BlockPos pos, SpawnerContext context);
}
