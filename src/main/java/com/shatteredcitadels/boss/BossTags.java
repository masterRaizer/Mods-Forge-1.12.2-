package com.shatteredcitadels.boss;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.UUID;

/** Persistent-data keys identifying citadel bosses and their minions. */
public final class BossTags {

    public static final String BOSS = "ShatteredCitadelBoss";
    public static final String MINION = "ShatteredCitadelMinion";
    public static final String ALTAR = "ShatteredCitadelAltar";
    public static final String PHASE = "ShatteredCitadelBossPhase";
    public static final String SCOREBOARD_BOSS = "shatteredcitadels_boss";
    public static final String SCOREBOARD_MINION = "shatteredcitadels_minion";

    private BossTags() {
    }

    public static boolean isBoss(Entity entity) {
        return entity != null && entity.getEntityData().hasKey(BOSS, Constants.NBT.TAG_STRING);
    }

    public static boolean isMinion(Entity entity) {
        return entity != null && entity.getEntityData().hasKey(MINION, Constants.NBT.TAG_STRING);
    }

    /** @return the altar that owns this boss or minion, or null. */
    public static BlockPos altarOf(Entity entity) {
        return entity != null && entity.getEntityData().hasKey(ALTAR, Constants.NBT.TAG_LONG)
                ? BlockPos.fromLong(entity.getEntityData().getLong(ALTAR)) : null;
    }

    public static void markBoss(Entity entity, UUID citadelId, BlockPos altar) {
        entity.getEntityData().setString(BOSS, citadelId == null ? "" : citadelId.toString());
        entity.getEntityData().setLong(ALTAR, altar.toLong());
        entity.addTag(SCOREBOARD_BOSS);
    }

    public static void markMinion(Entity entity, UUID citadelId, BlockPos altar) {
        entity.getEntityData().setString(MINION, citadelId == null ? "" : citadelId.toString());
        entity.getEntityData().setLong(ALTAR, altar.toLong());
        entity.addTag(SCOREBOARD_MINION);
    }
}
