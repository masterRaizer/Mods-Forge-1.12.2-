package com.shatteredcitadels.structure;

import com.shatteredcitadels.palette.CitadelArchetype;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertSame;

/** The boss altar must mark the room that holds the arena, also when rooms share one height. */
public class BossFloorTest {

    private static CitadelInstance citadel() {
        return new CitadelInstance(UUID.randomUUID(), "", StructureType.MANOR, CitadelArchetype.KEEPERS_CITADEL, 0,
                new BlockPos(0, 64, 0), new StructureBoundingBox(-20, 40, -20, 60, 160, 60));
    }

    @Test
    public void roomsOnOneLevelPickTheOneAroundTheArena() {
        CitadelInstance c = citadel();
        c.addFloor(0, 64, 88, 1, 2, false).setArea(0, 0, 10, 10);
        FloorData throne = c.addFloor(1, 64, 88, 5, 1, true).setArea(20, 20, 50, 40);
        c.addFloor(2, 64, 88, 2, 2, false).setArea(0, 20, 10, 40);
        c.setBossArena(new StructureBoundingBox(22, 64, 22, 48, 80, 38), new BlockPos(35, 66, 30));
        assertSame(throne, c.getBossFloor());
    }

    @Test
    public void towersKeepTheFloorAtTheArenaHeight() {
        CitadelInstance c = citadel();
        c.addFloor(0, 64, 71, 1, 2, false);
        FloorData top = c.addFloor(1, 72, 85, 2, 1, true);
        c.setBossArena(new StructureBoundingBox(2, 72, 2, 20, 84, 20), new BlockPos(10, 73, 10));
        assertSame(top, c.getBossFloor());
    }

    @Test
    public void arenaOutsideEveryRoomFallsBackToTheOldRule() {
        CitadelInstance c = citadel();
        FloorData first = c.addFloor(0, 64, 88, 1, 2, false).setArea(0, 0, 10, 10);
        c.addFloor(1, 64, 88, 5, 1, true).setArea(20, 20, 50, 40);
        c.setBossArena(new StructureBoundingBox(52, 64, 52, 58, 80, 58), new BlockPos(55, 66, 55));
        assertSame(first, c.getBossFloor());
    }
}
