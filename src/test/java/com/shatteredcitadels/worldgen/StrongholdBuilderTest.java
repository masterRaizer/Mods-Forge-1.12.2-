package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.stronghold.StrongholdBuilder;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StrongholdBuilderTest {

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    static BlockWriter built(StrongholdLayout layout, long seed) {
        BlockWriter writer = new BlockWriter(new Blueprint());
        CitadelInstance rec = new CitadelInstance(UUID.randomUUID(), "", StructureType.CRYPT, CitadelArchetype.KEEPERS_CITADEL, 0,
                BlockPos.ORIGIN, new StructureBoundingBox(-80, 5, -80, 80, 80, 80));
        new StrongholdBuilder(writer, rec, new Random(seed)).build(layout);
        return writer;
    }

    @Test
    public void portalFrameHas12InwardFrames() {
        for (long seed = 1; seed <= 5; seed++) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, 0, 30, 0);
            BlockWriter writer = built(layout, seed);
            StructureBoundingBox box = layout.portalRoom().box;
            List<BlockPos> frames = new ArrayList<>();
            for (BlockPos p : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
                if (writer.get(p.getX(), p.getY(), p.getZ()).getBlock() == Blocks.END_PORTAL_FRAME) {
                    frames.add(p.toImmutable());
                }
            }
            assertEquals("seed " + seed, 12, frames.size());
            double cx = 0;
            double cz = 0;
            for (BlockPos p : frames) {
                cx += p.getX() / 12.0;
                cz += p.getZ() / 12.0;
            }
            for (BlockPos p : frames) {
                IBlockState frame = writer.get(p.getX(), p.getY(), p.getZ());
                EnumFacing facing = frame.getValue(BlockEndPortalFrame.FACING);
                BlockPos ahead = p.offset(facing);
                double before = Math.hypot(p.getX() - cx, p.getZ() - cz);
                double after = Math.hypot(ahead.getX() - cx, ahead.getZ() - cz);
                assertTrue("frame at " + p + " faces " + facing, after < before);
            }
        }
    }

    @Test
    public void unusedDoorsAreClosedAndUsedDoorsWalkable() {
        for (long seed = 1; seed <= 5; seed++) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, 0, 30, 0);
            BlockWriter writer = built(layout, seed);
            for (StrongholdLayout.Piece piece : layout.pieces()) {
                for (StrongholdLayout.Doorway door : piece.doors) {
                    EnumFacing side = door.out.rotateY();
                    for (int t = -1; t <= 1; t++) {
                        BlockPos p = door.pos.offset(side, t);
                        if (door.used) {
                            assertTrue("seed " + seed + " blocked doorway at " + p, !writer.isSolid(p.getX(), p.getY(), p.getZ())
                                    && !writer.isSolid(p.getX(), p.getY() + 1, p.getZ()));
                        } else {
                            for (int dy = 0; dy <= 2; dy++) {
                                assertTrue("seed " + seed + " open unused doorway at " + p.up(dy),
                                        writer.isSolid(p.getX(), p.getY() + dy, p.getZ()));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Every open block is enclosed by planned blocks: liquids and caves around are replaced, never reach inside. */
    @Test
    public void shellIsClosed() {
        for (long seed = 1; seed <= 3; seed++) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, 0, 30, 0);
            BlockWriter writer = built(layout, seed);
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
}
