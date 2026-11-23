package com.shatteredcitadels.worldgen.temple;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ground of a jungle temple site. Trees of any mod (Dynamic Trees branches and trunks are wood,
 * their leaves are leaves), plants and vines are not ground; Dynamic Trees' rooty dirt is.
 */
public final class JungleSite {

    /** Water at the top of a column. */
    public static final int WATER = -1;
    public static final int MAX_SLOPE = 12;
    private static final int STEP = 4;

    /** Block access for sampling (a world, or a map in tests). */
    public interface Column {
        IBlockState at(int x, int y, int z);
    }

    private JungleSite() {
    }

    public static Column of(World world) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return (x, y, z) -> world.getBlockState(cursor.setPos(x, y, z));
    }

    /** @return the y of the ground block under {@code top} at x, z, {@link #WATER} for water, or 0. */
    public static int groundY(Column column, int x, int z, int top) {
        for (int y = top; y > 0; y--) {
            Material m = column.at(x, y, z).getMaterial();
            if (m.isLiquid()) {
                return WATER;
            }
            if (isGround(m)) {
                return y;
            }
        }
        return 0;
    }

    private static boolean isGround(Material m) {
        if (!m.isSolid() || !m.blocksMovement()) {
            return false;
        }
        return m != Material.WOOD && m != Material.LEAVES && m != Material.PLANTS && m != Material.VINE
                && m != Material.GOURD && m != Material.CACTUS && m != Material.WEB;
    }

    /**
     * @return the median ground height on a 4-block grid over ±{@code half} of the centre, or null if
     * more than an eighth of the samples are water or the ground spans more than {@link #MAX_SLOPE}.
     */
    public static Integer siteY(Column column, int centreX, int centreZ, int half) {
        List<Integer> heights = new ArrayList<>();
        int water = 0;
        int samples = 0;
        for (int dx = -half; dx <= half; dx += STEP) {
            for (int dz = -half; dz <= half; dz += STEP) {
                samples++;
                int y = groundY(column, centreX + dx, centreZ + dz, 255);
                if (y == WATER) {
                    water++;
                } else {
                    heights.add(y);
                }
            }
        }
        if (water * 8 > samples || heights.isEmpty()) {
            return null;
        }
        Collections.sort(heights);
        if (heights.get(heights.size() - 1) - heights.get(0) > MAX_SLOPE) {
            return null;
        }
        return heights.get(heights.size() / 2);
    }
}
