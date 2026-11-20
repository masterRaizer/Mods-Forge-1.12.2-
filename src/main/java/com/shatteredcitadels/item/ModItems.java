package com.shatteredcitadels.item;

import com.shatteredcitadels.Reference;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The four relics of the Shattered Crown. */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ModItems {

    public static final ResourceLocation CROWN_SHARD_ID = new ResourceLocation(Reference.MODID, "crown_shard");
    public static final ResourceLocation CLOUDSPIRE_AEGIS_ID = new ResourceLocation(Reference.MODID, "cloudspire_aegis");
    public static final ResourceLocation GOBLIN_FORGE_HEART_ID = new ResourceLocation(Reference.MODID, "goblin_forge_heart");
    public static final ResourceLocation ARCHITECT_BLADE_ID = new ResourceLocation(Reference.MODID, "architect_blade");
    public static final ResourceLocation GUARDIAN_STAFF_ID = new ResourceLocation(Reference.MODID, "guardian_staff");
    public static final ResourceLocation GUARDIAN_FOLIO_ID = new ResourceLocation(Reference.MODID, "guardian_folio");
    public static final ResourceLocation ASHEN_SIGHT_RING_ID = new ResourceLocation(Reference.MODID, "ashen_sight_ring");

    public static Item CROWN_SHARD;
    public static Item CLOUDSPIRE_AEGIS;
    public static Item GOBLIN_FORGE_HEART;
    public static Item ARCHITECT_BLADE;
    public static Item GUARDIAN_STAFF;
    public static Item GUARDIAN_FOLIO;
    public static Item ASHEN_SIGHT_RING;

    private ModItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        CROWN_SHARD = relic(RelicKind.CROWN_SHARD, CROWN_SHARD_ID);
        CLOUDSPIRE_AEGIS = relic(RelicKind.CLOUDSPIRE_AEGIS, CLOUDSPIRE_AEGIS_ID);
        GOBLIN_FORGE_HEART = relic(RelicKind.GOBLIN_FORGE_HEART, GOBLIN_FORGE_HEART_ID);
        ARCHITECT_BLADE = new ItemArchitectBlade().setRegistryName(ARCHITECT_BLADE_ID)
                .setTranslationKey(Reference.MODID + "." + ARCHITECT_BLADE_ID.getPath());
        GUARDIAN_STAFF = new ItemGuardianStaff().setRegistryName(GUARDIAN_STAFF_ID)
                .setTranslationKey(Reference.MODID + "." + GUARDIAN_STAFF_ID.getPath());
        GUARDIAN_FOLIO = relic(RelicKind.GUARDIAN_FOLIO, GUARDIAN_FOLIO_ID);
        ASHEN_SIGHT_RING = relic(RelicKind.ASHEN_SIGHT_RING, ASHEN_SIGHT_RING_ID);
        event.getRegistry().registerAll(CROWN_SHARD, CLOUDSPIRE_AEGIS, GOBLIN_FORGE_HEART, ARCHITECT_BLADE,
                GUARDIAN_STAFF, GUARDIAN_FOLIO, ASHEN_SIGHT_RING);
    }

    private static Item relic(RelicKind kind, ResourceLocation id) {
        return new ItemRelic(kind).setRegistryName(id).setTranslationKey(Reference.MODID + "." + id.getPath());
    }

    public static Item[] all() {
        return new Item[]{CROWN_SHARD, CLOUDSPIRE_AEGIS, GOBLIN_FORGE_HEART, ARCHITECT_BLADE, GUARDIAN_STAFF, GUARDIAN_FOLIO,
                ASHEN_SIGHT_RING};
    }
}
