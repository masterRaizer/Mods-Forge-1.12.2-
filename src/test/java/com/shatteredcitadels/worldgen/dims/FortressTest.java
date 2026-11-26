package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.Blueprint;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.StructurePlans;
import com.shatteredcitadels.worldgen.TestBlocks;
import com.shatteredcitadels.worldgen.dims.fortress.FortressLayout;
import com.shatteredcitadels.worldgen.dims.fortress.FortressPlan;
import com.shatteredcitadels.worldgen.dims.fortress.FortressSpawns;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FortressTest {

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    private static int count(BlockWriter writer, StructureBoundingBox box, Block block) {
        int n = 0;
        for (int x = box.minX; x <= box.maxX; x++) {
            for (int y = box.minY; y <= box.maxY; y++) {
                for (int z = box.minZ; z <= box.maxZ; z++) {
                    n += writer.get(x, y, z).getBlock() == block ? 1 : 0;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ layout

    @Test
    public void sameSeedSameFortress() {
        for (long seed = 1; seed <= 20; seed++) {
            FortressLayout a = FortressLayout.generate(seed, 100, 60, -200, 3);
            FortressLayout b = FortressLayout.generate(seed, 100, 60, -200, 3);
            assertEquals(a.nodes().size(), b.nodes().size());
            assertEquals(a.edges().size(), b.edges().size());
            for (int i = 0; i < a.nodes().size(); i++) {
                assertEquals(a.nodes().get(i).x, b.nodes().get(i).x);
                assertEquals(a.nodes().get(i).z, b.nodes().get(i).z);
                assertEquals(a.nodes().get(i).kind(), b.nodes().get(i).kind());
            }
            assertEquals(a.boxes().toString(), b.boxes().toString());
        }
        assertFalse(FortressLayout.generate(1L, 0, 60, 0, 3).boxes().toString()
                .equals(FortressLayout.generate(2L, 0, 60, 0, 3).boxes().toString()));
    }

    @Test
    public void piecesDoNotOverlapAndStayInReach() {
        for (long seed = 1; seed <= 200; seed++) {
            FortressLayout layout = FortressLayout.generate(seed, 40, 58, -90, 3);
            List<StructureBoundingBox> boxes = layout.boxes();
            assertEquals(layout.nodes().size() + layout.edges().size(), boxes.size());
            for (int i = 0; i < boxes.size(); i++) {
                StructureBoundingBox a = boxes.get(i);
                assertTrue("seed " + seed + " empty box " + a, a.minX <= a.maxX && a.minY <= a.maxY && a.minZ <= a.maxZ);
                assertTrue("seed " + seed + " out of reach " + a, a.minX >= 40 - FortressLayout.REACH
                        && a.maxX <= 40 + FortressLayout.REACH && a.minZ >= -90 - FortressLayout.REACH
                        && a.maxZ <= -90 + FortressLayout.REACH);
                for (int j = i + 1; j < boxes.size(); j++) {
                    assertFalse("seed " + seed + ": " + a + " overlaps " + boxes.get(j), a.intersectsWith(boxes.get(j)));
                }
            }
        }
    }

    @Test
    public void everyNodeIsReachableFromTheKeep() {
        for (long seed = 1; seed <= 200; seed++) {
            FortressLayout layout = FortressLayout.generate(seed, 0, 60, 0, 3);
            assertTrue("seed " + seed + " nodes " + layout.nodes().size(), layout.nodes().size() >= 12);
            assertEquals(FortressLayout.Kind.KEEP, layout.keep().kind());
            assertEquals(1, layout.count(FortressLayout.Kind.KEEP));
            Set<FortressLayout.Node> seen = new HashSet<>();
            Deque<FortressLayout.Node> queue = new ArrayDeque<>();
            queue.add(layout.keep());
            seen.add(layout.keep());
            while (!queue.isEmpty()) {
                FortressLayout.Node node = queue.poll();
                for (FortressLayout.Edge edge : layout.edges()) {
                    FortressLayout.Node other = edge.from == node ? edge.to : edge.to == node ? edge.from : null;
                    if (other != null && seen.add(other)) {
                        queue.add(other);
                    }
                }
            }
            assertEquals("seed " + seed, layout.nodes().size(), seen.size());
            for (FortressLayout.Edge edge : layout.edges()) {
                int cells = Math.abs(edge.from.cellX - edge.to.cellX) + Math.abs(edge.from.cellZ - edge.to.cellZ);
                assertEquals("edges join neighbours", 1, cells);
            }
        }
    }

    @Test
    public void blazeSpawnersAreBounded() {
        for (long seed = 1; seed <= 100; seed++) {
            for (int blaze = 0; blaze <= FortressLayout.MAX_BLAZE; blaze++) {
                FortressLayout layout = FortressLayout.generate(seed, 0, 60, 0, blaze);
                assertEquals("seed " + seed, blaze, layout.count(FortressLayout.Kind.BLAZE));
                assertTrue("seed " + seed + " no wart room", layout.count(FortressLayout.Kind.WART) >= 1);
            }
            assertEquals(FortressLayout.MAX_BLAZE, FortressLayout.generate(seed, 0, 60, 0, 99).count(FortressLayout.Kind.BLAZE));
            assertEquals(0, FortressLayout.generate(seed, 0, 60, 0, -5).count(FortressLayout.Kind.BLAZE));
        }
    }

    // ------------------------------------------------------------------ plan and builder

    @Test
    public void planKeepsTheDeckBetweenLavaAndRoof() {
        for (long seed = -50; seed <= 50; seed++) {
            FortressPlan plan = FortressPlan.plan(-1, 3, -4, seed, 3);
            assertTrue("deck " + plan.deckY(), plan.deckY() >= FortressPlan.MIN_DECK_Y
                    && plan.deckY() < FortressPlan.MIN_DECK_Y + FortressPlan.DECK_SPAN);
            StructureBoundingBox footprint = plan.footprint();
            assertTrue("roof " + footprint.maxY, footprint.maxY < GroundFinder.NETHER_ROOF);
            assertTrue(footprint.minY >= BlockWriter.MIN_Y);
            for (StructureBoundingBox box : plan.layout().boxes()) {
                assertTrue(box + " outside " + footprint, box.minX >= footprint.minX && box.maxX <= footprint.maxX
                        && box.minY >= footprint.minY && box.maxY <= footprint.maxY && box.minZ >= footprint.minZ
                        && box.maxZ <= footprint.maxZ);
            }
        }
    }

    @Test
    public void planSurvivesSaving() {
        FortressPlan plan = FortressPlan.plan(-1, 12, 34, 987654321L, 2);
        NBTTagCompound tag = plan.writeToNBT();
        StructurePlan read = StructurePlans.read(tag);
        assertTrue(read instanceof FortressPlan);
        assertEquals(tag, read.writeToNBT());
        assertEquals(plan.footprint().toString(), read.footprint().toString());
        assertEquals(plan.describe(), read.describe());
        assertEquals(-1, read.dimension());
        // A save without the newer fields still reads, with the defaults.
        tag.removeTag("blaze");
        tag.removeTag("dim");
        FortressPlan old = FortressPlan.read(tag);
        assertEquals(3, old.blaze());
        assertEquals(-1, old.dimension());
    }

    @Test
    public void builtFortressHasItsVanillaParts() {
        for (long seed = 1; seed <= 6; seed++) {
            FortressPlan plan = FortressPlan.plan(-1, 0, 0, seed, 3);
            BlockWriter writer = new BlockWriter(new Blueprint());
            CitadelInstance record = plan.build(writer);
            FortressLayout layout = plan.layout();
            assertEquals(StructureType.NETHER_FORTRESS, record.getType());
            assertTrue(record.isUnprotected());
            assertEquals(layout.boxes().size(), record.getParts().size());
            assertEquals("seed " + seed + " blaze spawners", 3, count(writer, plan.footprint(), Blocks.MOB_SPAWNER));
            assertTrue("seed " + seed + " nether wart", count(writer, plan.footprint(), Blocks.NETHER_WART) >= 16);
            int turrets = layout.count(FortressLayout.Kind.TURRET);
            assertEquals("seed " + seed + " chests", 4 + turrets, count(writer, plan.footprint(), Blocks.CHEST));
            assertEquals(4 + turrets, record.getChestPoints().size());
            int deck = plan.deckY();
            for (FortressLayout.Node node : layout.nodes()) {
                assertTrue("deck under " + node.kind(), writer.isSolid(node.x, deck, node.z));
                for (int side = 0; side < 4; side++) {
                    if (!node.doors[side]) {
                        continue;
                    }
                    int dx = side == 1 ? node.half() : side == 3 ? -node.half() : 0;
                    int dz = side == 0 ? -node.half() : side == 2 ? node.half() : 0;
                    for (int up = 1; up <= 2; up++) {
                        assertFalse("seed " + seed + " blocked door of " + node.kind() + " side " + side,
                                writer.isSolid(node.x + dx, deck + up, node.z + dz));
                    }
                }
            }
            for (FortressLayout.Edge edge : layout.edges()) {
                StructureBoundingBox box = layout.edgeBox(edge);
                int midX = (box.minX + box.maxX) / 2;
                int midZ = (box.minZ + box.maxZ) / 2;
                assertTrue("bridge deck", writer.isSolid(midX, deck, midZ));
                assertFalse("bridge way", writer.isSolid(midX, deck + 1, midZ));
                assertFalse("bridge way", writer.isSolid(midX, deck + 2, midZ));
            }
            // Nothing is written outside the footprint the job reserves.
            StructureBoundingBox f = plan.footprint();
            StructureBoundingBox ring = new StructureBoundingBox(f.minX - 3, f.minY, f.minZ - 3, f.maxX + 3, f.maxY + 3, f.maxZ + 3);
            int inside = writer.getBlueprint().blockCount();
            int counted = 0;
            for (int x = ring.minX; x <= ring.maxX; x++) {
                for (int z = ring.minZ; z <= ring.maxZ; z++) {
                    for (int y = ring.minY; y <= ring.maxY; y++) {
                        if (writer.isPlanned(x, y, z)) {
                            counted++;
                            assertTrue("block outside the footprint at " + x + "," + y + "," + z, f.isVecInside(new BlockPos(x, y, z)));
                        }
                    }
                }
            }
            assertEquals(inside, counted);
        }
    }

    // ------------------------------------------------------------------ spawn bounds and sites

    @Test
    public void fortressMobsSpawnOnlyInsideThePieces() {
        FortressLayout layout = FortressLayout.generate(7L, 0, 60, 0, 3);
        List<StructureBoundingBox> boxes = layout.boxes();
        FortressLayout.Node keep = layout.keep();
        assertTrue(FortressSpawns.inside(boxes, new BlockPos(keep.x, 61, keep.z)));
        assertTrue(FortressSpawns.inside(boxes, new BlockPos(keep.x + FortressLayout.KEEP_HALF, 61, keep.z)));
        assertFalse(FortressSpawns.inside(boxes, new BlockPos(keep.x, 61 + FortressLayout.KEEP_HEIGHT, keep.z)));
        assertFalse(FortressSpawns.inside(boxes, new BlockPos(keep.x, 40, keep.z)));
        assertFalse(FortressSpawns.inside(boxes, new BlockPos(FortressLayout.REACH + 1, 61, 0)));
        assertFalse(FortressSpawns.inside(Collections.<StructureBoundingBox>emptyList(), new BlockPos(0, 61, 0)));
        FortressLayout.Edge edge = layout.edges().get(0);
        StructureBoundingBox bridge = layout.edgeBox(edge);
        assertTrue(FortressSpawns.inside(boxes, new BlockPos(bridge.minX, 61, bridge.minZ)));
        // Beside a bridge, over the lava: not fortress ground.
        int besideX = edge.alongX() ? (bridge.minX + bridge.maxX) / 2 : bridge.maxX + 2;
        int besideZ = edge.alongX() ? bridge.maxZ + 2 : (bridge.minZ + bridge.maxZ) / 2;
        assertFalse(FortressSpawns.inside(boxes, new BlockPos(besideX, 61, besideZ)));
    }

    @Test
    public void sitesFollowTheVanillaRegions() {
        long seed = 31337L;
        int found = 0;
        for (int rx = -10; rx < 10; rx++) {
            for (int rz = -10; rz < 10; rz++) {
                int[] site = FortressSpawns.siteInRegion(seed, rx, rz);
                if (site == null) {
                    for (int x = rx << 4; x < (rx << 4) + 16; x += 5) {
                        assertFalse(FortressSpawns.isSite(seed, x, (rz << 4) + 6));
                    }
                    continue;
                }
                found++;
                assertEquals(rx, site[0] >> 4);
                assertEquals(rz, site[1] >> 4);
                assertTrue((site[0] & 15) >= 4 && (site[0] & 15) < 12 && (site[1] & 15) >= 4 && (site[1] & 15) < 12);
                assertTrue(FortressSpawns.isSite(seed, site[0], site[1]));
                assertFalse(FortressSpawns.isSite(seed, site[0] + 1, site[1]));
            }
        }
        assertTrue("about a third of 400 regions: " + found, found > 90 && found < 180);
    }

    @Test
    public void nearestSiteIsFoundWithoutLoadingAnything() {
        long seed = 31337L;
        int[] near = FortressSpawns.nearestSite(seed, 5, 5, 12);
        assertNotNull(near);
        assertTrue(FortressSpawns.isSite(seed, near[0], near[1]));
        long best = (long) (near[0] - 5) * (near[0] - 5) + (long) (near[1] - 5) * (near[1] - 5);
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                int[] site = FortressSpawns.siteInRegion(seed, rx, rz);
                if (site != null) {
                    long d = (long) (site[0] - 5) * (site[0] - 5) + (long) (site[1] - 5) * (site[1] - 5);
                    assertTrue(d >= best);
                }
            }
        }
        assertNull(FortressSpawns.nearestSite(seed, 5, 5, -1));
    }
}
