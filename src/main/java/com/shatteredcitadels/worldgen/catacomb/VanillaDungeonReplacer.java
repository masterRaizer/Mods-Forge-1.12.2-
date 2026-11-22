package com.shatteredcitadels.worldgen.catacomb;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/**
 * Replaces the vanilla cobblestone spawner dungeons of the Overworld with buried catacomb
 * fragments: the vanilla ones are denied on the terrain-generation bus, and one fragment per
 * region (when its roll succeeds) is queued after a chunk has been populated.
 *
 * <p>Registered on {@code MinecraftForge.TERRAIN_GEN_BUS} by the common proxy for
 * {@link #onPopulate}; {@link #onPostPopulate} runs on the main bus.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class VanillaDungeonReplacer {

    private static final long SALT = 0x6A09E667F3BCC909L;
    /** Chunks kept free around each section's structure site (a castle with its moat and catacombs). */
    private static final int SITE_CLEARANCE_CHUNKS = 12;

    private VanillaDungeonReplacer() {
    }

    @SubscribeEvent
    public static void onPopulate(PopulateChunkEvent.Populate event) {
        if (event.getType() == PopulateChunkEvent.Populate.EventType.DUNGEON && applies(event.getWorld())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onPostPopulate(PopulateChunkEvent.Post event) {
        World world = event.getWorld();
        if (!applies(world)) {
            return;
        }
        ConfigCache.Snapshot config = ConfigCache.get();
        FragmentPlan plan = fragmentAt(world.getSeed(), event.getChunkX(), event.getChunkZ(),
                config.getFragmentRegionChunks(), config.getFragmentChance(), world.provider.getDimension());
        if (plan == null || CitadelWorldGenerator.isNearSiteCandidate(world.getSeed(), event.getChunkX(), event.getChunkZ(),
                config.getCastleSectionSize(), SITE_CLEARANCE_CHUNKS)) {
            return;
        }
        StructureBoundingBox f = plan.footprint();
        StructureBoundingBox margin = new StructureBoundingBox(f.minX - 32, f.minY, f.minZ - 32, f.maxX + 32, f.maxY, f.maxZ + 32);
        if (CitadelWorldGenerator.isAreaFree(world, margin, true)) {
            StructureJobs.get(world).submit(plan);
        }
    }

    private static boolean applies(World world) {
        return !world.isRemote && world.provider.getDimension() == 0 && ConfigCache.get().isReplaceVanillaDungeons();
    }

    /**
     * @return the fragment whose region picked this chunk (and whose roll succeeded), or null.
     * Deterministic in the world seed: each region picks one chunk.
     */
    public static FragmentPlan fragmentAt(long worldSeed, int chunkX, int chunkZ, int region, double chance, int dimension) {
        int rx = Math.floorDiv(chunkX, region);
        int rz = Math.floorDiv(chunkZ, region);
        Random random = new Random(worldSeed ^ rx * 341873128712L ^ rz * 132897987541L ^ SALT);
        int pickX = rx * region + random.nextInt(region);
        int pickZ = rz * region + random.nextInt(region);
        boolean roll = random.nextDouble() < chance;
        if (!roll || pickX != chunkX || pickZ != chunkZ) {
            return null;
        }
        int y = 12 + random.nextInt(29);
        return new FragmentPlan((chunkX << 4) + 8, (chunkZ << 4) + 8, y, random.nextLong(), dimension);
    }
}
