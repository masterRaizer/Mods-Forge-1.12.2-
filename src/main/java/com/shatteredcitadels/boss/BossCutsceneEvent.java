package com.shatteredcitadels.boss;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.UUID;

/** Cutscene lifecycle hooks on the Forge event bus. */
public abstract class BossCutsceneEvent extends Event {

    private final EntityPlayerMP player;
    private final UUID citadelId;
    private final BlockPos altarPos;

    protected BossCutsceneEvent(EntityPlayerMP player, UUID citadelId, BlockPos altarPos) {
        this.player = player;
        this.citadelId = citadelId;
        this.altarPos = altarPos;
    }

    public EntityPlayerMP getPlayer() { return player; }
    public UUID getCitadelId() { return citadelId; }
    public BlockPos getAltarPos() { return altarPos; }

    public static class Started extends BossCutsceneEvent {
        public Started(EntityPlayerMP player, UUID citadelId, BlockPos altarPos) {
            super(player, citadelId, altarPos);
        }
    }

    /** The cutscene ended normally, was skipped, or was cancelled (left arena / logged out). */
    public static class Finished extends BossCutsceneEvent {
        private final boolean cancelled;

        public Finished(EntityPlayerMP player, UUID citadelId, BlockPos altarPos, boolean cancelled) {
            super(player, citadelId, altarPos);
            this.cancelled = cancelled;
        }

        public boolean wasCancelled() { return cancelled; }
    }
}
