package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.bridge.BridgeBuilder;
import com.shatteredcitadels.worldgen.bridge.BridgeStyle;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeBuilderTest {

    private static final int SEA = 63;
    private static final int[] WIDTHS = {5, 12, 24};

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    private interface Check {
        void run(BridgeStyle style, BlockWriter w, BridgeBuilder b, String what);
    }

    private static void forAll(Check check) {
        for (BridgeStyle style : BridgeStyle.values()) {
            for (int width : WIDTHS) {
                for (boolean alongX : new boolean[]{true, false}) {
                    BlockWriter w = new BlockWriter(new Blueprint());
                    BridgeBuilder b = new BridgeBuilder(w, new Random(11), style, alongX, 100, 100 + width - 1, -40, SEA);
                    b.build();
                    check.run(style, w, b, style + " width " + width + (alongX ? " x" : " z"));
                }
            }
        }
    }

    private static boolean air(BlockWriter w, BlockPos pos) {
        return w.get(pos.getX(), pos.getY(), pos.getZ()).getMaterial() == Material.AIR;
    }

    @Test
    public void deckIsContinuousForEveryStyleExceptRuined() {
        forAll((style, w, b, what) -> {
            if (style == BridgeStyle.RUINED) {
                return;
            }
            for (int a = b.deckFirst(); a <= b.deckLast(); a++) {
                for (int c = -b.half(); c <= b.half(); c++) {
                    assertTrue(what + " hole at " + a + "/" + c,
                            !air(w, b.at(a, b.deckY(), c)) || !air(w, b.at(a, b.deckY() - 1, c)));
                }
            }
        });
    }

    @Test
    public void ruinedHasGap() {
        for (int width : WIDTHS) {
            BlockWriter w = new BlockWriter(new Blueprint());
            BridgeBuilder b = new BridgeBuilder(w, new Random(3), BridgeStyle.RUINED, true, 100, 100 + width - 1, 0, SEA);
            b.build();
            int missing = 0;
            for (int a = 100; a < 100 + width; a++) {
                if (air(w, b.at(a, b.deckY(), 0))) {
                    missing++;
                }
            }
            assertEquals("width " + width, Math.max(3, width / 4), missing);
            assertFalse(air(w, b.at(b.deckFirst(), b.deckY(), 0)));
            assertFalse(air(w, b.at(b.deckLast(), b.deckY(), 0)));
        }
    }

    @Test
    public void headroomIsThree() {
        forAll((style, w, b, what) -> {
            for (int a = b.deckFirst(); a <= b.deckLast(); a++) {
                for (int c = -1; c <= 1; c++) {
                    for (int y = b.deckY() + 1; y <= b.deckY() + 3; y++) {
                        assertTrue(what + " blocked at " + a + "/" + y + "/" + c, air(w, b.at(a, y, c)));
                    }
                }
            }
        });
    }

    @Test
    public void archLeavesBoatClearance() {
        forAll((style, w, b, what) -> {
            if (style.deckRise < 3) {
                return;
            }
            int mid = (b.deckFirst() + b.deckLast()) / 2;
            int open = 0;
            for (int a = mid - 1; a <= mid + 1; a++) {
                boolean free = true;
                for (int c = -b.half() - 1; c <= b.half() + 1; c++) {
                    free &= air(w, b.at(a, SEA, c)) && air(w, b.at(a, SEA + 1, c));
                }
                if (free) {
                    open++;
                }
            }
            assertTrue(what + " no way for a boat", open >= 1);
        });
    }

    @Test
    public void everythingInsideFootprint() {
        forAll((style, w, b, what) -> {
            boolean alongX = what.endsWith("x");
            int end = b.deckLast() - BridgeBuilder.ABUTMENT;
            StructureBoundingBox box = BridgeBuilder.footprint(style, alongX, 100, end, -40, SEA);
            int inside = 0;
            for (int x = box.minX - 10; x <= box.maxX + 10; x++) {
                for (int z = box.minZ - 10; z <= box.maxZ + 10; z++) {
                    for (int y = SEA - 30; y <= SEA + 30; y++) {
                        if (!w.isPlanned(x, y, z)) {
                            continue;
                        }
                        assertTrue(what + " outside at " + x + "/" + y + "/" + z, box.isVecInside(new BlockPos(x, y, z)));
                        inside++;
                    }
                }
            }
            assertEquals(what, w.getWritten(), inside);
        });
    }

    @Test
    public void boardwalkDeckIsOneAboveSea() {
        assertEquals(SEA, BridgeBuilder.deckY(BridgeStyle.BOARDWALK, SEA));
        BlockWriter w = new BlockWriter(new Blueprint());
        BridgeBuilder b = new BridgeBuilder(w, new Random(1), BridgeStyle.BOARDWALK, true, 0, 9, 0, SEA);
        b.build();
        IBlockState deck = w.get(5, SEA, 0);
        assertTrue(deck.getBlock() instanceof BlockSlab);
        assertEquals(BlockSlab.EnumBlockHalf.TOP, deck.getValue(BlockSlab.HALF));
    }

    @Test
    public void buildIsDeterministic() {
        for (BridgeStyle style : BridgeStyle.values()) {
            BlockWriter one = new BlockWriter(new Blueprint());
            BlockWriter two = new BlockWriter(new Blueprint());
            new BridgeBuilder(one, new Random(5), style, false, 10, 25, 7, SEA).build();
            new BridgeBuilder(two, new Random(5), style, false, 10, 25, 7, SEA).build();
            StructureBoundingBox box = BridgeBuilder.footprint(style, false, 10, 25, 7, SEA);
            for (int x = box.minX; x <= box.maxX; x++) {
                for (int z = box.minZ; z <= box.maxZ; z++) {
                    for (int y = box.minY; y <= box.maxY; y++) {
                        assertEquals(style + " differs", one.get(x, y, z), two.get(x, y, z));
                    }
                }
            }
        }
    }
}
