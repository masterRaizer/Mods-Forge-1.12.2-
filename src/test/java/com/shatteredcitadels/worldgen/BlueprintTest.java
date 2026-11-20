package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BlueprintTest {

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
        TestBlocks.install();
    }

    @Test
    public void packMatchesBlockPos() {
        int[][] samples = {{0, 0, 0}, {-1, 5, -1}, {1234567, 250, -7654321}, {-30000000, 64, 29999999}};
        for (int[] p : samples) {
            assertEquals(new BlockPos(p[0], p[1], p[2]).toLong(), Blueprint.pack(p[0], p[1], p[2]));
        }
    }

    @Test
    public void writerKeepsLastStateAndSplitsByChunk() {
        Blueprint blueprint = new Blueprint();
        BlockWriter writer = new BlockWriter(blueprint);
        IBlockState stone = Blocks.STONE.getDefaultState();
        writer.set(3, 70, 3, Blocks.DIRT.getDefaultState());
        writer.set(3, 70, 3, stone);
        writer.set(-1, 70, 20, stone);
        assertFalse(writer.set(0, 300, 0, stone));
        writer.fillDown(17, 1, 60, stone, 10);
        writer.clearColumn(17, 1, 71, 90);
        writer.afterPlace(new BlockPos(3, 70, 3), (world, at) -> { });
        assertSame(stone, writer.get(3, 70, 3));
        assertTrue(writer.isPlanned(3, 70, 3));
        assertSame(Blocks.AIR, writer.get(5, 70, 5).getBlock());
        assertEquals(1, writer.getRejected());

        blueprint.seal();
        assertEquals(3, blueprint.parts().size());
        Blueprint.ChunkPart origin = blueprint.parts().get(ChunkPos.asLong(0, 0));
        assertNotNull(origin);
        assertEquals(1, origin.positions.size());
        assertEquals(new BlockPos(3, 70, 3), origin.unpack(origin.positions.getInt(0)));
        assertEquals(1, origin.actions.size());
        Blueprint.ChunkPart negative = blueprint.parts().get(ChunkPos.asLong(-1, 1));
        assertEquals(new BlockPos(-1, 70, 20), negative.unpack(negative.positions.getInt(0)));
        Blueprint.ChunkPart ops = blueprint.parts().get(ChunkPos.asLong(1, 0));
        assertEquals(1, ops.clears.size());
        assertEquals(1, ops.fills.size());
    }

    @Test
    public void plansSurviveSaving() {
        CitadelPlan keep = new CitadelPlan(100, -50, 27, 70, 6, 13, false, CitadelArchetype.TITAN_FORGE, 120, 3,
                42L, 0, true, 1);
        CastlePlan castle = new CastlePlan(keep, 48, 52, 12, 7L);
        NBTTagCompound tag = castle.writeToNBT();
        StructurePlan read = StructurePlans.read(tag);
        assertTrue(read instanceof CastlePlan);
        CastlePlan copy = (CastlePlan) read;
        assertEquals(48, copy.halfX);
        assertEquals(52, copy.halfZ);
        assertEquals(12, copy.wallHeight);
        assertEquals(7L, copy.seed);
        assertEquals(keep.writeToNBT(), copy.keep.writeToNBT());
        assertEquals(castle.footprint().toString(), copy.footprint().toString());

        CryptPlan crypt = new CryptPlan(5, 6, 60, 4, CitadelArchetype.OBSIDIAN_BASTILLE, 9L, -1);
        assertEquals(crypt.writeToNBT(), StructurePlans.read(crypt.writeToNBT()).writeToNBT());

        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString(StructurePlan.KIND_KEY, "manor_from_the_future");
        assertNull(StructurePlans.read(unknown));
    }

    @Test
    public void keepFloorWinsOverCourtyardAtTheSameHeight() {
        CitadelInstance castle = new CitadelInstance(UUID.randomUUID(), "", StructureType.WALLED_CASTLE,
                CitadelArchetype.KEEPERS_CITADEL, 0, new BlockPos(-50, 64, -50), new StructureBoundingBox(-50, 40, -50, 50, 200, 50));
        castle.addFloor(0, 64, 85, 1, 3, false).setArea(-50, -50, 50, 50).setCourtyard(true);
        castle.addFloor(1, 64, 71, 1, 3, false).setArea(-13, -13, 13, 13);
        castle.addFloor(2, 72, 79, 2, 3, false).setArea(-13, -13, 13, 13);
        assertTrue(castle.hasCourtyard());
        FloorData inKeep = castle.floorAt(new BlockPos(0, 65, 0));
        assertEquals(1, inKeep.getIndex());
        FloorData inYard = castle.floorAt(new BlockPos(30, 65, 30));
        assertEquals(0, inYard.getIndex());
        assertEquals(2, castle.floorAt(new BlockPos(0, 75, 0)).getIndex());
        assertEquals(0, castle.floorAt(new BlockPos(30, 75, 30)).getIndex());
        assertNull(castle.floorAt(new BlockPos(0, 120, 0)));
    }

    @Test
    public void protectionCoversPartsOnly() {
        CitadelInstance citadel = new CitadelInstance(UUID.randomUUID(), "", StructureType.CITADEL,
                CitadelArchetype.KEEPERS_CITADEL, 0, BlockPos.ORIGIN, new StructureBoundingBox(0, 60, 0, 10, 100, 10));
        assertTrue(citadel.isInPart(new BlockPos(5, 70, 5)));
        citadel.addPart(new StructureBoundingBox(0, 60, 0, 10, 100, 10));
        citadel.addPart(new StructureBoundingBox(20, 60, 0, 30, 80, 10));
        assertTrue(citadel.isInPart(new BlockPos(25, 70, 5)));
        assertFalse(citadel.isInPart(new BlockPos(15, 70, 5)));
        assertTrue(citadel.contains(new BlockPos(15, 70, 5)));
    }

    @Test
    public void manorZonesUnlockInRouteOrder() {
        ManorPlan plan = new ManorPlan(-39, -43, 70, 5L, 0);
        assertEquals(plan.writeToNBT(), StructurePlans.read(plan.writeToNBT()).writeToNBT());

        CitadelInstance manor = new CitadelInstance(UUID.randomUUID(), "", StructureType.MANOR,
                CitadelArchetype.GOTHIC_MANOR, 0, BlockPos.ORIGIN, new StructureBoundingBox(0, 60, 0, 100, 120, 100));
        FloorData yard = manor.addFloor(0, 70, 82, 1, 2, false).setArea(0, 0, 50, 50).setZoneKey("forecourt");
        FloorData hall = manor.addFloor(1, 70, 90, 2, 2, false).setArea(51, 0, 70, 50).setZoneKey("great_hall")
                .setRequiresFloor(0);
        assertTrue(manor.isUnlocked(yard));
        assertFalse(manor.isUnlocked(hall));
        yard.setCleared(true);
        assertTrue(manor.isUnlocked(hall));
        assertEquals("great_hall", manor.floorAt(new BlockPos(60, 75, 10)).getZoneKey());

        FloorData copy = FloorData.readFromNBT(hall.writeToNBT());
        assertEquals("great_hall", copy.getZoneKey());
        assertEquals(0, copy.getRequiresFloor());
        assertEquals(Integer.MIN_VALUE, FloorData.readFromNBT(yard.writeToNBT()).getRequiresFloor());
    }

    @Test
    public void manorWithCatacombsRegistersLevels() {
        ManorPlan plan = new ManorPlan(-39, -43, 70, 5L, 0, 2);
        CitadelInstance manor = plan.build(new BlockWriter(new Blueprint()));
        for (int zone = 0; zone <= 7; zone++) {
            assertNotNull("zone " + zone, manor.getFloor(zone));
        }
        assertNotNull(manor.getFloor(-1));
        assertNotNull(manor.getFloor(-2));
        assertNotNull(manor.getFloor(-10));
        StructureBoundingBox footprint = plan.footprint();
        FloorData deepest = manor.getFloor(-10);
        assertTrue(footprint.minY <= deepest.getMinY());
        assertTrue(footprint.isVecInside(new BlockPos(-39 + 54, 40, -43 + 70)));
    }

    @Test
    public void plansRoundTripCatacombLevels() {
        ManorPlan manor = new ManorPlan(-39, -43, 70, 5L, 0, 3);
        assertEquals(3, ((ManorPlan) StructurePlans.read(manor.writeToNBT())).catacombLevels);
        NBTTagCompound legacy = manor.writeToNBT();
        legacy.removeTag("catacombs");
        assertEquals(0, ManorPlan.read(legacy).catacombLevels);
        CitadelPlan tower = new CitadelPlan(0, 0, 21, 70, 6, 12, false, CitadelArchetype.KEEPERS_CITADEL, 120, 3, 9L, 0,
                true, 0);
        assertEquals(tower.writeToNBT(), StructurePlans.read(tower.writeToNBT()).writeToNBT());
        assertEquals(3, ((CitadelPlan) StructurePlans.read(tower.writeToNBT())).cryptLevels);
    }
}
