package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Arrays;
import java.util.Random;

/**
 * Places citadels, the Citadel Above the Clouds and standalone crypts.
 *
 * <p>The world is split into square sections of {@code castleSectionSize} chunks. Each
 * section deterministically picks one candidate chunk for a citadel and one for a standalone
 * crypt. A structure is built only while its candidate chunk is being populated, entirely
 * inside the populated 2x2-chunk area (footprint &lt;= 30 blocks), so no other chunk is ever
 * loaded or generated: no cascading worldgen lag.
 */
public final class CitadelWorldGenerator implements IWorldGenerator {

    /** Registration weight: after vanilla-style decorators so citadels sit on final terrain. */
    public static final int WEIGHT = 100;

    private static final long SECTION_SALT = 0x5C17ADE1C17ADE1L;
    /** Chunks kept free at section edges: the largest footprint (walled castle) reaches this far. */
    private static final int SECTION_MARGIN = 6;
    private static final int MIN_BASE_Y = 12;

    private boolean generating;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        if (world.isRemote || generating) {
            return;
        }
        ConfigCache.Snapshot config = ConfigCache.get();
        if (!config.isCitadelGenerationEnabled() || !config.isDimensionAllowed(world.provider.getDimension())
                || world.getWorldType() == WorldType.FLAT || world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            return;
        }
        int section = config.getCastleSectionSize();
        int sx = Math.floorDiv(chunkX, section);
        int sz = Math.floorDiv(chunkZ, section);
        Random sectionRandom = sectionRandom(world.getSeed(), sx, sz);
        // Fixed draw order: every chunk of the section derives identical candidates.
        int span = Math.max(1, section - 2 * SECTION_MARGIN - 1);
        int citadelChunkX = sx * section + SECTION_MARGIN + sectionRandom.nextInt(span);
        int citadelChunkZ = sz * section + SECTION_MARGIN + sectionRandom.nextInt(span);
        int cryptChunkX = sx * section + SECTION_MARGIN + sectionRandom.nextInt(span);
        int cryptChunkZ = sz * section + SECTION_MARGIN + sectionRandom.nextInt(span);
        double cryptRoll = sectionRandom.nextDouble();
        long buildSeed = sectionRandom.nextLong();

