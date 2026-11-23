package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.temple.JungleSite;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JungleSiteTest {

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    /** Ground (dirt) at {@code ground(x, z)}; {@code tree} adds a trunk and canopy at x == 0. */
    static JungleSite.Column terrain(java.util.function.IntBinaryOperator ground, boolean tree, boolean water) {
        return (x, y, z) -> {
            int g = ground.applyAsInt(x, z);
            if (y <= g) {
                return Blocks.DIRT.getDefaultState();
            }
            if (water && y <= g + 2) {
                return Blocks.WATER.getDefaultState();
            }
            if (tree && x == 0 && z == 0 && y <= g + 20) {
                return y > g + 15 ? Blocks.LEAVES.getDefaultState() : Blocks.LOG.getDefaultState();
            }
            if (tree && Math.abs(x) <= 3 && Math.abs(z) <= 3 && y > g + 15 && y <= g + 20) {
                return Blocks.LEAVES.getDefaultState();
            }
            if (y == g + 1 && (x + z) % 3 == 0) {
                return Blocks.TALLGRASS.getDefaultState().withProperty(net.minecraft.block.BlockTallGrass.TYPE,
                        net.minecraft.block.BlockTallGrass.EnumType.GRASS);
            }
            return Blocks.AIR.getDefaultState();
        };
    }

    @Test
    public void skipsTrunksBranchesAndLeaves() {
        JungleSite.Column c = terrain((x, z) -> 70, true, false);
        assertEquals(70, JungleSite.groundY(c, 0, 0, 120));
        assertEquals(70, JungleSite.groundY(c, 2, 1, 120));
        assertEquals(70, JungleSite.groundY(c, 3, 0, 120));
    }

    @Test
    public void rootyDirtIsGround() {
        IBlockState rooty = Blocks.STONE.getDefaultState();
        JungleSite.Column c = (x, y, z) -> y == 64 ? rooty : y < 64 ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState();
        assertEquals(64, JungleSite.groundY(c, 5, 5, 120));
    }

    @Test
    public void waterSiteRejected() {
        assertNull(JungleSite.siteY(terrain((x, z) -> 60, false, true), 0, 0, 20));
    }

    @Test
    public void steepSiteRejected() {
        assertNull(JungleSite.siteY(terrain((x, z) -> x >= 0 ? 95 : 70, false, false), 0, 0, 20));
    }

    /** Jungle hills: a spread the levelling can take (hills cut, dips filled) is accepted. */
    @Test
    public void hillySiteAccepted() {
        assertEquals(Integer.valueOf(83), JungleSite.siteY(terrain((x, z) -> 70 + (x + 20) * 26 / 40, false, false), 0, 0, 20));
    }

    /** A deep dip on one side would leave the foundation hanging: rejected. */
    @Test
    public void deepDipRejected() {
        assertNull(JungleSite.siteY(terrain((x, z) -> x <= -8 ? 60 : 80, false, false), 0, 0, 20));
    }

    @Test
    public void medianOfGentleSlope() {
        Integer y = JungleSite.siteY(terrain((x, z) -> 70 + (x + 20) / 8, true, false), 0, 0, 20);
        assertEquals(Integer.valueOf(72), y);
    }

    /** Population guarantees only the site chunk and its +x/+z neighbours: sampling stays in them. */
    @Test
    public void planSamplesOnlyTheLoadedChunks() {
        int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        JungleSite.Column c = (x, y, z) -> {
            box[0] = Math.min(box[0], x);
            box[1] = Math.max(box[1], x);
            box[2] = Math.min(box[2], z);
            box[3] = Math.max(box[3], z);
            return y <= 70 ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState();
        };
        assertEquals(Integer.valueOf(70), com.shatteredcitadels.worldgen.temple.JungleTemplePlan.siteGround(c, 3, -2));
        assertTrue(box[0] >= 48 && box[1] <= 79 && box[2] >= -32 && box[3] <= -1);
        assertEquals(64, com.shatteredcitadels.worldgen.temple.JungleTemplePlan.centreOf(3));
    }
}
