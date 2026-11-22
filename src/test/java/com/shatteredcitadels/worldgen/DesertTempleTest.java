package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelProtectionHandler;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import com.shatteredcitadels.worldgen.temple.DesertTempleBuilder;
import com.shatteredcitadels.worldgen.temple.TempleKind;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DesertTempleTest {

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    static CitadelInstance record() {
        return new CitadelInstance(UUID.randomUUID(), "", StructureType.DESERT_TEMPLE, CitadelArchetype.KEEPERS_CITADEL, 0,
                BlockPos.ORIGIN, new StructureBoundingBox(-60, 5, -60, 60, 100, 60));
    }

    static StrongholdLayout layout(long seed) {
        return StrongholdLayout.generate(seed, 0, 48, 0, TempleKind.CATALOGUE);
    }

    @Test
    public void exactlyOneTomb() {
        for (long seed = 1; seed <= 100; seed++) {
            StrongholdLayout layout = layout(seed);
            assertNotNull("seed " + seed, layout);
            int tombs = 0;
            for (StrongholdLayout.Piece piece : layout.pieces()) {
                tombs += piece.kind == TempleKind.TOMB ? 1 : 0;
                assertTrue(piece.box.maxX <= 40 && piece.box.minX >= -40 && piece.box.maxZ <= 40 && piece.box.minZ >= -40);
            }
            assertEquals("seed " + seed, 1, tombs);
        }
    }

    @Test
    public void shellIsClosed() {
        for (long seed = 1; seed <= 4; seed++) {
            StrongholdLayout layout = layout(seed);
            BlockWriter writer = new BlockWriter(new Blueprint());
            new DesertTempleBuilder(writer, record(), new Random(seed), seed % 2 == 0).build(layout);
            for (StrongholdLayout.Piece piece : layout.pieces()) {
                StructureBoundingBox b = piece.box;
                for (int x = b.minX; x <= b.maxX; x++) {
                    for (int y = b.minY; y <= b.maxY; y++) {
                        for (int z = b.minZ; z <= b.maxZ; z++) {
                            if (!writer.isPlanned(x, y, z) || writer.isSolid(x, y, z)) {
                                continue;
                            }
                            for (EnumFacing d : EnumFacing.values()) {
                                assertTrue("seed " + seed + " " + piece.kind + ": open " + x + "," + y + "," + z + " next to raw " + d,
                                        writer.isPlanned(x + d.getXOffset(), y + d.getYOffset(), z + d.getZOffset()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void pharaohTombSealsSarcophagus() {
        CitadelInstance rec = record();
        new DesertTempleBuilder(new BlockWriter(new Blueprint()), rec, new Random(3), true).build(layout(3));
        assertNotNull(rec.getFloor(0));
        assertTrue(rec.getFloor(0).isBossFloor());
        assertNotNull(rec.getBossArenaBounds());
        int gates = 0;
        for (CitadelInstance.GatePoint gate : rec.getGates()) {
            gates += gate.requiredFloor == 0 ? 1 : 0;
        }
        assertTrue("sarcophagus gates " + gates, gates >= 5);
        assertEquals("atum:pharaoh", String.valueOf(rec.getPreferredBoss()));
        assertFalse(rec.isUnprotected());
        StructureBoundingBox arena = rec.getBossArenaBounds();
        assertTrue(CitadelProtectionHandler.protects(rec, new BlockPos((arena.minX + arena.maxX) / 2, arena.minY + 1, (arena.minZ + arena.maxZ) / 2)));
    }

    @Test
    public void plainTombIsUnprotected() {
        CitadelInstance rec = record();
        new DesertTempleBuilder(new BlockWriter(new Blueprint()), rec, new Random(4), false).build(layout(4));
        assertTrue(rec.getFloors().isEmpty());
        assertTrue(rec.isUnprotected());
        assertTrue(CitadelInstance.readFromNBT(rec.writeToNBT()).isUnprotected());
    }

    @Test
    public void planRoundTripAndSpiralReachesStart() {
        com.shatteredcitadels.worldgen.temple.DesertTemplePlan plan =
                new com.shatteredcitadels.worldgen.temple.DesertTemplePlan(300, 66, -200, 7L, 0, true);
        StructurePlan copy = StructurePlans.read(plan.writeToNBT());
        assertEquals(plan.writeToNBT(), copy.writeToNBT());
        BlockWriter writer = new BlockWriter(new Blueprint());
        CitadelInstance temple = copy.build(writer);
        assertNotNull(temple);
        assertEquals(StructureType.DESERT_TEMPLE, temple.getType());
        int start = com.shatteredcitadels.worldgen.temple.DesertTemplePlan.undergroundFloor(66);
        for (int[] step : com.shatteredcitadels.worldgen.catacomb.CatacombBuilder.spiralPath(66, start)) {
            net.minecraft.block.state.IBlockState state = writer.get(300 + step[0], step[1], -200 + step[2]);
            assertTrue("step at " + step[1] + " is " + state, state.getBlock() instanceof net.minecraft.block.BlockStairs);
            assertFalse(writer.isSolid(300 + step[0], step[1] + 1, -200 + step[2]));
            assertFalse(writer.isSolid(300 + step[0], step[1] + 2, -200 + step[2]));
        }
    }

    @Test
    public void onlyDesertSitesReplaced() {
        assertTrue(com.shatteredcitadels.worldgen.temple.ScatteredFeatureGen.isOurs(net.minecraft.init.Biomes.DESERT));
        assertTrue(com.shatteredcitadels.worldgen.temple.ScatteredFeatureGen.isOurs(net.minecraft.init.Biomes.DESERT_HILLS));
        assertFalse(com.shatteredcitadels.worldgen.temple.ScatteredFeatureGen.isOurs(net.minecraft.init.Biomes.JUNGLE));
        assertFalse(com.shatteredcitadels.worldgen.temple.ScatteredFeatureGen.isOurs(net.minecraft.init.Biomes.SWAMPLAND));
        assertFalse(com.shatteredcitadels.worldgen.temple.ScatteredFeatureGen.isOurs(net.minecraft.init.Biomes.ICE_PLAINS));
    }

    @Test
    public void preferredBossFirstThenHuskThenPool() {
        CitadelInstance rec = record();
        rec.setPreferredBoss(new net.minecraft.util.ResourceLocation("atum", "pharaoh"));
        assertEquals("atum:pharaoh", String.valueOf(CitadelInstance.readFromNBT(rec.writeToNBT()).getPreferredBoss()));
        java.util.List<net.minecraft.util.ResourceLocation> order = com.shatteredcitadels.boss.BossFactory.candidateOrder(
                rec.getPreferredBoss(), java.util.Arrays.asList(new net.minecraft.util.ResourceLocation("minecraft", "zombie")));
        assertEquals("atum:pharaoh", order.get(0).toString());
        assertEquals("minecraft:husk", order.get(1).toString());
        assertEquals("minecraft:zombie", order.get(2).toString());
        assertEquals(1, com.shatteredcitadels.boss.BossFactory.candidateOrder(null,
                java.util.Arrays.asList(new net.minecraft.util.ResourceLocation("minecraft", "zombie"))).size());
    }
}
