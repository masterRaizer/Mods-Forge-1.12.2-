package com.shatteredcitadels.structure;

import com.shatteredcitadels.Reference;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
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

/**
 * Per-dimension structure tracker ("floor tracker"). Stores every citadel, crypt and adopted
 * Castle Dungeons castle, and answers distance and containment queries during world
 * generation without touching unloaded chunks.
 */
public final class CitadelSavedData extends WorldSavedData {

    public static final String DATA_NAME = Reference.MODID + "_structures";

    /** Grid cell for the spatial index, in chunks. Large enough that structures span few cells. */
    private static final int CELL_CHUNKS = 8;

    private final Map<Long, CitadelDataRecord> records = new LinkedHashMap<>();
    /** cell key -> record IDs whose bounds touch the cell. */
    private final Map<Long, List<Long>> spatialIndex = new HashMap<>();
    private long nextId = 1L;

    public CitadelSavedData() {
        this(DATA_NAME);
    }

    /** Required by {@link MapStorage} reflection. */
    public CitadelSavedData(String name) {
        super(name);
    }

    /** @return the tracker of the given world's dimension; created on first use. Server side. */
    public static CitadelSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        CitadelSavedData data = (CitadelSavedData) storage.getOrLoadData(CitadelSavedData.class, DATA_NAME);
        if (data == null) {
            data = new CitadelSavedData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public long allocateId() {
        long id = nextId++;
        markDirty();
        return id;
    }

    public void add(CitadelDataRecord record) {
        records.put(record.getId(), record);
        if (record.getId() >= nextId) {
            nextId = record.getId() + 1;
        }
        index(record);
        markDirty();
    }

    /** Re-indexes a record whose bounds grew after it was added. */
    public void update(CitadelDataRecord record) {
        if (records.containsKey(record.getId())) {
            unindex(record.getId());
            index(record);
            markDirty();
        }
    }

    public CitadelDataRecord getById(long id) {
        return records.get(id);
    }

    public List<CitadelDataRecord> all() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }

    public int size() {
        return records.size();
    }

    /** @return the structure containing the position, or null. */
    public CitadelDataRecord findContaining(BlockPos pos) {
        List<Long> ids = spatialIndex.get(cellKey(pos.getX() >> 4, pos.getZ() >> 4));
        if (ids == null) {
            return null;
        }
        for (Long id : ids) {
            CitadelDataRecord record = records.get(id);
            if (record != null && record.contains(pos)) {
                return record;
            }
        }
        return null;
    }

    /**
     * @return true when no tracked structure origin lies closer than {@code minDistanceChunks}
     * (Euclidean, in chunks) to the given chunk.
     */
    public boolean isFarEnough(int chunkX, int chunkZ, int minDistanceChunks) {
        long limit = (long) minDistanceChunks * minDistanceChunks;
        for (CitadelDataRecord record : records.values()) {
            if (record.getType() == StructureType.CRYPT) {
                continue;
            }
            long dx = record.getOriginChunkX() - chunkX;
            long dz = record.getOriginChunkZ() - chunkZ;
            if (dx * dx + dz * dz < limit) {
                return false;
            }
        }
        return true;
    }

    /** @return true when any tracked structure touches the chunk. */
    public boolean isChunkOccupied(int chunkX, int chunkZ) {
        List<Long> ids = spatialIndex.get(cellKey(chunkX, chunkZ));
        if (ids == null) {
            return false;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (Long id : ids) {
            CitadelDataRecord record = records.get(id);
            if (record != null && record.getTotalBounds().intersectsWith(minX, minZ, minX + 15, minZ + 15)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ index

    private void index(CitadelDataRecord record) {
        StructureBoundingBox box = record.getTotalBounds();
        int minCellX = Math.floorDiv(box.minX >> 4, CELL_CHUNKS);
        int maxCellX = Math.floorDiv(box.maxX >> 4, CELL_CHUNKS);
        int minCellZ = Math.floorDiv(box.minZ >> 4, CELL_CHUNKS);
        int maxCellZ = Math.floorDiv(box.maxZ >> 4, CELL_CHUNKS);
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                List<Long> ids = spatialIndex.get(key);
                if (ids == null) {
                    ids = new ArrayList<>(2);
                    spatialIndex.put(key, ids);
                }
                if (!ids.contains(record.getId())) {
                    ids.add(record.getId());
                }
            }
        }
    }

    private void unindex(long id) {
        for (List<Long> ids : spatialIndex.values()) {
            ids.remove(Long.valueOf(id));
        }
    }

    private static long cellKey(int chunkX, int chunkZ) {
        int cx = Math.floorDiv(chunkX, CELL_CHUNKS);
        int cz = Math.floorDiv(chunkZ, CELL_CHUNKS);
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        records.clear();
        spatialIndex.clear();
        nextId = Math.max(1L, nbt.getLong("nextId"));
        NBTTagList list = nbt.getTagList("records", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            CitadelDataRecord record = CitadelDataRecord.readFromNBT(list.getCompoundTagAt(i));
            records.put(record.getId(), record);
            nextId = Math.max(nextId, record.getId() + 1);
            index(record);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setLong("nextId", nextId);
        NBTTagList list = new NBTTagList();
        for (CitadelDataRecord record : records.values()) {
            list.appendTag(record.writeToNBT());
        }
        compound.setTag("records", list);
        return compound;
    }
}
