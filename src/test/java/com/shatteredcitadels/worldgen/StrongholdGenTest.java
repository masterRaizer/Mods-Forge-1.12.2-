package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.stronghold.ShatteredStrongholdGen;
import com.shatteredcitadels.worldgen.stronghold.StrongholdPlan;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;

import net.minecraft.util.math.BlockPos;

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

    /** Other structures keep off stronghold sites, so the eye never leads to an empty site. */
    @Test
    public void structuresKeepOffStrongholdSites() {
        BlockPos site = new BlockPos(1000, 32, 1000);
        assertTrue(com.shatteredcitadels.worldgen.stronghold.StrongholdSites.conflicts(site,
                new net.minecraft.world.gen.structure.StructureBoundingBox(1060, 5, 1060, 1100, 120, 1100)));
        assertFalse(com.shatteredcitadels.worldgen.stronghold.StrongholdSites.conflicts(site,
                new net.minecraft.world.gen.structure.StructureBoundingBox(1200, 5, 1200, 1250, 120, 1250)));
    }

    /** A vanilla stronghold already started in an older world keeps the site: ours is not built over it. */
    @Test
    public void legacyVanillaStartBlocksOurs() {
        LegacyGen gen = new LegacyGen();
        StrongholdPlan plan = new StrongholdPlan(1000, 30, 1000, 5L, 0);
        assertFalse(gen.hasLegacyStartIn(plan.footprint()));
        gen.addStart(new net.minecraft.world.gen.structure.StructureBoundingBox(990, 20, 990, 1010, 40, 1010));
        assertTrue(gen.hasLegacyStartIn(plan.footprint()));
    }

    static final class LegacyGen extends ShatteredStrongholdGen {
        void addStart(net.minecraft.world.gen.structure.StructureBoundingBox box) {
            net.minecraft.world.gen.structure.StructureStart start = new net.minecraft.world.gen.structure.StructureStart() {
                {
                    boundingBox = box;
                }
            };
            structureMap.put(1L, start);
        }
    }

    /** The cistern's sunken pool lies inside its own box, so no other piece can sit in it. */
    @Test
    public void cisternPoolInsideItsBox() {
        int cisterns = 0;
        for (long seed = 1; seed <= 30; seed++) {
            com.shatteredcitadels.worldgen.stronghold.StrongholdLayout layout =
                    com.shatteredcitadels.worldgen.stronghold.StrongholdLayout.generate(seed, 0, 30, 0);
            BlockWriter writer = StrongholdBuilderTest.built(layout, seed);
            for (com.shatteredcitadels.worldgen.stronghold.StrongholdLayout.Piece piece : layout.pieces()) {
                if (piece.kind != com.shatteredcitadels.worldgen.stronghold.PieceKind.CISTERN) {
                    continue;
                }
                cisterns++;
                assertEquals(piece.floorY - 2, piece.box.minY);
                for (int x = piece.box.minX; x <= piece.box.maxX; x++) {
                    for (int z = piece.box.minZ; z <= piece.box.maxZ; z++) {
                        assertTrue("pool floor", !writer.get(x, piece.box.minY, z).getMaterial().isLiquid());
                        assertTrue("room floor level stays solid at the walls",
                                writer.isSolid(piece.box.minX, piece.floorY, z));
                    }
                }
            }
        }
        assertTrue(cisterns > 0);
    }

    @Test
    public void treasureHasAButtonInside() {
        for (long seed = 1; seed <= 30; seed++) {
            com.shatteredcitadels.worldgen.stronghold.StrongholdLayout layout =
                    com.shatteredcitadels.worldgen.stronghold.StrongholdLayout.generate(seed, 0, 30, 0);
            BlockWriter writer = StrongholdBuilderTest.built(layout, seed);
            for (com.shatteredcitadels.worldgen.stronghold.StrongholdLayout.Piece piece : layout.pieces()) {
                if (piece.kind != com.shatteredcitadels.worldgen.stronghold.PieceKind.TREASURE) {
                    continue;
                }
                boolean inside = false;
                net.minecraft.world.gen.structure.StructureBoundingBox b = piece.box;
                for (int x = b.minX; x <= b.maxX; x++) {
                    for (int y = b.minY; y <= b.maxY; y++) {
                        for (int z = b.minZ; z <= b.maxZ; z++) {
                            inside |= writer.get(x, y, z).getBlock() == net.minecraft.init.Blocks.STONE_BUTTON;
                        }
                    }
                }
                assertTrue("seed " + seed + ": no way out of the treasure room", inside);
                return;
            }
        }
    }
}
