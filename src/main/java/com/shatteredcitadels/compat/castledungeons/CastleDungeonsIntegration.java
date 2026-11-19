package com.shatteredcitadels.compat.castledungeons;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.spawner.SpawnerContext;
import com.shatteredcitadels.spawner.SpawnerSource;
import com.shatteredcitadels.spawner.WaveSpawnerHooks;
import com.shatteredcitadels.structure.CitadelDataRecord;
import com.shatteredcitadels.structure.CitadelSavedData;
import com.shatteredcitadels.structure.RoomTag;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Hooks called from the Castle Dungeons mixins. This class never references a Castle Dungeons
 * type, so it loads fine without the mod; the mixins translate CD objects into plain values.
 * Every hook is fail-safe: on any error it logs once and lets Castle Dungeons proceed unchanged.
 */
public final class CastleDungeonsIntegration {

    public static final String MODID = "castle_dungeons";

    /** Castle Dungeons grid: cell width and layer height in blocks. */
    public static final int CELL_SIZE = 6;
    public static final int LAYER_HEIGHT = 5;
    private static final int FOUNDATION_DEPTH = 16;
    private static final int ROOF_HEIGHT = 8;

    /**
     * Stack, not a single value: Castle Dungeons reads neighbouring chunks before building, which
     * can populate those chunks and re-enter its generator (nested generation).
     */
    private static final ThreadLocal<Deque<Context>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile boolean errorLogged;
    private static volatile int adoptedCastles;
    private static volatile int replacedSpawners;
    private static volatile int rejectedByDistance;

    private CastleDungeonsIntegration() {
    }

    public static boolean isLoaded() {
        return Loader.isModLoaded(MODID);
    }

    // ------------------------------------------------------------------ config

    /** @return the section size Castle Dungeons should use. */
    public static int sectionSize(int original) {
        try {
            ConfigCache.Snapshot config = ConfigCache.get();
            return config.isCastleDungeonsSectionOverride() ? config.getCastleSectionSize() : original;
        } catch (RuntimeException e) {
            logOnce(e);
            return original;
        }
    }

    // ------------------------------------------------------------------ generation context

    public static void beginGenerate(World world, int chunkX, int chunkZ) {
        CONTEXT.get().push(new Context(world, chunkX, chunkZ));
    }

    public static void endGenerate() {
        Deque<Context> stack = CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    private static Context current() {
        return CONTEXT.get().peek();
    }

    /**
     * Second filter after Castle Dungeons picked a chunk: enforces the minimum distance to
     * every tracked citadel and castle.
     */
    public static boolean allowCastle(int chunkX, int chunkZ) {
        Context context = current();
        if (context == null || context.world == null || context.world.isRemote) {
            return true;
        }
        try {
            ConfigCache.Snapshot config = ConfigCache.get();
            if (!config.isCastleDungeonsSectionOverride()) {
                return true;
            }
            boolean allowed = CitadelSavedData.get(context.world)
                    .isFarEnough(chunkX, chunkZ, config.getMinDistanceBetweenCitadelsChunks());
            if (!allowed) {
                rejectedByDistance++;
            }
            return allowed;
        } catch (RuntimeException e) {
            logOnce(e);
            return true;
        }
    }

    /**
     * A Castle Dungeons castle starts building at (x, y, z) = its minimum corner. Registers it
     * in the floor tracker so floors, distance checks and spawners know about it.
     */
    public static void onCastleStart(int x, int y, int z, int gridWidth, int gridDepth, int layers) {
        Context context = current();
        if (context == null || context.world == null || context.world.isRemote) {
            return;
        }
        try {
            int width = gridWidth * CELL_SIZE + 1;
            int depth = gridDepth * CELL_SIZE + 1;
            int layerCount = Math.max(1, layers);
            int top = y + layerCount * LAYER_HEIGHT + ROOF_HEIGHT;
            CitadelSavedData data = CitadelSavedData.get(context.world);
            CitadelDataRecord record = new CitadelDataRecord(data.allocateId(), StructureType.CASTLE_DUNGEONS, MODID,
                    context.world.provider.getDimension(), new BlockPos(x, y, z),
                    new StructureBoundingBox(x, Math.max(0, y - FOUNDATION_DEPTH), z, x + width - 1, Math.min(255, top),
                            z + depth - 1));
            for (int i = 0; i < layerCount; i++) {
                record.addFloor(i, y + i * LAYER_HEIGHT, y + i * LAYER_HEIGHT + LAYER_HEIGHT - 1);
            }
            int topY = y + (layerCount - 1) * LAYER_HEIGHT;
            StructureBoundingBox arena = new StructureBoundingBox(x, topY, z, x + width - 1, topY + LAYER_HEIGHT - 1, z + depth - 1);
            record.setBossArena(arena, new BlockPos(x + width / 2, topY + 1, z + depth / 2));
            record.addRoom(arena, RoomTag.BOSS_ARENA, layerCount - 1);
            data.add(record);
            context.record = record;
            adoptedCastles++;
        } catch (RuntimeException e) {
            logOnce(e);
        }
    }

    /**
     * Replaces a Castle Dungeons spawner.
     *
     * @param difficulty 0 = easy, 1 = medium, 2 = hard (CD MonsterType ordinal)
     * @return true if our spawner was placed and the original must be skipped
     */
    public static boolean replaceSpawner(World world, BlockPos pos, int difficulty, Random random) {
        if (world == null || world.isRemote || pos == null) {
            return false;
        }
        try {
            if (!ConfigCache.get().isCastleDungeonsSpawnerReplacement()) {
                return false;
            }
            Context context = current();
            CitadelDataRecord record = context != null ? context.record : null;
            if (record == null) {
                record = CitadelSavedData.get(world).findContaining(pos);
            }
            int floor = 0;
            if (record != null) {
                CitadelDataRecord.FloorSpan span = record.floorAt(pos.getY());
                floor = span != null ? span.index : Math.max(0, (pos.getY() - record.getOrigin().getY()) / LAYER_HEIGHT);
            }
            int tier = 1 + Math.max(0, Math.min(2, difficulty)) + (floor >= 3 ? 1 : 0);
            SpawnerContext spawnerContext = new SpawnerContext(SpawnerSource.CASTLE_DUNGEONS,
                    record != null ? record.getId() : 0L, floor, tier, RoomTag.GUARD_ROOM);
            if (!WaveSpawnerHooks.place(world, pos, spawnerContext)) {
                return false;
            }
            if (record != null) {
                record.addSpawnerPoint(pos, floor, spawnerContext.getTier());
                CitadelSavedData.get(world).markDirty();
            }
            replacedSpawners++;
            return true;
        } catch (RuntimeException e) {
            logOnce(e);
            return false;
        }
    }

    public static String describeStatus() {
        return "mixins: " + CastleDungeonsMixinState.describe() + "; castles adopted=" + adoptedCastles
                + ", spawners replaced=" + replacedSpawners + ", rejected by distance=" + rejectedByDistance;
    }

    private static void logOnce(Throwable t) {
        if (!errorLogged) {
            errorLogged = true;
            ShatteredCitadelsMod.LOGGER.error("Castle Dungeons integration hook failed; Castle Dungeons continues unmodified", t);
        }
    }

    private static final class Context {
        final World world;
        final int chunkX;
        final int chunkZ;
        CitadelDataRecord record;

        Context(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
