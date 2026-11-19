package com.shatteredcitadels.boss;

import com.shatteredcitadels.Reference;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The mod's own sound events, for sounds Minecraft 1.12.2 does not have (e.g. the 1.13+ beacon
 * sounds). {@code assets/shatteredcitadels/sounds.json} maps them to 1.12.2 audio for now; a
 * resource pack or a later version can point them at dedicated .ogg files without code changes.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ModSounds {

    /** Arcane Shield goes up. */
    public static SoundEvent BOSS_SHIELD_ACTIVATE;
    /** Telegraph of the Arcane Shield. */
    public static SoundEvent BOSS_SHIELD_CHARGE;

    private ModSounds() {
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> event) {
        BOSS_SHIELD_ACTIVATE = create("boss.shield_activate");
        BOSS_SHIELD_CHARGE = create("boss.shield_charge");
        event.getRegistry().registerAll(BOSS_SHIELD_ACTIVATE, BOSS_SHIELD_CHARGE);
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(Reference.MODID, name);
        return new SoundEvent(id).setRegistryName(id);
    }
}