        boolean citadelChunk = chunkX == citadelChunkX && chunkZ == citadelChunkZ;
        boolean cryptChunk = chunkX == cryptChunkX && chunkZ == cryptChunkZ && !citadelChunk
                && cryptRoll < config.getStandaloneUndergroundDungeonChance();
        if (!citadelChunk && !cryptChunk) {
            return;
        }
        generating = true;
        try {
            StructureJobs jobs = StructureJobs.get(world);
            CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
            StructurePlan plan;
            if (citadelChunk) {
                Random buildRandom = new Random(buildSeed);
                int minDistance = config.getMinDistanceBetweenCitadelsChunks();
                if (!data.isFarEnough(chunkX, chunkZ, minDistance) || jobs.isNear(chunkX, chunkZ, minDistance)) {
                    ShatteredCitadelsMod.LOGGER.debug("Citadel site in chunk {},{} rejected: too close to another structure",
                            chunkX, chunkZ);
                    return;
                }
                plan = buildRandom.nextDouble() < config.getWalledCastleChance()
                        ? CastlePlanner.plan(world, chunkX, chunkZ, buildRandom, null, false) : null;
                if (plan == null) {
                    // No castle (not rolled, or too much water or mountain around): a citadel tower still fits here.
                    plan = planCitadel(world, (chunkX << 4) + 1, (chunkZ << 4) + 1, new Random(buildSeed ^ 0x51ED270B27L),
                            null, null, null, false);
                }
            } else {
                plan = planCrypt(world, (chunkX << 4) + 1, (chunkZ << 4) + 1, new Random(buildSeed ^ 0x9E3779B97F4A7C15L), false);
            }
            if (plan != null && isAreaFree(world, plan.footprint())) {
                jobs.submit(plan);
            }
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Citadel planning failed in chunk [{}, {}]", chunkX, chunkZ, e);
        } finally {
            generating = false;
        }
    }

    /** @return true if no registered or queued structure overlaps the footprint. */
    public static boolean isAreaFree(World world, StructureBoundingBox footprint) {
        if (StructureJobs.get(world).isReserved(footprint)) {
            return false;
        }
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        for (int cx = footprint.minX >> 4; cx <= footprint.maxX >> 4; cx++) {
            for (int cz = footprint.minZ >> 4; cz <= footprint.maxZ >> 4; cz++) {
                if (data.isChunkOccupied(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    static Random sectionRandom(long worldSeed, int sx, int sz) {
        return new Random(worldSeed + sx * 341873128712L + sz * 132897987541L + SECTION_SALT);
    }

    // ------------------------------------------------------------------ planning

    /**
     * Evaluates the site and fixes every layout decision.
     *
     * @param forceSky       null = roll with skyCitadelChance
     * @param forceArchetype null = choose from the biome
     * @param ignoreTerrain  true for admin commands: skip slope/water/biome rejection
     * @return the plan, or null if the site is unsuitable
     */
    public CitadelPlan planCitadel(World world, int minX, int minZ, Random random, Boolean forceSky,
                                   CitadelArchetype forceArchetype, boolean ignoreTerrain) {
        return planCitadel(world, minX, minZ, random, forceSky, null, forceArchetype, ignoreTerrain);
    }

    public CitadelPlan planCitadel(World world, int minX, int minZ, Random random, Boolean forceSky, Boolean forceTower,
                                   CitadelArchetype forceArchetype, boolean ignoreTerrain) {
        return planCitadel(world, minX, minZ, random, forceSky, forceTower, forceArchetype, ignoreTerrain, 0);
    }

    /**
     * @param forceTower  null = roll with towerCitadelChance (regular citadels only)
     * @param floorOffset 1 for the keep of a walled castle (its courtyard is floor 0)
     */
    public CitadelPlan planCitadel(World world, int minX, int minZ, Random random, Boolean forceSky, Boolean forceTower,
                                   CitadelArchetype forceArchetype, boolean ignoreTerrain, int floorOffset) {
        ConfigCache.Snapshot config = ConfigCache.get();
        boolean sky = forceSky != null ? forceSky : random.nextDouble() < config.getSkyCitadelChance();
        int width = sky ? 21 + 2 * random.nextInt(2) : 25 + 2 * random.nextInt(3);
        int floors = config.rollFloorCount(random, sky);
        double towerRoll = random.nextDouble();
        int towerFloors = config.rollTowerFloorCount(random);
        boolean tower = !sky && (forceTower != null ? forceTower : towerRoll < config.getTowerCitadelChance());
        if (tower) {
            floors = Math.max(floors, towerFloors);
        }
        int arenaHeight = 12 + random.nextInt(3);
        int spireTarget = 210 + random.nextInt(31);
        boolean crypt = random.nextDouble() < config.getUndergroundUnderCastleChance();
        int cryptLevels = crypt ? config.rollCryptLevels(random) : 0;
        double archetypeRoll = random.nextDouble();

        int centerX = minX + width / 2;
        int centerZ = minZ + width / 2;
        Biome biome = world.getBiome(new BlockPos(centerX, 64, centerZ));
        if (!ignoreTerrain && isUnsuitableBiome(biome)) {
            ShatteredCitadelsMod.LOGGER.debug("Citadel site {},{} rejected: biome {}", minX, minZ, biome.getRegistryName());
            return null;
        }
        int[] heights = sampleHeights(world, minX, minZ, width);
        if (heights == null) {
            if (!ignoreTerrain) {
                ShatteredCitadelsMod.LOGGER.debug("Citadel site {},{} rejected: water", minX, minZ);
                return null;
            }
            heights = new int[]{world.getSeaLevel() + 1};
        }
        int min = heights[0];
        int max = heights[heights.length - 1];
        if (!ignoreTerrain && max - min > config.getMaxFoundationDrop()) {
            ShatteredCitadelsMod.LOGGER.debug("Citadel site {},{} rejected: slope {}", minX, minZ, max - min);
            return null;
        }
        int baseY = Math.max(MIN_BASE_Y, heights[heights.length / 2]);

        // Shrink until the roof, battlements and corner lights fit below Y 250.
        int minFloors = sky ? config.getSkyCitadelMinFloors() : config.getMinFloors();
        floors = fitFloors(baseY, floors, minFloors, arenaHeight);
        int roof = baseY + (floors - 1) * CitadelPlan.FLOOR_HEIGHT + arenaHeight;
        if (roof + 4 > BlockWriter.MAX_Y) {
            return null;
        }
        int spireTop = sky ? Math.min(BlockWriter.MAX_Y, Math.max(spireTarget, roof + 6)) : roof + 4;

        // Crypt levels must stay above the world floor.
        while (cryptLevels > 0 && baseY - 10 - (cryptLevels - 1) * CitadelPlan.CRYPT_LEVEL_HEIGHT < BlockWriter.MIN_Y + 1) {
            cryptLevels--;
        }
        CitadelArchetype archetype = forceArchetype != null ? forceArchetype
                : sky ? CitadelArchetype.SKY_CITADEL : chooseArchetype(biome, archetypeRoll);
        boolean wings = config.getTowerWingChance() > 0.0D;
        return new CitadelPlan(minX, minZ, width, baseY, floors, arenaHeight, sky, archetype, spireTop,
                cryptLevels, random.nextLong(), world.provider.getDimension(), wings, floorOffset);
    }

    /**
     * A floor without any wave spawner (none fit, or the placer failed) has nothing to clear: it is
     * marked cleared right away and its sealed gates are removed again, so no way stays locked.
     */
    static void openUnguardedFloors(BlockWriter writer, CitadelInstance record) {
        java.util.Set<Integer> guarded = new java.util.HashSet<>();
        for (CitadelInstance.SpawnerPoint point : record.getSpawnerPoints()) {
            guarded.add(point.floorIndex);
        }
        for (FloorData floor : record.getFloors().values()) {
            if (floor.isBossFloor() || guarded.contains(floor.getIndex())) {
                continue;
            }
            floor.setCleared(true);
            for (CitadelInstance.GatePoint gate : new java.util.ArrayList<>(record.getGates())) {
                if (gate.requiredFloor == floor.getIndex()) {
                    writer.set(gate.pos, net.minecraft.block.Block.getStateById(gate.restoreStateId));
                    record.removeGate(gate);
                }
            }
        }
    }

    /**
     * @return the floor count shortened (never below {@code minFloors}) so the roof, battlements and
     * corner lights stay below {@link BlockWriter#MAX_Y}.
     */
    static int fitFloors(int baseY, int floors, int minFloors, int arenaHeight) {
        int fitted = floors;
        while (fitted > minFloors && baseY + (fitted - 1) * CitadelPlan.FLOOR_HEIGHT + arenaHeight + 4 > BlockWriter.MAX_Y) {
            fitted--;
        }
        return fitted;
    }

    /** @return sorted ground heights on a 4-block grid, or null if the site is mostly water. */
    static int[] sampleHeights(World world, int minX, int minZ, int width) {
        int steps = width / 4 + 1;
        int[] heights = new int[steps * steps];
        int water = 0;
        int n = 0;
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < steps; j++) {
                int x = minX + Math.min(width - 1, i * 4);
                int z = minZ + Math.min(width - 1, j * 4);
                BlockPos top = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
                Material below = world.getBlockState(top.down()).getMaterial();
                if (below.isLiquid() || below == Material.ICE) {
                    water++;
                }
                heights[n++] = top.getY();
            }
        }
        if (water > heights.length / 8) {
            return null;
        }
        Arrays.sort(heights);
        return heights;
    }

    static boolean isUnsuitableBiome(Biome biome) {
        return BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.MUSHROOM);
    }

    static CitadelArchetype chooseArchetype(Biome biome, double roll) {
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.NETHER)
                || (BiomeDictionary.hasType(biome, BiomeDictionary.Type.HOT) && BiomeDictionary.hasType(biome, BiomeDictionary.Type.DRY))) {
            return roll < 0.75D ? CitadelArchetype.OBSIDIAN_BASTILLE : CitadelArchetype.TITAN_FORGE;
        }
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.MOUNTAIN) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.HILLS)) {
            return roll < 0.6D ? CitadelArchetype.TITAN_FORGE : CitadelArchetype.KEEPERS_CITADEL;
        }
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.MAGICAL) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.SPOOKY)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)) {
            return roll < 0.6D ? CitadelArchetype.TECHNO_ARCANE_SPIRE : CitadelArchetype.KEEPERS_CITADEL;
        }
        if (roll < 0.55D) {
            return CitadelArchetype.KEEPERS_CITADEL;
        }
        if (roll < 0.70D) {
            return CitadelArchetype.TECHNO_ARCANE_SPIRE;
        }
        if (roll < 0.85D) {
            return CitadelArchetype.TITAN_FORGE;
        }
        return CitadelArchetype.OBSIDIAN_BASTILLE;
    }

    // ------------------------------------------------------------------ crypts

    /** Standalone crypt with a ruined surface entrance; footprint 21x21. */
    public CryptPlan planCrypt(World world, int minX, int minZ, Random random, boolean ignoreTerrain) {
        int cx = minX + 11;
        int cz = minZ + 11;
        Biome biome = world.getBiome(new BlockPos(cx, 64, cz));
        if (!ignoreTerrain && isUnsuitableBiome(biome)) {
            return null;
        }
        BlockPos top = world.getTopSolidOrLiquidBlock(new BlockPos(cx, 0, cz));
        if (!ignoreTerrain && world.getBlockState(top.down()).getMaterial().isLiquid()) {
            return null;
        }
        int surfaceY = Math.max(MIN_BASE_Y + 12, top.getY() - 1);
        int levels = ConfigCache.get().rollCryptLevels(random);
        CitadelArchetype archetype = random.nextBoolean() ? CitadelArchetype.KEEPERS_CITADEL : CitadelArchetype.OBSIDIAN_BASTILLE;
        return new CryptPlan(minX, minZ, surfaceY, levels, archetype, random.nextLong(), world.provider.getDimension());
    }
}
