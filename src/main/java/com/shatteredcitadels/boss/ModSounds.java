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

    /** Herobrine answers the altar. */
    public static SoundEvent HEROBRINE_APPEAR;
    public static SoundEvent HEROBRINE_TELEPORT;
    /** The warning before his bolt. */
    public static SoundEvent HEROBRINE_LIGHTNING;
    public static SoundEvent HEROBRINE_SUMMON;
    public static SoundEvent HEROBRINE_PHASE;
    public static SoundEvent HEROBRINE_DEATH;
    /** The distant figure; sent only to the player it is shown to. */
    public static SoundEvent HEROBRINE_APPARITION;
    /** A quiet whisper near a lair; sent only to the player who stands there. */
    public static SoundEvent HEROBRINE_WHISPER;

    private ModSounds() {
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> event) {
        BOSS_SHIELD_ACTIVATE = create("boss.shield_activate");
        BOSS_SHIELD_CHARGE = create("boss.shield_charge");
        HEROBRINE_APPEAR = create("herobrine.appear");
        HEROBRINE_TELEPORT = create("herobrine.teleport");
        HEROBRINE_LIGHTNING = create("herobrine.lightning");
        HEROBRINE_SUMMON = create("herobrine.summon");
        HEROBRINE_PHASE = create("herobrine.phase");
        HEROBRINE_DEATH = create("herobrine.death");
        HEROBRINE_APPARITION = create("herobrine.apparition");
        HEROBRINE_WHISPER = create("herobrine.whisper");
        event.getRegistry().registerAll(BOSS_SHIELD_ACTIVATE, BOSS_SHIELD_CHARGE, HEROBRINE_APPEAR, HEROBRINE_TELEPORT,
                HEROBRINE_LIGHTNING, HEROBRINE_SUMMON, HEROBRINE_PHASE, HEROBRINE_DEATH, HEROBRINE_APPARITION, HEROBRINE_WHISPER);
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(Reference.MODID, name);
        return new SoundEvent(id).setRegistryName(id);
    }
}
