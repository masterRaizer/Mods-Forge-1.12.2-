package com.shatteredcitadels.worldgen.temple;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.ComponentScatteredFeaturePieces;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.Random;

/**
 * Replaces the scattered-feature generator (vanilla's or Biomes O' Plenty's, described by a
 * {@link TempleSites}): on its desert and jungle sites it builds our temples instead; the sites we do not
 * handle (witch huts, igloos, disabled kinds, other dimensions) keep that generator's own start, and
 * swamp huts keep their witch spawning.
 */
public class ScatteredFeatureGen extends MapGenScatteredFeature {

    private static final long SALT = 0x2545F4914F6CDD1DL;
    private static final int SEPARATION = 8;
    private static final int GRID_SALT = 14357617;

    private final TempleSites sites;
    private final int spacing;

    public ScatteredFeatureGen(TempleSites sites, int spacing) {
        this.sites = sites;
        this.spacing = Math.max(SEPARATION + 1, spacing);
        // Vanilla's nearest-site search reads its private spacing: keep it in step with ours.
        setVanillaSpacing(this.spacing);
    }

    private void setVanillaSpacing(int value) {
        for (String name : new String[]{"field_82669_g", "maxDistanceBetweenScatteredFeatures"}) {
            try {
                java.lang.reflect.Field field = MapGenScatteredFeature.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(this, value);
                return;
            } catch (ReflectiveOperationException ignored) {
                // the other name (obfuscated or development)
            }
        }
        ShatteredCitadelsMod.LOGGER.warn("Cannot set the temple spacing; /locate Temple may miss sites");
    }

    /** @return true for the biomes where vanilla places the desert pyramid. */
    public static boolean isOurs(Biome biome) {
        return VanillaTempleSites.INSTANCE.kindOf(biome) == TempleSites.Kind.DESERT;
    }

    /** @return true if this mod builds the temples of {@code kind} (by config). */
    public static boolean handles(TempleSites.Kind kind) {
        return kind == TempleSites.Kind.DESERT && ConfigCache.get().isReplaceDesertTemples()
                || kind == TempleSites.Kind.JUNGLE && ConfigCache.get().isReplaceJungleTemples();
    }

    private TempleSites.Kind siteKind(int chunkX, int chunkZ) {
        return sites.kindOf(world.getBiomeProvider().getBiome(new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8)));
    }

    /** The site grid of vanilla and BOP: one site per {@code spacing}² chunk region. */
    private boolean isGridSite(int chunkX, int chunkZ) {
        int x = chunkX < 0 ? chunkX - spacing + 1 : chunkX;
        int z = chunkZ < 0 ? chunkZ - spacing + 1 : chunkZ;
        int regionX = x / spacing;
        int regionZ = z / spacing;
        Random random = world.setRandomSeed(regionX, regionZ, GRID_SALT);
        return chunkX == regionX * spacing + random.nextInt(spacing - SEPARATION)
                && chunkZ == regionZ * spacing + random.nextInt(spacing - SEPARATION);
    }

    private boolean isOurs(TempleSites.Kind kind) {
        return world.provider.getDimension() == 0 && handles(kind);
    }

    /** The generator's own sites, except the ones that get our temple. */
    @Override
    public boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        if (!isGridSite(chunkX, chunkZ)) {
            return false;
        }
        TempleSites.Kind kind = siteKind(chunkX, chunkZ);
        return kind != TempleSites.Kind.NONE && !isOurs(kind);
    }

    @Override
    protected StructureStart getStructureStart(int chunkX, int chunkZ) {
        return sites.otherStart(world, rand, chunkX, chunkZ);
    }

    /** Any swamp hut, whichever generator's start holds it (BOP's start is not vanilla's). */
    @Override
    public boolean isSwampHut(BlockPos pos) {
        StructureStart start = getStructureAt(pos);
        return start != null && !start.getComponents().isEmpty()
                && start.getComponents().get(0) instanceof ComponentScatteredFeaturePieces.SwampHut;
    }

    /** @return true if a temple started before the mod (older world) overlaps the box. */
    public boolean hasLegacyStartIn(StructureBoundingBox box) {
        for (StructureStart start : structureMap.values()) {
            if (start != null && start.getBoundingBox() != null && start.getBoundingBox().intersectsWith(box)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean generateStructure(World worldIn, Random randomIn, ChunkPos chunkCoord) {
        boolean generated = super.generateStructure(worldIn, randomIn, chunkCoord);
        if (worldIn.isRemote || worldIn.provider.getDimension() != 0) {
            return generated;
        }
        this.world = worldIn;
        if (!isGridSite(chunkCoord.x, chunkCoord.z)) {
            return generated;
        }
        TempleSites.Kind kind = siteKind(chunkCoord.x, chunkCoord.z);
        if (!isOurs(kind)) {
            return generated;
        }
        long seed = worldIn.getSeed() ^ ChunkPos.asLong(chunkCoord.x, chunkCoord.z) ^ SALT;
        StructurePlan plan = kind == TempleSites.Kind.DESERT ? DesertTemplePlan.plan(worldIn, chunkCoord.x, chunkCoord.z, seed)
                : JungleTemplePlan.plan(worldIn, chunkCoord.x, chunkCoord.z, seed, null, ConfigCache.get().getJungleTempleBossChance());
        if (plan == null) {
            ShatteredCitadelsMod.LOGGER.info("Jungle site {},{} is too wet or steep for a temple", chunkCoord.x, chunkCoord.z);
        } else if (hasLegacyStartIn(plan.footprint())) {
            ShatteredCitadelsMod.LOGGER.info("Temple site {},{} keeps the temple started before", chunkCoord.x, chunkCoord.z);
        } else if (CitadelWorldGenerator.isAreaFree(worldIn, plan.footprint())) {
            StructureJobs.get(worldIn).submit(plan);
        }
        return generated;
    }
}
