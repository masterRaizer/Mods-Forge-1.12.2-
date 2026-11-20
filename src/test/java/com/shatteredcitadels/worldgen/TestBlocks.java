package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockSealedGate;
import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import net.minecraft.init.Bootstrap;

/** Mod blocks and palettes for unit tests, which run without Forge's registries and mod loader. */
final class TestBlocks {

    private TestBlocks() {
    }

    static synchronized void install() {
        Bootstrap.register();
        if (ModBlocks.SEALED_GATE == null) {
            ModBlocks.SEALED_GATE = new BlockSealedGate();
        }
        if (ModBlocks.TRIAL_SPAWNER == null) {
            ModBlocks.TRIAL_SPAWNER = new com.shatteredcitadels.block.BlockTrialSpawner();
        }
        if (ModBlocks.VASE == null) {
            ModBlocks.VASE = new BlockVase();
        }
        ModPaletteRegistry.initWithoutLogging();
    }
}
