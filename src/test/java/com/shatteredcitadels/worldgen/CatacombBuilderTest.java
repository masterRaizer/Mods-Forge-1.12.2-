package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.BlockSealedGate;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.catacomb.CatacombBuilder;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CatacombBuilderTest {

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
        if (ModBlocks.SEALED_GATE == null) {
            ModBlocks.SEALED_GATE = new BlockSealedGate();
        }
    }

    static CitadelInstance record() {
        return new CitadelInstance(UUID.randomUUID(), "", StructureType.CITADEL, CitadelArchetype.KEEPERS_CITADEL, 0,
                new BlockPos(0, 64, 0), new StructureBoundingBox(-60, 5, -60, 60, 120, 60));
    }

    static CatacombBuilder builder(BlockWriter writer, CitadelInstance rec, long seed) {
        return new CatacombBuilder(writer, rec, ModPaletteRegistry.quiet(CitadelArchetype.KEEPERS_CITADEL), new Random(seed), 2);
    }

    @Test
    public void buildsThreeLevelsWithChainedFloors() {
        CitadelInstance rec = record();
        int built = builder(new BlockWriter(new Blueprint()), rec, 1L).build(0, 0, 54, 3, 0, 0, 64);
        assertEquals(3, built);
        for (int k = 1; k <= 3; k++) {
            assertNotNull(rec.getFloor(-k));
            assertEquals("catacombs_level_" + k, rec.getFloor(-k).getZoneKey());
        }
        assertEquals(Integer.MIN_VALUE, rec.getFloor(-1).getRequiresFloor());
        assertEquals(-1, rec.getFloor(-2).getRequiresFloor());
        assertEquals(-2, rec.getFloor(-3).getRequiresFloor());
        assertEquals(3, rec.getFloor(-1).getDifficultyTier());
        assertEquals(5, rec.getFloor(-3).getDifficultyTier());
        assertTrue(rec.isInPart(new BlockPos(0, 50, 0)));
    }

    /** Level k sits at topLevelY - 10k and must stay at y 6 or above. */
    @Test
    public void stopsAboveWorldBottom() {
        CitadelInstance rec = record();
        assertEquals(2, builder(new BlockWriter(new Blueprint()), rec, 2L).build(0, 0, 20, 3, 0, 0, 40));
        assertNull(rec.getFloor(-3));
        CitadelInstance none = record();
        assertEquals(0, builder(new BlockWriter(new Blueprint()), none, 2L).build(0, 0, 5, 3, 0, 0, 40));
        assertTrue(none.getFloors().isEmpty());
    }

    @Test
    public void spiralConnectsEntranceToLevelZero() {
        BlockWriter writer = new BlockWriter(new Blueprint());
        builder(writer, record(), 3L).build(0, 0, 54, 2, 7, -4, 64);
        List<int[]> path = CatacombBuilder.spiralPath(64, 54);
        assertTrue(path.size() >= 9);
        assertEquals(64, path.get(0)[1]);
        assertEquals(55, path.get(path.size() - 1)[1]);
        for (int[] step : path) {
            int x = 7 + step[0];
            int z = -4 + step[2];
            IBlockState stair = writer.get(x, step[1], z);
            assertTrue("step at " + step[1] + " is " + stair, stair.getBlock() instanceof BlockStairs);
            assertTrue(writer.get(x, step[1] + 1, z).getMaterial().isReplaceable());
            assertTrue(writer.get(x, step[1] + 2, z).getMaterial().isReplaceable());
        }
    }

    @Test
    public void downStairIsGated() {
        CitadelInstance rec = record();
        builder(new BlockWriter(new Blueprint()), rec, 4L).build(0, 0, 54, 3, 0, 0, 64);
        Set<Integer> required = new HashSet<>();
        for (CitadelInstance.GatePoint gate : rec.getGates()) {
            required.add(gate.requiredFloor);
        }
        assertTrue(required.contains(-1));
        assertTrue(required.contains(-2));
        assertTrue(!required.contains(-3));
    }
}
