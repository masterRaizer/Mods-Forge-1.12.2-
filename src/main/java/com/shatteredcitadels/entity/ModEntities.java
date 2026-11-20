package com.shatteredcitadels.entity;

import com.shatteredcitadels.Reference;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

/** The mod's own entities: the catacomb guardian mage and its slowing bolt. */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ModEntities {

    public static final ResourceLocation GUARDIAN_MAGE_ID = new ResourceLocation(Reference.MODID, "guardian_mage");
    public static final ResourceLocation MAGE_BOLT_ID = new ResourceLocation(Reference.MODID, "mage_bolt");

    private ModEntities() {
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().registerAll(
                EntityEntryBuilder.create().entity(EntityGuardianMage.class).id(GUARDIAN_MAGE_ID, 0)
                        .name(Reference.MODID + ".guardian_mage").tracker(64, 3, true).egg(0x2A1A3A, 0xC9A227).build(),
                EntityEntryBuilder.create().entity(EntityMageBolt.class).id(MAGE_BOLT_ID, 1)
                        .name(Reference.MODID + ".mage_bolt").tracker(64, 10, true).build());
    }
}
