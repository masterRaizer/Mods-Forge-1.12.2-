package com.shatteredcitadels.structure;

import com.shatteredcitadels.Reference;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension store of every citadel, crypt and adopted Castle Dungeons castle. A per-chunk
 * spatial index answers {@link #getCitadelAt(BlockPos)} with one hash lookup, so the floor
 * tracker can query it for every player without lag.
 */
public final class CitadelWorldSavedData extends WorldSavedData {

    public static final String DATA_NAME = Reference.MODID + "_citadels";

    private final Map<UUID, CitadelInstance> citadels = new LinkedHashMap<>();
    /** ChunkPos.asLong(x, z) -> instances whose total bounds touch that chunk. */
    private final Long2ObjectOpenHashMap<List<CitadelInstance>> chunkIndex = new Long2ObjectOpenHashMap<>();

    /** Required by {@link MapStorage}, which instantiates saved data reflectively. */
    public CitadelWorldSavedData(String name) {
        super(name);
    }

    /** @return the store of the world's dimension; created on first use. Server side only. */
    public static CitadelWorldSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        CitadelWorldSavedData data = (CitadelWorldSavedData) storage.getOrLoadData(CitadelWorldSavedData.class, DATA_NAME);
        if (data == null) {
            data = new CitadelWorldSavedData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    // ------------------------------------------------------------------ registration

    /** Registers (or replaces) an instance and indexes it. */
    public void register(CitadelInstance instance) {
        CitadelInstance previous = citadels.put(instance.getCitadelId(), instance);
        if (previous != null) {
            unindex(previous);
        }
        index(instance);
        markDirty();
    }

    /** Re-indexes an instance whose bounds changed after registration. */
    public void update(CitadelInstance instance) {
        if (citadels.containsKey(instance.getCitadelId())) {
            unindex(instance);
            index(instance);
            markDirty();
        }
    }

    // ------------------------------------------------------------------ queries

    public CitadelInstance getById(UUID id) {
        return id == null ? null : citadels.get(id);
    }

    public List<CitadelInstance> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(citadels.values()));
    }

    public int size() {
        return citadels.size();
    }

    /** @return the structure whose bounds contain the position, or null. O(1) average. */
    public CitadelInstance getCitadelAt(BlockPos pos) {
        List<CitadelInstance> list = chunkIndex.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (list == null) {
            return null;
        }
        for (CitadelInstance instance : list) {
            if (instance.contains(pos)) {
                return instance;
            }
        }
        return null;
    }

    /** @return true when a tracked structure other than a buried fragment touches the chunk. */
    public boolean isChunkOccupiedByStructure(int chunkX, int chunkZ) {
        List<CitadelInstance> list = chunkIndex.get(ChunkPos.asLong(chunkX, chunkZ));
        if (list != null) {
            for (CitadelInstance instance : list) {
                if (!instance.isBuriedFragment()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return true when any tracked structure touches the chunk. */
    public boolean isChunkOccupied(int chunkX, int chunkZ) {
        List<CitadelInstance> list = chunkIndex.get(ChunkPos.asLong(chunkX, chunkZ));
        return list != null && !list.isEmpty();
    }

    /**
     * @return true when no tracked citadel or castle origin lies closer than
     * {@code minDistanceChunks} (Euclidean, in chunks). Standalone crypts are ignored.
     */
    public boolean isFarEnough(int chunkX, int chunkZ, int minDistanceChunks) {
        long limit = (long) minDistanceChunks * minDistanceChunks;
        for (CitadelInstance instance : citadels.values()) {
            if (instance.isUnderground()) {
                continue;
            }
            long dx = instance.getOriginChunkX() - chunkX;
            long dz = instance.getOriginChunkZ() - chunkZ;
            if (dx * dx + dz * dz < limit) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ index

    private void index(CitadelInstance instance) {
        StructureBoundingBox box = instance.getTotalBounds();
        for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                List<CitadelInstance> list = chunkIndex.get(key);
                if (list == null) {
                    list = new ArrayList<>(1);
                    chunkIndex.put(key, list);
                }
                if (!list.contains(instance)) {
                    list.add(instance);
                }
            }
        }
    }

    private void unindex(CitadelInstance instance) {
        UUID id = instance.getCitadelId();
        List<Long> empty = new ArrayList<>();
        for (Map.Entry<Long, List<CitadelInstance>> entry : chunkIndex.entrySet()) {
            entry.getValue().removeIf(other -> other.getCitadelId().equals(id));
            if (entry.getValue().isEmpty()) {
                empty.add(entry.getKey());
            }
        }
        for (Long key : empty) {
            chunkIndex.remove(key.longValue());
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        citadels.clear();
        chunkIndex.clear();
        NBTTagList list = nbt.getTagList("citadels", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            CitadelInstance instance = CitadelInstance.readFromNBT(list.getCompoundTagAt(i));
            if (instance != null) {
                citadels.put(instance.getCitadelId(), instance);
                index(instance);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (CitadelInstance instance : citadels.values()) {
            list.appendTag(instance.writeToNBT());
        }
        compound.setTag("citadels", list);
        compound.setInteger("version", 1);
        return compound;
    }

    /** Test hook: number of indexed chunks. */
    int indexedChunkCount() {
        return chunkIndex.size();
    }

    /** Test hook: snapshot of the index keys. */
    Map<Long, Integer> indexSnapshot() {
        Map<Long, Integer> snapshot = new HashMap<>();
        for (Map.Entry<Long, List<CitadelInstance>> entry : chunkIndex.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().size());
        }
        return snapshot;
    }
}
