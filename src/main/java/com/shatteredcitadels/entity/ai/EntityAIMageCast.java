package com.shatteredcitadels.entity.ai;

import com.shatteredcitadels.entity.EntityGuardianMage;
import com.shatteredcitadels.entity.MageSpellCycle;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/** Casts a spell whenever the cooldown of the previous one has run out. */
public class EntityAIMageCast extends EntityAIBase {

    private final EntityGuardianMage mage;
    private int cooldown = 40;

    public EntityAIMageCast(EntityGuardianMage mage) {
        this.mage = mage;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mage.getAttackTarget();
        return target != null && target.isEntityAlive();
    }

    @Override
    public void updateTask() {
        if (--cooldown > 0) {
            return;
        }
        EntityLivingBase target = mage.getAttackTarget();
        if (target == null) {
            return;
        }
        MageSpellCycle.Spell spell = MageSpellCycle.next(mage.getRNG(), mage.aliveGuards(), mage.maxGuards());
        boolean needsSight = spell == MageSpellCycle.Spell.FIREBALLS || spell == MageSpellCycle.Spell.SLOW_BOLT;
        if (needsSight && !mage.canEntityBeSeen(target)) {
            spell = MageSpellCycle.Spell.LIGHTNING;
        }
        mage.cast(spell, target);
        cooldown = MageSpellCycle.cooldownAfter(spell);
    }
}
