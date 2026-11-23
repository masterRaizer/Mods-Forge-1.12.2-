package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockSealedGate;
import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import net.minecraft.init.Bootstrap;

/** Mod blocks and palettes for unit tests, which run without Forge's registries and mod loader. */
public final class TestBlocks {

    private TestBlocks() {
    }

    public static synchronized void install() {
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
        if (ModBlocks.VAULT_BRICK == null) {
            ModBlocks.VAULT_BRICK = new com.shatteredcitadels.block.BlockVaultBrick();
        }
        if (ModBlocks.IDOL_SOCKET == null) {
            ModBlocks.IDOL_SOCKET = new com.shatteredcitadels.block.BlockIdolSocket();
        }
        if (ModBlocks.BOSS_ALTAR == null) {
            ModBlocks.BOSS_ALTAR = new com.shatteredcitadels.block.BlockBossAltar();
        }
        // Blueprints store state ids (block id + meta): the mod blocks need ids, as in the game.
        String[] names = {"sealed_gate", "vault_brick", "idol_socket", "boss_altar"};
        net.minecraft.block.Block[] blocks = {ModBlocks.SEALED_GATE, ModBlocks.VAULT_BRICK, ModBlocks.IDOL_SOCKET, ModBlocks.BOSS_ALTAR};
        unlock(net.minecraft.block.Block.REGISTRY);
        for (int i = 0; i < blocks.length; i++) {
            if (net.minecraft.block.Block.getIdFromBlock(blocks[i]) <= 0) {
                net.minecraft.block.Block.REGISTRY.register(3000 + i, new net.minecraft.util.ResourceLocation("shatteredcitadels", names[i]),
                        blocks[i]);
            }
        }
        ModPaletteRegistry.initWithoutLogging();
    }

    /** Bootstrap locks the registry wrapper; tests may still add their blocks. */
    private static void unlock(Object registry) {
        for (Class<?> type = registry.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field locked = type.getDeclaredField("locked");
                locked.setAccessible(true);
                locked.setBoolean(registry, false);
                return;
            } catch (ReflectiveOperationException ignored) {
                // look further up
            }
        }
    }
}
