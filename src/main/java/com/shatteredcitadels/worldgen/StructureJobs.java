package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.worldgen.catacomb.FragmentPlan;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Structures waiting to be written into one world. A planned structure is built into a
 * {@link Blueprint} on a background thread; the server thread then copies it into chunks that
 * are loaded, populated and surrounded by loaded chunks, nearest to a player first, spending at
 * most {@code generationBudgetMs} per tick. Nothing here ever loads or generates a chunk, so
 * structures of any size cause neither cascading worldgen nor freezes. Jobs are saved: an
 * unfinished structure is rebuilt from its plan and completed after a restart.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class StructureJobs extends WorldSavedData {

    public static final String DATA_NAME = Reference.MODID + "_jobs";
    /** Drop the in-memory blueprint of a job none of whose chunks has been ready for this long. */
    private static final int EVICT_AFTER_TICKS = 1200;
    private static final int TIME_CHECK_INTERVAL = 32;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ShatteredCitadels-Blueprint");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        return thread;
    });

    private final List<Job> jobs = new ArrayList<>();
    private Job current;
    private Blueprint.ChunkPart currentPart;
    private int stage;
    private int index;
    private long ticks;

    public StructureJobs(String name) {
        super(name);
    }

    public static StructureJobs get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        StructureJobs data = (StructureJobs) storage.getOrLoadData(StructureJobs.class, DATA_NAME);
        if (data == null) {
            data = new StructureJobs(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    // ------------------------------------------------------------------ API

    /** Queues a planned structure; it is built in the background and written over the next ticks. */
    public void submit(StructurePlan plan) {
        if (!(plan instanceof FragmentPlan)) {
            dropFragmentsUnder(plan.footprint());
        }
        Job job = new Job(plan, null, new LongOpenHashSet());
        jobs.add(job);
        startBuild(job);
        markDirty();
        ShatteredCitadelsMod.LOGGER.info("Planned {}", plan.describe());
    }

    /**
     * A structure takes precedence over buried fragments: queued fragments under it are dropped
     * (whatever they already wrote is overwritten by the structure).
     */
    private void dropFragmentsUnder(StructureBoundingBox box) {
        for (Iterator<Job> it = jobs.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (job.plan instanceof FragmentPlan
                    && job.plan.footprint().intersectsWith(box.minX, box.minZ, box.maxX, box.maxZ)) {
                it.remove();
                dropCurrent(job);
                ShatteredCitadelsMod.LOGGER.info("Dropped {}: a structure takes its place", job.plan.describe());
            }
        }
    }

    /** @return true if a queued structure other than a buried fragment reserves this area. */
    public boolean isReservedForStructures(StructureBoundingBox box) {
        for (Job job : jobs) {
            if (!(job.plan instanceof FragmentPlan)
                    && job.plan.footprint().intersectsWith(box.minX, box.minZ, box.maxX, box.maxZ)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if a queued (not yet registered or unfinished) structure reserves this area. */
    public boolean isReserved(StructureBoundingBox box) {
        for (Job job : jobs) {
            if (job.plan.footprint().intersectsWith(box.minX, box.minZ, box.maxX, box.maxZ)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if a queued structure's centre lies within {@code minDistanceChunks}. */
    public boolean isNear(int chunkX, int chunkZ, int minDistanceChunks) {
        long limit = (long) minDistanceChunks * minDistanceChunks;
        for (Job job : jobs) {
            // Underground pieces are not structure sites; overlap is still caught by isReserved.
            if (job.plan instanceof CryptPlan || job.plan instanceof FragmentPlan
                    || job.plan instanceof com.shatteredcitadels.worldgen.stronghold.StrongholdPlan) {
                continue;
            }
            StructureBoundingBox box = job.plan.footprint();
            long dx = ((box.minX + box.maxX) >> 5) - chunkX;
            long dz = ((box.minZ + box.maxZ) >> 5) - chunkZ;
            if (dx * dx + dz * dz < limit) {
                return true;
            }
        }
        return false;
    }

    public int pendingJobs() {
        return jobs.size();
    }

    /** @return one line per queued structure: chunks written / total, loaded, ready to write. */
    public List<String> describe(WorldServer world) {
        List<String> lines = new ArrayList<>();
        ChunkProviderServer provider = world.getChunkProvider();
        for (Job job : jobs) {
            if (job.blueprint == null) {
                StructureBoundingBox box = job.plan.footprint();
                int loaded = 0;
                int populated = 0;
                int count = 0;
                for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
                    for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                        count++;
                        Chunk chunk = provider.getLoadedChunk(cx, cz);
                        loaded += chunk != null ? 1 : 0;
                        populated += chunk != null && chunk.isTerrainPopulated() ? 1 : 0;
                    }
                }
                lines.add(job.plan.describe() + ": " + (job.future != null ? "building blueprint" : "waiting for its chunks")
                        + ", " + job.done.size() + " chunks written; footprint " + count + " chunks, " + loaded
                        + " loaded, " + populated + " populated");
                continue;
            }
            int total = job.blueprint.parts().size();
            int loaded = 0;
            int populated = 0;
            int ready = 0;
            for (Blueprint.ChunkPart part : job.blueprint.parts().values()) {
                Chunk chunk = provider.getLoadedChunk(part.chunkX, part.chunkZ);
                if (chunk != null) {
                    loaded++;
                    if (chunk.isTerrainPopulated()) {
                        populated++;
                    }
                }
                if (!job.done.contains(ChunkPos.asLong(part.chunkX, part.chunkZ)) && isReady(world, part.chunkX, part.chunkZ)) {
                    ready++;
                }
            }
            lines.add(job.plan.describe() + ": " + job.done.size() + "/" + total + " chunks written, " + loaded
                    + " loaded, " + populated + " populated, " + ready + " ready now");
        }
        return lines;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || !(event.world instanceof WorldServer)) {
            return;
        }
        StructureJobs data = get(event.world);
        if (!data.jobs.isEmpty()) {
            data.tick((WorldServer) event.world);
        }
    }

    private void tick(WorldServer world) {
        ticks++;
        long deadline = System.nanoTime() + ConfigCache.get().getGenerationBudgetMs() * 1_000_000L;
        for (Iterator<Job> it = jobs.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (job.future != null && job.future.isDone()) {
                if (!collect(world, job)) {
                    it.remove();
                    dropCurrent(job);
                    markDirty();
                    continue;
                }
            }
            if (job.blueprint == null && job.future == null && (job.citadelId == null || anyFootprintChunkReady(world, job))) {
                startBuild(job);
            }
            if (job.blueprint != null && ticks - job.lastReadyTick > EVICT_AFTER_TICKS && job != current) {
                job.blueprint = null;
            }
        }
        while (System.nanoTime() < deadline) {
            if (currentPart == null && !pickNext(world)) {
                return;
            }
            if (writeStep(world, deadline)) {
                current.done.add(ChunkPos.asLong(currentPart.chunkX, currentPart.chunkZ));
                markDirty();
                if (current.done.size() >= current.blueprint.parts().size()) {
                    ShatteredCitadelsMod.LOGGER.info("Finished writing {} ({} chunks, {} blocks repaired)",
                            current.plan.describe(), current.done.size(), current.repaired);
                    jobs.remove(current);
                }
                current = null;
                currentPart = null;
            }
        }
    }

    private void startBuild(Job job) {
        StructurePlan plan = job.plan;
        job.future = WORKER.submit(() -> {
            long started = System.nanoTime();
            Blueprint blueprint = new Blueprint();
            BlockWriter writer = new BlockWriter(blueprint);
            CitadelInstance record = plan.build(writer);
            if (record != null) {
                CitadelWorldGenerator.openUnguardedFloors(writer, record);
            }
            int blocks = blueprint.blockCount();
            blueprint.seal();
            return new BuildResult(record, blueprint, blocks, (System.nanoTime() - started) / 1_000_000L);
        });
    }

    /** @return false if the job failed and must be dropped. */
    private boolean collect(WorldServer world, Job job) {
        BuildResult result;
        try {
            result = job.future.get();
        } catch (InterruptedException | ExecutionException e) {
            ShatteredCitadelsMod.LOGGER.error("Building {} failed", job.plan.describe(), e);
            job.future = null;
            return false;
        }
        job.future = null;
        if (result.record == null) {
            ShatteredCitadelsMod.LOGGER.info("Nothing to build for {}", job.plan.describe());
            return false;
        }
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        if (job.citadelId == null || data.getById(job.citadelId) == null) {
            data.register(result.record);
            job.citadelId = result.record.getCitadelId();
            markDirty();
            ShatteredCitadelsMod.LOGGER.info("Generated {} #{} ({}): {} blocks in {} chunks, built in {} ms, bounds {}",
                    result.record.getType(), job.citadelId, job.plan.describe(), result.blocks,
                    result.blueprint.parts().size(), result.millis, result.record.getTotalBounds());
        }
        job.blueprint = result.blueprint;
        job.lastReadyTick = ticks;
        if (job.done.size() >= job.blueprint.parts().size()) {
            return false;
        }
        return true;
    }

    private void dropCurrent(Job job) {
        if (current == job) {
            current = null;
            currentPart = null;
        }
    }

    /** Ordering key for ready chunks: a structure's priority area first, then the nearest to a player. */
    public static double rank(double distanceToPlayers, boolean priority) {
        return priority ? distanceToPlayers - 1.0E12D : distanceToPlayers;
    }

    /** Chooses the ready, unwritten chunk closest to any player (a priority area first). */
    private boolean pickNext(WorldServer world) {
        Job bestJob = null;
        Blueprint.ChunkPart best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Job job : jobs) {
            if (job.blueprint == null) {
                continue;
            }
            for (Blueprint.ChunkPart part : job.blueprint.parts().values()) {
                if (job.done.contains(ChunkPos.asLong(part.chunkX, part.chunkZ)) || !isReady(world, part.chunkX, part.chunkZ)) {
                    continue;
                }
                job.lastReadyTick = ticks;
                StructureBoundingBox priority = job.plan.priorityArea();
                boolean first = priority != null && priority.intersectsWith(part.chunkX << 4, part.chunkZ << 4,
                        (part.chunkX << 4) + 15, (part.chunkZ << 4) + 15);
                double distance = rank(distanceToPlayers(world, part.chunkX, part.chunkZ), first);
                if (best == null || distance < bestDistance) {
                    bestDistance = distance;
                    best = part;
                    bestJob = job;
                }
            }
        }
        if (best == null) {
            return false;
        }
        current = bestJob;
        currentPart = best;
        stage = 0;
        index = 0;
        lightChanges.clear();
        return true;
    }

    /**
     * Writes the current chunk until done (true) or the deadline passes (false). Plain blocks go
     * straight into the chunk sections (no per-block lighting or packets); blocks that carry or
     * replace a tile entity go through {@code World#setBlockState}. When the chunk is complete its
     * sky light is regenerated once, light sources are re-lit and the chunk is re-sent to players.
     */
    @SuppressWarnings("deprecation")
    private boolean writeStep(WorldServer world, long deadline) {
        Blueprint.ChunkPart part = currentPart;
        if (!isReady(world, part.chunkX, part.chunkZ)) {
            // Unloaded meanwhile: start this chunk over when it is back.
            current = null;
            currentPart = null;
            return false;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(part.chunkX, part.chunkZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int counter = 0;
        while (true) {
            if (++counter % TIME_CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) {
                return false;
            }
            switch (stage) {
                case 0:
                    if (index >= part.clears.size()) {
                        stage = 1;
                        index = 0;
                        continue;
                    }
                    Blueprint.ColumnOp clear = part.clears.get(index++);
                    for (int y = clear.y; y <= clear.stateIdOrToY; y++) {
                        put(world, chunk, cursor.setPos(clear.x, y, clear.z), Blocks.AIR.getDefaultState(), true);
                    }
                    break;
                case 1:
                    if (index >= part.positions.size()) {
                        stage = 2;
                        index = 0;
                        continue;
                    }
                    int packed = part.positions.getInt(index);
                    IBlockState state = Block.getStateById(part.states.getInt(index));
                    index++;
                    cursor.setPos((part.chunkX << 4) | (packed & 15), packed >>> 8, (part.chunkZ << 4) | ((packed >>> 4) & 15));
                    put(world, chunk, cursor, state, false);
                    break;
                case 2:
                    // Read every plain block back through the chunk itself. Some performance mods
                    // change how chunks store their sections; whatever did not stick is rewritten
                    // through Chunk#setBlockState, which they all support.
                    if (index >= part.positions.size()) {
                        stage = 3;
                        index = 0;
                        continue;
                    }
                    int check = part.positions.getInt(index);
                    IBlockState expected = Block.getStateById(part.states.getInt(index));
                    index++;
                    if (expected.getBlock().hasTileEntity(expected)) {
                        break;
                    }
                    cursor.setPos((part.chunkX << 4) | (check & 15), check >>> 8, (part.chunkZ << 4) | ((check >>> 4) & 15));
                    if (chunk.getBlockState(cursor) != expected) {
                        chunk.setBlockState(cursor.toImmutable(), expected);
                        if (current.repaired++ == 0) {
                            ShatteredCitadelsMod.LOGGER.warn("Direct chunk writes did not stick at {} ({}); rewriting such blocks "
                                    + "through the chunk", cursor.toImmutable(), current.plan.describe());
                        }
                    }
                    break;
                case 3:
                    if (index >= part.fills.size()) {
                        stage = 4;
                        index = 0;
                        continue;
                    }
                    fill(world, chunk, part.fills.get(index++), cursor);
                    break;
                case 4:
                    if (index >= part.actions.size()) {
                        stage = 5;
                        index = 0;
                        continue;
                    }
                    BlockPos at = part.actionPositions.get(index);
                    Blueprint.TileAction action = part.actions.get(index);
                    index++;
                    try {
                        action.apply(world, at);
                    } catch (RuntimeException e) {
                        ShatteredCitadelsMod.LOGGER.warn("Structure tile set-up failed at {}: {}", at, e.toString());
                    }
                    break;
                default:
                    finishChunk(world, chunk);
                    return true;
            }
        }
    }

    /** Light sources placed or removed in the chunk being written; re-lit when it is finished. */
    private final List<BlockPos> lightChanges = new ArrayList<>();

    /**
     * Sets one block. Direct section write for plain blocks; the world path for anything with a
     * tile entity so it is created and removed properly.
     *
     * @param onlyIfNotAir clearing: leave air alone
     */
    @SuppressWarnings("deprecation")
    private void put(WorldServer world, Chunk chunk, BlockPos.MutableBlockPos pos, IBlockState state, boolean onlyIfNotAir) {
        int y = pos.getY();
        if (y < 0 || y > 255) {
            return;
        }
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        ExtendedBlockStorage section = sections[y >> 4];
        IBlockState old = section == Chunk.NULL_BLOCK_STORAGE ? Blocks.AIR.getDefaultState()
                : section.get(pos.getX() & 15, y & 15, pos.getZ() & 15);
        if (old == state || (onlyIfNotAir && old.getMaterial() == Material.AIR)) {
            return;
        }
        if (old.getBlock().hasTileEntity(old) || state.getBlock().hasTileEntity(state)) {
            world.setBlockState(pos.toImmutable(), state, BlockWriter.FLAGS);
            return;
        }
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            if (state.getMaterial() == Material.AIR) {
                return;
            }
            // Let the chunk create the section itself: some mods hand out a copy of the section
            // array, so a section stored into it by hand would be lost.
            chunk.setBlockState(pos.toImmutable(), state);
        } else {
            section.set(pos.getX() & 15, y & 15, pos.getZ() & 15, state);
        }
        if (state.getLightValue() > 0 || old.getLightValue() > 0) {
            lightChanges.add(pos.toImmutable());
        }
    }

    /** Sky light once for the whole chunk, block light around light sources, re-send to players. */
    private void finishChunk(WorldServer world, Chunk chunk) {
        chunk.generateSkylightMap();
        for (BlockPos pos : lightChanges) {
            world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        }
        lightChanges.clear();
        chunk.markDirty();
        PlayerChunkMapEntry entry = world.getPlayerChunkMap().getEntry(chunk.x, chunk.z);
        if (entry != null && entry.isSentToPlayers()) {
            // Section updates (as vanilla sends for many block changes) rather than a whole-chunk
            // packet: the client keeps its chunk and just receives every section, new ones included.
            int mask = 0;
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            for (int i = 0; i < sections.length; i++) {
                if (sections[i] != Chunk.NULL_BLOCK_STORAGE) {
                    mask |= 1 << i;
                }
            }
            if (mask == 65535) {
                entry.sendPacket(new SPacketChunkData(chunk, 0x00FF));
                entry.sendPacket(new SPacketChunkData(chunk, 0xFF00));
            } else if (mask != 0) {
                entry.sendPacket(new SPacketChunkData(chunk, mask));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void fill(WorldServer world, Chunk chunk, Blueprint.ColumnOp op, BlockPos.MutableBlockPos cursor) {
        IBlockState state = Block.getStateById(op.stateIdOrToY);
        if (op.kind == Blueprint.ColumnOp.SET_IF_NOT_SOLID) {
            cursor.setPos(op.x, op.y, op.z);
            if (!isSolid(chunk.getBlockState(cursor))) {
                put(world, chunk, cursor, state, false);
            }
            return;
        }
        for (int d = 0; d < op.maxDepth; d++) {
            int y = op.y - d;
            if (y < BlockWriter.MIN_Y) {
                return;
            }
            cursor.setPos(op.x, y, op.z);
            if (stopsFill(chunk.getBlockState(cursor))) {
                return;
            }
            put(world, chunk, cursor, state, false);
        }
    }

    /**
     * A foundation fill stops on solid ground; leaves, plants and vines (a jungle's undergrowth)
     * are filled through, so no void is left under them.
     */
    public static boolean stopsFill(IBlockState state) {
        Material m = state.getMaterial();
        return isSolid(state) && m != Material.LEAVES && m != Material.PLANTS && m != Material.VINE;
    }

    private static boolean isSolid(IBlockState state) {
        return state.getMaterial().isSolid() && state.isOpaqueCube();
    }

    /** Loaded, populated, and every neighbour loaded (correct lighting, no cascading). */
    private static boolean isReady(WorldServer world, int chunkX, int chunkZ) {
        ChunkProviderServer provider = world.getChunkProvider();
        Chunk chunk = provider.getLoadedChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isTerrainPopulated()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && provider.getLoadedChunk(chunkX + dx, chunkZ + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean anyFootprintChunkReady(WorldServer world, Job job) {
        StructureBoundingBox box = job.plan.footprint();
        for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                if (!job.done.contains(ChunkPos.asLong(cx, cz)) && isReady(world, cx, cz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distanceToPlayers(World world, int chunkX, int chunkZ) {
        double x = (chunkX << 4) + 8;
        double z = (chunkZ << 4) + 8;
        double best = Double.MAX_VALUE;
        for (EntityPlayer player : world.playerEntities) {
            double dx = player.posX - x;
            double dz = player.posZ - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        jobs.clear();
        NBTTagList list = tag.getTagList("jobs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            StructurePlan plan = StructurePlans.read(entry.getCompoundTag("plan"));
            if (plan == null) {
                continue;
            }
            LongOpenHashSet done = new LongOpenHashSet();
            int[] chunks = entry.getIntArray("done");
            for (int c = 0; c + 1 < chunks.length; c += 2) {
                done.add(ChunkPos.asLong(chunks[c], chunks[c + 1]));
            }
            UUID id = entry.hasUniqueId("citadel") ? entry.getUniqueId("citadel") : null;
            jobs.add(new Job(plan, id, done));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Job job : jobs) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("plan", job.plan.writeToNBT());
            if (job.citadelId != null) {
                entry.setUniqueId("citadel", job.citadelId);
            }
            int[] chunks = new int[job.done.size() * 2];
            int n = 0;
            for (long key : job.done) {
                chunks[n++] = (int) key;
                chunks[n++] = (int) (key >>> 32);
            }
            entry.setIntArray("done", chunks);
            list.appendTag(entry);
        }
        tag.setTag("jobs", list);
        return tag;
    }

    // ------------------------------------------------------------------ types

    private static final class Job {
        final StructurePlan plan;
        UUID citadelId;
        final LongOpenHashSet done;
        Future<BuildResult> future;
        Blueprint blueprint;
        long lastReadyTick;
        /** Blocks a direct section write did not keep, rewritten through the chunk (see verify stage). */
        int repaired;

        Job(StructurePlan plan, UUID citadelId, LongOpenHashSet done) {
            this.plan = plan;
            this.citadelId = citadelId;
            this.done = done;
        }
    }

    private static final class BuildResult {
        final CitadelInstance record;
        final Blueprint blueprint;
        final int blocks;
        final long millis;

        BuildResult(CitadelInstance record, Blueprint blueprint, int blocks, long millis) {
            this.record = record;
            this.blueprint = blueprint;
            this.blocks = blocks;
            this.millis = millis;
        }
    }
}
