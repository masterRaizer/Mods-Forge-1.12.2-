package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
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
    private static final int SECTION_MARGIN = 2;
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
        CascadeGuard.enter(chunkX, chunkZ);
        try {
            StructureBoundingBox clip = BlockWriter.populationBox(chunkX, chunkZ);
            if (citadelChunk) {
                Random buildRandom = new Random(buildSeed);
                CitadelPlan plan = planCitadel(world, (chunkX << 4) + 1, (chunkZ << 4) + 1, buildRandom, null, null, false);
                if (plan != null && CitadelWorldSavedData.get(world).isFarEnough(chunkX, chunkZ,
                        config.getMinDistanceBetweenCitadelsChunks())) {
                    buildCitadel(world, plan, buildRandom, clip);
                } else if (plan != null) {
                    ShatteredCitadelsMod.LOGGER.debug("Citadel site in chunk {},{} rejected: too close to another structure",
                            chunkX, chunkZ);
                }
            } else {
                CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
                boolean free = !data.isChunkOccupied(chunkX, chunkZ) && !data.isChunkOccupied(chunkX + 1, chunkZ)
                        && !data.isChunkOccupied(chunkX, chunkZ + 1) && !data.isChunkOccupied(chunkX + 1, chunkZ + 1);
                if (free) {
                    Random buildRandom = new Random(buildSeed ^ 0x9E3779B97F4A7C15L);
                    buildStandaloneCrypt(world, (chunkX << 4) + 1, (chunkZ << 4) + 1, buildRandom, clip, false);
                }
            }
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.error("Citadel generation failed in chunk [{}, {}]", chunkX, chunkZ, e);
        } finally {
            CascadeGuard.exit();
            generating = false;
        }
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
        ConfigCache.Snapshot config = ConfigCache.get();
        boolean sky = forceSky != null ? forceSky : random.nextDouble() < config.getSkyCitadelChance();
        int width = sky ? 21 + 2 * random.nextInt(2) : 25 + 2 * random.nextInt(3);
        int floors = config.rollFloorCount(random, sky);
        int arenaHeight = 12 + random.nextInt(3);
        int spireTarget = 210 + random.nextInt(31);
        boolean crypt = random.nextDouble() < config.getUndergroundUnderCastleChance();
        int cryptLevels = crypt ? 2 + random.nextInt(2) : 0;
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
        while (floors > minFloors && baseY + (floors - 1) * CitadelPlan.FLOOR_HEIGHT + arenaHeight + 4 > BlockWriter.MAX_Y) {
            floors--;
        }
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
        return new CitadelPlan(minX, minZ, width, baseY, floors, arenaHeight, sky, archetype, spireTop,
                cryptLevels, random.nextLong());
    }

    /** @return sorted ground heights on a 4-block grid, or null if the site is mostly water. */
    private static int[] sampleHeights(World world, int minX, int minZ, int width) {
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

    private static boolean isUnsuitableBiome(Biome biome) {
        return BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.MUSHROOM);
    }

    private static CitadelArchetype chooseArchetype(Biome biome, double roll) {
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

    // ------------------------------------------------------------------ building

    /** Builds the planned citadel (and its crypt) and registers it in the floor tracker. */
    public CitadelInstance buildCitadel(World world, CitadelPlan plan, Random random, StructureBoundingBox clip) {
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        BlockPalette palette = ModPaletteRegistry.get(plan.archetype);
        BlockWriter writer = new BlockWriter(world, clip);
        Random buildRandom = new Random(plan.buildSeed ^ random.nextLong());
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(buildRandom),
                CitadelNameGenerator.generate(plan.archetype.key(), buildRandom),
                plan.sky ? StructureType.SKY_CITADEL : StructureType.CITADEL, plan.archetype,
                world.provider.getDimension(), new BlockPos(plan.minX, plan.baseY, plan.minZ), plan.totalBounds());
        new CitadelBuilder(writer, plan, palette, buildRandom, record).build();
        if (plan.cryptLevels > 0) {
            int tier = plan.sky ? 3 : 2;
            new DungeonCryptBuilder(writer, palette, buildRandom, record, plan.minX, plan.minZ, plan.width, tier)
                    .build(plan.cryptLevelY(0), plan.cryptLevels, CitadelBuilder.shaftX(plan), CitadelBuilder.shaftZ(plan),
                            plan.floorY(0));
        }
        data.register(record);
        ShatteredCitadelsMod.LOGGER.info("Generated {} #{} ({}, {} floors, crypt levels {}) at {} y={}..{}: {} blocks, {} clipped",
                record.getType(), record.getCitadelId(), plan.archetype.key(), plan.floors, plan.cryptLevels,
                record.getOrigin(), record.getTotalBounds().minY, record.getTotalBounds().maxY,
                writer.getWritten(), writer.getRejected());
        return record;
    }

    /** Standalone crypt with a ruined surface entrance; footprint 21x21 inside the clip box. */
    public CitadelInstance buildStandaloneCrypt(World world, int minX, int minZ, Random random,
                                                  StructureBoundingBox clip, boolean ignoreTerrain) {
        int size = 21;
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
        int topLevelY = surfaceY - 12;
        int levels = 2 + random.nextInt(2);
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        CitadelArchetype archetype = random.nextBoolean() ? CitadelArchetype.KEEPERS_CITADEL : CitadelArchetype.OBSIDIAN_BASTILLE;
        BlockPalette palette = ModPaletteRegistry.get(archetype);
        BlockWriter writer = new BlockWriter(world, clip);
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("crypt", random), StructureType.CRYPT, archetype,
                world.provider.getDimension(), new BlockPos(minX, topLevelY, minZ),
                new StructureBoundingBox(minX, topLevelY, minZ, minX + size - 1, surfaceY + 4, minZ + size - 1));
        DungeonCryptBuilder builder = new DungeonCryptBuilder(writer, palette, random, record, minX, minZ, size, 1);
        builder.buildSurfaceRuin(cx, surfaceY, cz);
        int built = builder.build(topLevelY, levels, cx, cz, surfaceY);
        if (built == 0) {
            return null;
        }
        data.register(record);
        ShatteredCitadelsMod.LOGGER.info("Generated CRYPT #{} ({} levels) at {}: {} blocks, {} clipped",
                record.getCitadelId(), built, record.getOrigin(), writer.getWritten(), writer.getRejected());
        return record;
    }
}
