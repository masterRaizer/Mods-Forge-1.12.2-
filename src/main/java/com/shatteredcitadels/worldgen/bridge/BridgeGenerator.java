package com.shatteredcitadels.worldgen.bridge;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;
import java.util.function.BiFunction;

/**
 * Puts bridges over rivers. A chunk whose centre column is a river crossing may get one; everything is
 * decided on the biome map, so no chunk is read or loaded here. The bridge itself is written by
 * {@link StructureJobs} as its chunks appear.
 */
public final class BridgeGenerator implements IWorldGenerator {

    /** Runs after the citadel generator, so a citadel always wins a contested place. */
    public static final int WEIGHT = CitadelWorldGenerator.WEIGHT + 10;
    /** How far outside the river the bank biome is read. */
    private static final int BANK_OFFSET = 6;
    private static final long SALT = 0xB21D6E5L;
    /** The command looks this far ahead for a river. */
    private static final int COMMAND_REACH = 24;
    private static final int COMMAND_SPAN = 12;
    private static final int COMMAND_AHEAD = 10;
    /** No bridge (and so no camp of bandits) this close to the world spawn. */
    static final int SPAWN_CLEARANCE_CHUNKS = 8;
    /** A castle, manor or tower site keeps this many chunks around it free of bridges. */
    static final int SITE_CLEARANCE_CHUNKS = 6;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        if (world.isRemote) {
            return;
        }
        ConfigCache.Snapshot config = ConfigCache.get();
        int dimension = world.provider.getDimension();
        if (!config.isBridgesEnabled() || !config.isBridgeDimension(dimension)) {
            return;
        }
        WorldType type = world.getWorldType();
        if (type == WorldType.FLAT || type == WorldType.DEBUG_ALL_BLOCK_STATES) {
            return;
        }
        try {
            BiomeProvider biomes = world.getBiomeProvider();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            if (!isRiver(biomes.getBiome(pos.setPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8)))) {
                return;
            }
            // The cheap refusals first: finding the crossing reads a few hundred biome columns.
            int distance = config.getBridgeMinDistanceChunks();
            StructureJobs jobs = StructureJobs.get(world);
            BlockPos spawn = world.getSpawnPoint();
            if (isNearSpawn(chunkX, chunkZ, spawn.getX() >> 4, spawn.getZ() >> 4)
                    || !CitadelWorldSavedData.get(world).isFarFromType(StructureType.BRIDGE, chunkX, chunkZ, distance)
                    || jobs.isNearKind(BridgePlan.KIND, chunkX, chunkZ, distance)
                    || CitadelWorldGenerator.isNearSiteCandidate(world.getSeed(), chunkX, chunkZ,
                    config.getCastleSectionSize(), SITE_CLEARANCE_CHUNKS)) {
                return;
            }
            RiverCrossing.BiomeMap map = biomeMap(world);
            BridgePlan plan = planAt(map, (x, z) -> bankOf(biomes.getBiome(new BlockPos(x, 0, z))), chunkX, chunkZ,
                    world.getSeed(), world.getSeaLevel(), dimension, config.getBridgeChance(),
                    config.getBridgeFortifiedChance(), config.getBridgeSpecialChance());
            if (plan == null) {
                return;
            }
            if (!CitadelWorldGenerator.isAreaFree(world, plan.footprint())) {
                return;
            }
            jobs.submit(plan);
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Bridge planning failed in chunk [{}, {}]", chunkX, chunkZ, e);
        }
    }

    /** @return true if the chunk lies within {@link #SPAWN_CLEARANCE_CHUNKS} of the spawn chunk. */
    public static boolean isNearSpawn(int chunkX, int chunkZ, int spawnChunkX, int spawnChunkZ) {
        return Math.abs(chunkX - spawnChunkX) <= SPAWN_CLEARANCE_CHUNKS
                && Math.abs(chunkZ - spawnChunkZ) <= SPAWN_CLEARANCE_CHUNKS;
    }

    /** The biome map of a world, read through the biome provider (never through chunks). */
    public static RiverCrossing.BiomeMap biomeMap(World world) {
        final BiomeProvider biomes = world.getBiomeProvider();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return new RiverCrossing.BiomeMap() {
            @Override
            public boolean isRiver(int x, int z) {
                return BridgeGenerator.isRiver(biomes.getBiome(pos.setPos(x, 0, z)));
            }

            @Override
            public boolean isWater(int x, int z) {
                Biome biome = biomes.getBiome(pos.setPos(x, 0, z));
                return BridgeGenerator.isRiver(biome) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                        || BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH);
            }
        };
    }

    private static boolean isRiver(Biome biome) {
        return biome != null && BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER);
    }

    /** @return what kind of land a bank is, or null where no bridge belongs (mountains, sea, other worlds). */
    public static BridgeStyle.Bank bankOf(Biome biome) {
        if (biome == null || has(biome, BiomeDictionary.Type.MOUNTAIN) || has(biome, BiomeDictionary.Type.OCEAN)
                || has(biome, BiomeDictionary.Type.MUSHROOM) || has(biome, BiomeDictionary.Type.NETHER)
                || has(biome, BiomeDictionary.Type.END)) {
            return null;
        }
        if (has(biome, BiomeDictionary.Type.SNOWY)) {
            return BridgeStyle.Bank.SNOW;
        }
        if (has(biome, BiomeDictionary.Type.JUNGLE)) {
            return BridgeStyle.Bank.JUNGLE;
        }
        if (has(biome, BiomeDictionary.Type.SWAMP)) {
            return BridgeStyle.Bank.SWAMP;
        }
        if (has(biome, BiomeDictionary.Type.SANDY) || has(biome, BiomeDictionary.Type.MESA)
                || has(biome, BiomeDictionary.Type.SAVANNA)) {
            return BridgeStyle.Bank.DESERT;
        }
        if (has(biome, BiomeDictionary.Type.CONIFEROUS)) {
            return BridgeStyle.Bank.TAIGA;
        }
        return BridgeStyle.Bank.FOREST;
    }

    private static boolean has(Biome biome, BiomeDictionary.Type type) {
        return BiomeDictionary.hasType(biome, type);
    }

    /**
     * Decides the bridge of one chunk from the biome map alone.
     *
     * @param bankAt the kind of land at a column, null where no bridge belongs
     * @return the plan, or null: the chunk centre is no crossing, the seeded roll failed or a bank refuses
     */
    public static BridgePlan planAt(RiverCrossing.BiomeMap map, BiFunction<Integer, Integer, BridgeStyle.Bank> bankAt,
                                    int chunkX, int chunkZ, long worldSeed, int seaLevel, int dimension, double chance,
                                    double fortified, double special) {
        Random rand = new Random(worldSeed ^ ChunkPos.asLong(chunkX, chunkZ) ^ SALT);
        // The first values of neighbouring seeds are close; one spare draw separates them.
        rand.nextInt();
        if (rand.nextDouble() >= chance) {
            return null;
        }
        RiverCrossing crossing = RiverCrossing.find(map, (chunkX << 4) + 8, (chunkZ << 4) + 8);
        if (crossing == null) {
            return null;
        }
        BridgeStyle.Bank near = bank(bankAt, crossing, crossing.start - BANK_OFFSET);
        BridgeStyle.Bank far = bank(bankAt, crossing, crossing.end + BANK_OFFSET);
        if (near == null || far == null) {
            return null;
        }
        return plan(crossing, null, null, near, rand, seaLevel, dimension, fortified, special);
    }

    private static BridgeStyle.Bank bank(BiFunction<Integer, Integer, BridgeStyle.Bank> bankAt, RiverCrossing crossing,
                                         int along) {
        return crossing.alongX ? bankAt.apply(along, crossing.cross) : bankAt.apply(crossing.cross, along);
    }

    private static BridgePlan plan(RiverCrossing crossing, BridgeStyle style, Boolean fortified, BridgeStyle.Bank bank,
                                   Random rand, int seaLevel, int dimension, double fortifiedChance, double specialChance) {
        int width = crossing.width();
        BridgeStyle chosen = style != null ? style : BridgeStyle.choose(bank, width, rand, specialChance);
        boolean camp = fortified != null ? fortified : BridgeStyle.fortified(chosen, width, rand, fortifiedChance);
        return new BridgePlan(chosen, camp, crossing.alongX, crossing.start, crossing.end, crossing.cross, seaLevel,
                rand.nextLong(), dimension);
    }

    /**
     * The bridge of {@code /citadel spawn bridge}: over the river within {@value #COMMAND_REACH} blocks ahead,
     * or, with no river there, a span of {@value #COMMAND_SPAN} centred {@value #COMMAND_AHEAD} blocks ahead.
     *
     * @param alongX         true if the player looks along X
     * @param step           +1 or -1: which way along that axis
     * @param styleOrNull    null picks the style of the bank
     * @param fortifiedOrNull null rolls the camp as in the world
     */
    public static BridgePlan planForCommand(RiverCrossing.BiomeMap map, int x, int z, boolean alongX, int step,
                                            BridgeStyle styleOrNull, Boolean fortifiedOrNull, BridgeStyle.Bank bank,
                                            long seed, int seaLevel, int dimension) {
        Random rand = new Random(seed);
        ConfigCache.Snapshot config = ConfigCache.get();
        RiverCrossing crossing = null;
        for (int ahead = 0; ahead <= COMMAND_REACH && crossing == null; ahead++) {
            int cx = alongX ? x + step * ahead : x;
            int cz = alongX ? z : z + step * ahead;
            if (map.isRiver(cx, cz)) {
                crossing = RiverCrossing.find(map, cx, cz);
                if (crossing == null) {
                    // A river, but no clean crossing: bridge what lies ahead anyway.
                    break;
                }
            }
        }
        if (crossing == null) {
            int centre = (alongX ? x : z) + step * COMMAND_AHEAD;
            int start = centre - COMMAND_SPAN / 2 + 1;
            crossing = new RiverCrossing(alongX, start, start + COMMAND_SPAN - 1, alongX ? z : x);
        }
        return plan(crossing, styleOrNull, fortifiedOrNull, bank == null ? BridgeStyle.Bank.FOREST : bank, rand, seaLevel,
                dimension, config.getBridgeFortifiedChance(), config.getBridgeSpecialChance());
    }
}
