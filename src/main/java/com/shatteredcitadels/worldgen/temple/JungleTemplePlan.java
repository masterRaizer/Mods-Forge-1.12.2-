package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import com.shatteredcitadels.worldgen.stronghold.StrongholdMobs;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A jungle temple: the stepped surface temple ({@link JungleTemple}), a spiral from its main hall
 * down to an underground ({@link JungleTempleBuilder}); an idol vault below, or a boss shrine on top
 * when {@code boss}.
 */
public final class JungleTemplePlan implements StructurePlan {

    public static final String KIND = "jungle_temple";
    /** The underground start lies this far below the temple floor. */
    public static final int DEPTH = 16;
    private static final int REACH = 36;
    /**
     * Half the sampled square: the site chunk and its +x/+z neighbours (the chunks population
     * guarantees, so sampling never generates a chunk), centred on their shared corner.
     */
    public static final int SITE_HALF = 14;

    public final int centreX;
    public final int surfaceY;
    public final int centreZ;
    public final long seed;
    public final int dimension;
    public final boolean boss;

    public JungleTemplePlan(int centreX, int surfaceY, int centreZ, long seed, int dimension, boolean boss) {
        this.centreX = centreX;
        this.surfaceY = surfaceY;
        this.centreZ = centreZ;
        this.seed = seed;
        this.dimension = dimension;
        this.boss = boss;
    }

    public static int undergroundFloor(int surfaceY) {
        return Math.max(StrongholdLayout.MIN_FLOOR_Y + 12, surfaceY - DEPTH);
    }

    /**
     * Plans the temple of the site chunk (loaded: it is being populated, or a command loaded it).
     * @param boss forced variant, or null for {@code chance}
     * @return null if the ground is unsuitable (water, too steep)
     */
    public static JungleTemplePlan plan(World world, int chunkX, int chunkZ, long seed, Boolean boss, double chance) {
        int x = centreOf(chunkX);
        int z = centreOf(chunkZ);
        Integer ground = siteGround(JungleSite.of(world), chunkX, chunkZ);
        if (ground == null) {
            return null;
        }
        boolean shrine = DesertTemplePlan.variantFor(boss, chance, new Random(seed ^ 0x5DEECE66DL));
        return new JungleTemplePlan(x, Math.max(40, ground), z, seed, world.provider.getDimension(), shrine);
    }

    /** @return the temple's centre along one axis: the corner the site chunk shares with its +1 neighbour. */
    public static int centreOf(int chunk) {
        return (chunk << 4) + 16;
    }

    /** @return the site's ground, sampled within the site chunk and its +x/+z neighbours, or null. */
    public static Integer siteGround(JungleSite.Column column, int chunkX, int chunkZ) {
        return JungleSite.siteY(column, centreOf(chunkX), centreOf(chunkZ), SITE_HALF);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public StructureBoundingBox footprint() {
        return new StructureBoundingBox(centreX - REACH, Math.max(BlockWriter.MIN_Y, undergroundFloor(surfaceY) - 14), centreZ - REACH,
                centreX + REACH, Math.min(BlockWriter.MAX_Y, surfaceY + JungleTemple.TIER_HEIGHT * JungleTemple.TIERS + 16),
                centreZ + REACH);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        int floor = undergroundFloor(surfaceY);
        StrongholdLayout layout = StrongholdLayout.generate(seed, centreX, floor, centreZ, JungleKind.CATALOGUE);
        if (layout == null) {
            return null;
        }
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("jungle", random), StructureType.JUNGLE_TEMPLE, CitadelArchetype.KEEPERS_CITADEL,
                dimension, new BlockPos(centreX, surfaceY, centreZ), footprint());
        ResourceLocation[] mobs = StrongholdMobs.resolve(ConfigCache.get().getJungleTempleSpawnerMobs(), JungleTempleBuilder.DEFAULT_MOBS);
        new JungleTempleBuilder(writer, record, random, boss).withMobs(mobs).build(layout);
        new JungleTemple(writer, random, centreX, surfaceY, centreZ, boss, record).withMobs(mobs).build();
        TempleShaft.build(writer, random, new JunglePalette(), Blocks.STONE_BRICK_STAIRS, centreX, centreZ, surfaceY, floor,
                JungleKind.ENTRY_HALL.height());
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("x", centreX);
        tag.setInteger("y", surfaceY);
        tag.setInteger("z", centreZ);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        tag.setBoolean("boss", boss);
        return tag;
    }

    public static JungleTemplePlan read(NBTTagCompound tag) {
        return new JungleTemplePlan(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), tag.getLong("seed"),
                tag.getInteger("dim"), tag.getBoolean("boss"));
    }

    @Override
    public String describe() {
        return "jungle temple" + (boss ? " (Barako's shrine)" : " (idol vault)") + " at " + centreX + "," + surfaceY + "," + centreZ;
    }
}
