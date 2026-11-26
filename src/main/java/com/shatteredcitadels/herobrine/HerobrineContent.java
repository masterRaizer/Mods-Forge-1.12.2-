package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.item.ItemRelic;
import com.shatteredcitadels.item.RelicKind;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Everything the Herobrine part registers: its two entities, the relic, the lair generator and loot. */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class HerobrineContent {

    public static final ResourceLocation HEROBRINE_ID = new ResourceLocation(Reference.MODID, "herobrine");
    public static final ResourceLocation APPARITION_ID = new ResourceLocation(Reference.MODID, "herobrine_apparition");
    public static final ResourceLocation EYE_ID = new ResourceLocation(Reference.MODID, "herobrine_eye");
    /** Network ids of the entities inside this mod; far from the ids of the other parts. */
    private static final int HEROBRINE_NET_ID = 40;
    private static final int APPARITION_NET_ID = 41;

    private static Item eye;

    private HerobrineContent() {
    }

    /** @return the Eye of Herobrine, or null before the items are registered */
    public static Item eye() {
        return eye;
    }

    /** Called once from the common proxy during init. */
    public static void init() {
        GameRegistry.registerWorldGenerator(new HerobrineLairGenerator(), HerobrineLairGenerator.WEIGHT);
        LootTableList.register(HerobrineLairPlan.LOOT);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        eye = new ItemRelic(RelicKind.HEROBRINE_EYE).setRegistryName(EYE_ID)
                .setTranslationKey(Reference.MODID + "." + EYE_ID.getPath());
        event.getRegistry().register(eye);
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().registerAll(
                EntityEntryBuilder.create().entity(EntityHerobrine.class).id(HEROBRINE_ID, HEROBRINE_NET_ID)
                        .name(Reference.MODID + ".herobrine").tracker(80, 3, true).build(),
                EntityEntryBuilder.create().entity(EntityHerobrineApparition.class).id(APPARITION_ID, APPARITION_NET_ID)
                        .name(Reference.MODID + ".herobrine_apparition").tracker(96, 10, false).build());
    }
}
