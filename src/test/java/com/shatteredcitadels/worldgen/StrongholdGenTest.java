package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.stronghold.ShatteredStrongholdGen;
import com.shatteredcitadels.worldgen.stronghold.StrongholdPlan;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrongholdGenTest {

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    /** No vanilla pieces, but the vanilla site lookup stays: the eye (and old worlds' strongholds) keep working. */
    @Test
    public void noVanillaPiecesButSitesKept() {
        ShatteredStrongholdGen gen = new ShatteredStrongholdGen();
        for (int x = -40; x <= 40; x += 7) {
            for (int z = -40; z <= 40; z += 7) {
                assertFalse(gen.canSpawnStructureAtCoords(x, z));
            }
        }
        assertEquals("Stronghold", gen.getStructureName());
        for (Method method : ShatteredStrongholdGen.class.getDeclaredMethods()) {
            assertFalse("site lookup must stay vanilla", method.getName().equals("getNearestStructurePos"));
        }
    }

    @Test
    public void duplicateSiteIsNotQueuedTwice() {
        StructureJobs jobs = new StructureJobs("test_jobs_stronghold");
        StrongholdPlan plan = new StrongholdPlan(1000, 30, 1000, 5L, 0);
        jobs.submit(plan);
        assertTrue(jobs.isReservedForStructures(new StrongholdPlan(1000, 30, 1000, 6L, 0).footprint()));
    }

    /** The portal room's chunks are written first, so it stands by the time a player digs down to the centre. */
    @Test
    public void priorityChunksGoFirst() {
        assertTrue(StructureJobs.rank(1000.0D, true) < StructureJobs.rank(0.0D, false));
        assertTrue(StructureJobs.rank(5.0D, false) < StructureJobs.rank(10.0D, false));
        StrongholdPlan plan = new StrongholdPlan(1000, 30, 1000, 5L, 0);
        assertTrue(plan.priorityArea() != null && plan.footprint().intersectsWith(plan.priorityArea()));
    }
}
