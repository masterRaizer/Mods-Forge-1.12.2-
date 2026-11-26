package com.shatteredcitadels.worldgen.dims.fortress;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import com.shatteredcitadels.worldgen.dims.DimsConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenNetherBridge;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.Random;

/**
 * Replaces the vanilla Nether fortress generator (also in generators that ask for it through the
 * same event, such as Biomes O' Plenty's). The vanilla sites stay, no vanilla pieces are started,
 * and the mod's fortress is queued when a site chunk is populated. The chunk generator keeps asking
 * this object where fortress mobs spawn, so the answer covers both the mod's fortresses and the
 * vanilla ones of an older world.
 */
public class ShatteredNetherBridgeGen extends MapGenNetherBridge {

    private static final long SALT = 0x6A09E667F3BCC909L;
    /** Regions of 16 chunks searched by {@code /locate Fortress}. */
    private static final int LOCATE_REACH = 12;

    /** No vanilla fortress pieces are ever started. */
    @Override
    public boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        return false;
    }

    /** @return true if a vanilla fortress started before the mod (older world) overlaps the box. */
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
        // Vanilla fortresses already started in an older world keep growing their pieces.
        boolean legacy = super.generateStructure(worldIn, randomIn, chunkCoord);
        if (worldIn.isRemote || worldIn.provider.getDimension() != -1) {
            return legacy;
        }
        this.world = worldIn;
        if (!FortressSpawns.isSite(worldIn.getSeed(), chunkCoord.x, chunkCoord.z)) {
            return legacy;
        }
        long seed = worldIn.getSeed() ^ ChunkPos.asLong(chunkCoord.x, chunkCoord.z) ^ SALT;
        FortressPlan plan = FortressPlan.plan(-1, chunkCoord.x, chunkCoord.z, seed, DimsConfig.fortressBlazeSpawners());
        if (hasLegacyStartIn(plan.footprint())) {
            ShatteredCitadelsMod.LOGGER.info("Fortress site {},{} keeps its vanilla fortress", chunkCoord.x, chunkCoord.z);
        } else if (CitadelWorldGenerator.isAreaFree(worldIn, plan.footprint())) {
            StructureJobs.get(worldIn).submit(plan);
        }
        return legacy;
    }

    @Override
    public boolean isInsideStructure(BlockPos pos) {
        return super.isInsideStructure(pos) || FortressSpawns.inPiece(this.world, pos);
    }

    @Override
    public boolean isPositionInStructure(World worldIn, BlockPos pos) {
        return super.isPositionInStructure(worldIn, pos) || FortressSpawns.inBounds(worldIn, pos);
    }

    @Override
    public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
        int[] site = FortressSpawns.nearestSite(worldIn.getSeed(), pos.getX() >> 4, pos.getZ() >> 4, LOCATE_REACH);
        return site == null ? null : new BlockPos((site[0] << 4) + 8, 64, (site[1] << 4) + 8);
    }
}
