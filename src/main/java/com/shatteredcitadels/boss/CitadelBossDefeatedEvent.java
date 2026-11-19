package com.shatteredcitadels.boss;

import com.shatteredcitadels.structure.CitadelInstance;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Posted on the Forge event bus when a citadel boss dies. Achievements/quests (stage 6) and other
 * mods subscribe to this hook; participants are the players inside the arena at the kill.
 */
public class CitadelBossDefeatedEvent extends Event {

    private final CitadelInstance citadel;
    private final EntityLivingBase boss;
    private final List<EntityPlayerMP> participants;
    private final Set<UUID> fallenPlayers;

    public CitadelBossDefeatedEvent(CitadelInstance citadel, EntityLivingBase boss, List<EntityPlayerMP> participants,
                                    Set<UUID> fallenPlayers) {
        this.citadel = citadel;
        this.boss = boss;
        this.participants = Collections.unmodifiableList(participants);
        this.fallenPlayers = Collections.unmodifiableSet(fallenPlayers);
    }

    /** @return true if the player took part without dying during the fight. */
    public boolean isFlawless(EntityPlayerMP player) {
        return !fallenPlayers.contains(player.getUniqueID());
    }

    public CitadelInstance getCitadel() { return citadel; }
    public EntityLivingBase getBoss() { return boss; }
    public List<EntityPlayerMP> getParticipants() { return participants; }
}
