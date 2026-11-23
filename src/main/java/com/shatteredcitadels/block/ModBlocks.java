package com.shatteredcitadels.block;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import com.shatteredcitadels.tile.TileEntityMageSigil;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemMultiTexture;
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
    public static Block SEALED_GATE;
    public static Item SEALED_GATE_ITEM;
    public static Block VASE;
    public static Block MAGE_SIGIL;
    public static Item VASE_ITEM;
    public static Block IDOL_SOCKET;
    public static Item IDOL_SOCKET_ITEM;
    public static Block VAULT_BRICK;
    public static Item VAULT_BRICK_ITEM;

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
        SEALED_GATE = new BlockSealedGate()
                .setRegistryName(Reference.MODID, "sealed_gate")
                .setTranslationKey(Reference.MODID + ".sealed_gate");
        event.getRegistry().register(SEALED_GATE);
        MAGE_SIGIL = new BlockMageSigil()
                .setRegistryName(TileEntityMageSigil.ID)
                .setTranslationKey(Reference.MODID + ".mage_sigil");
        event.getRegistry().register(MAGE_SIGIL);
        GameRegistry.registerTileEntity(TileEntityMageSigil.class, TileEntityMageSigil.ID);
        VASE = new BlockVase()
                .setRegistryName(Reference.MODID, "vase")
                .setTranslationKey(Reference.MODID + ".vase");
        event.getRegistry().register(VASE);
        IDOL_SOCKET = new BlockIdolSocket()
                .setRegistryName(Reference.MODID, "idol_socket")
                .setTranslationKey(Reference.MODID + ".idol_socket");
        event.getRegistry().register(IDOL_SOCKET);
        VAULT_BRICK = new BlockVaultBrick()
                .setRegistryName(Reference.MODID, "vault_brick")
                .setTranslationKey(Reference.MODID + ".vault_brick");
        event.getRegistry().register(VAULT_BRICK);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        TRIAL_SPAWNER_ITEM = new ItemBlock(TRIAL_SPAWNER).setRegistryName(TileEntityTrialSpawner.ID);
        event.getRegistry().register(TRIAL_SPAWNER_ITEM);
        BOSS_ALTAR_ITEM = new ItemBlock(BOSS_ALTAR).setRegistryName(TileEntityBossAltar.ID);
        event.getRegistry().register(BOSS_ALTAR_ITEM);
        SEALED_GATE_ITEM = new ItemBlock(SEALED_GATE).setRegistryName(SEALED_GATE.getRegistryName());
        event.getRegistry().register(SEALED_GATE_ITEM);
        VASE_ITEM = new ItemMultiTexture(VASE, VASE,
                stack -> BlockVase.Variant.byMeta(stack.getMetadata()).getName()).setRegistryName(VASE.getRegistryName());
        event.getRegistry().register(VASE_ITEM);
        IDOL_SOCKET_ITEM = new ItemBlock(IDOL_SOCKET).setRegistryName(IDOL_SOCKET.getRegistryName());
        event.getRegistry().register(IDOL_SOCKET_ITEM);
        VAULT_BRICK_ITEM = new ItemBlock(VAULT_BRICK).setRegistryName(VAULT_BRICK.getRegistryName());
        event.getRegistry().register(VAULT_BRICK_ITEM);
    }
}
