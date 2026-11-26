package modernbackports.stages.s14pripyat;

import net.minecraft.world.World;

/** Test stand-in for the entry point of the other mod: the zone is the square 1000..1100. */
public final class PripyatZone {
    private PripyatZone() {
    }

    public static boolean touches(World world, int x1, int z1, int x2, int z2) {
        return x2 >= 1000 && x1 <= 1100 && z2 >= 1000 && z1 <= 1100;
    }
}
