package com.shatteredcitadels.item;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GuardianStaffTest {

    @Test
    public void cooldownWithFolio() {
        assertEquals(60, ItemGuardianStaff.COOLDOWN_TICKS);
        assertEquals(45, ItemGuardianStaff.cooldownFor(60, true));
        assertEquals(60, ItemGuardianStaff.cooldownFor(60, false));
    }
}
