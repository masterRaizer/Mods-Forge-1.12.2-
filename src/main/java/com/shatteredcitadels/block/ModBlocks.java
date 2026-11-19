package com.shatteredcitadels.block;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Block, item and tile entity registration (1.12.2 registry events). */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ModBlocks {

    public static Block TRIAL_SPAWNER;
    public static Item TRIAL_SPAWNER_ITEM;
    public static Block BOSS_ALTAR;
    public static Item BOSS_ALTAR_ITEM;

    private ModBlocks() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        TRIAL_SPAWNER = new BlockTrialSpawner()
                .setRegistryName(TileEntityTrialSpawner.ID)
                .setTranslationKey(Reference.MODID + ".trial_spawner");
        event.getRegistry().register(TRIAL_SPAWNER);
        GameRegistry.registerTileEntity(TileEntityTrialSpawner.class, TileEntityTrialSpawner.ID);
        BOSS_ALTAR = new BlockBossAltar()
                .setRegistryName(TileEntityBossAltar.ID)
                .setTranslationKey(Reference.MODID + ".boss_altar");
        event.getRegistry().register(BOSS_ALTAR);
        GameRegistry.registerTileEntity(TileEntityBossAltar.class, TileEntityBossAltar.ID);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        TRIAL_SPAWNER_ITEM = new ItemBlock(TRIAL_SPAWNER).setRegistryName(TileEntityTrialSpawner.ID);
        event.getRegistry().register(TRIAL_SPAWNER_ITEM);
        BOSS_ALTAR_ITEM = new ItemBlock(BOSS_ALTAR).setRegistryName(TileEntityBossAltar.ID);
        event.getRegistry().register(BOSS_ALTAR_ITEM);
    }
}
