package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.stronghold.PieceKind;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StrongholdLayoutTest {

    private static int count(StrongholdLayout layout, PieceKind kind) {
        int n = 0;
        for (StrongholdLayout.Piece piece : layout.pieces()) {
            if (piece.kind == kind) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void exactlyOnePortalRoomForEverySeed() {
        for (long seed = 1; seed <= 200; seed++) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, 0, 30, 0);
            assertEquals("seed " + seed, 1, count(layout, PieceKind.PORTAL_ROOM));
            assertTrue("seed " + seed + " portal depth " + layout.portalRoom().depth, layout.portalRoom().depth >= 4);
        }
    }

    @Test
    public void piecesDoNotOverlapAndStayInRadius() {
        for (long seed = 1; seed <= 40; seed++) {
            List<StrongholdLayout.Piece> pieces = StrongholdLayout.generate(seed, 100, 30, -50).pieces();
            for (int i = 0; i < pieces.size(); i++) {
                StrongholdLayout.Piece a = pieces.get(i);
                assertTrue(a.box.minX >= 100 - 70 && a.box.maxX <= 100 + 70 && a.box.minZ >= -50 - 70 && a.box.maxZ <= -50 + 70);
                assertTrue("seed " + seed + " floor too low", a.box.minY >= StrongholdLayout.MIN_FLOOR_Y);
                for (int j = i + 1; j < pieces.size(); j++) {
                    assertTrue("seed " + seed + ": " + a.kind + " overlaps " + pieces.get(j).kind,
                            !a.box.intersectsWith(pieces.get(j).box));
                }
            }
        }
    }

    @Test
    public void everyPieceReachableFromStart() {
        for (long seed = 1; seed <= 40; seed++) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, 0, 30, 0);
            Set<StrongholdLayout.Piece> seen = new HashSet<>();
            Deque<StrongholdLayout.Piece> queue = new ArrayDeque<>();
            queue.add(layout.start());
            seen.add(layout.start());
            while (!queue.isEmpty()) {
                StrongholdLayout.Piece piece = queue.poll();
                for (StrongholdLayout.Doorway door : piece.doors) {
                    if (!door.used) {
                        continue;
                    }
                    BlockPos beyond = door.pos.offset(door.out);
                    for (StrongholdLayout.Piece other : layout.pieces()) {
                        for (StrongholdLayout.Doorway back : other.doors) {
                            if (back.used && back.pos.equals(beyond) && back.out == door.out.getOpposite() && seen.add(other)) {
                                queue.add(other);
                            }
                        }
                    }
                }
            }
            assertEquals("seed " + seed, layout.pieces().size(), seen.size());
        }
    }

    @Test
    public void sizeAndLimits() {
        int inRange = 0;
        for (long seed = 1; seed <= 100; seed++) {
            StrongholdLayout layout = StrongholdLayout.generate(seed, 0, 30, 0);
            int size = layout.pieces().size();
            if (size >= 60 && size <= 90) {
                inRange++;
            }
            assertTrue(count(layout, PieceKind.GRAND_LIBRARY) <= 1);
            assertTrue(count(layout, PieceKind.SMALL_LIBRARY) <= 3);
            assertTrue(count(layout, PieceKind.TREASURE) <= 1);
        }
        assertTrue("seeds with 60..90 pieces: " + inRange, inRange >= 90);
    }

    @Test
    public void deterministic() {
        StrongholdLayout a = StrongholdLayout.generate(77L, 0, 30, 0);
        StrongholdLayout b = StrongholdLayout.generate(77L, 0, 30, 0);
        assertEquals(a.pieces().size(), b.pieces().size());
        for (int i = 0; i < a.pieces().size(); i++) {
            assertEquals(a.pieces().get(i).kind, b.pieces().get(i).kind);
            assertEquals(a.pieces().get(i).box.toString(), b.pieces().get(i).box.toString());
        }
    }
}
