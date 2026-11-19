package com.shatteredcitadels.boss;

import com.shatteredcitadels.structure.CitadelInstance;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Collections;
import java.util.List;

/**
 * Posted on the Forge event bus when a citadel boss dies. Achievements/quests (stage 6) and other
 * mods subscribe to this hook; participants are the players inside the arena at the kill.
 */
public class CitadelBossDefeatedEvent extends Event {

    private final CitadelInstance citadel;
    private final EntityLivingBase boss;
    private final List<EntityPlayerMP> participants;

    public CitadelBossDefeatedEvent(CitadelInstance citadel, EntityLivingBase boss, List<EntityPlayerMP> participants) {
        this.citadel = citadel;
        this.boss = boss;
        this.participants = Collections.unmodifiableList(participants);
    }

    public CitadelInstance getCitadel() { return citadel; }
    public EntityLivingBase getBoss() { return boss; }
    public List<EntityPlayerMP> getParticipants() { return participants; }
}
