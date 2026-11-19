package com.shatteredcitadels.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything later stages need to know about one generated structure: floor Y ranges, total
 * bounds, boss arena, chest and spawner points and tagged rooms. Server thread only.
 */
public final class CitadelDataRecord {

    private final long id;
    private final StructureType type;
    private final String archetype;
    private final int dimension;
    private final BlockPos origin;
    private StructureBoundingBox totalBounds;
    /** floorIndex (0 = ground floor; negative = crypt levels) -> span. */
    private final TreeMap<Integer, FloorSpan> floors = new TreeMap<>();
    private StructureBoundingBox bossArenaBounds;
    private BlockPos bossAltarPos;
    private final List<SpawnerPoint> spawnerPoints = new ArrayList<>();
    private final List<BlockPos> chestPoints = new ArrayList<>();
    private final List<TaggedRoom> rooms = new ArrayList<>();
    private boolean hasCrypt;

    public CitadelDataRecord(long id, StructureType type, String archetype, int dimension, BlockPos origin,
                             StructureBoundingBox totalBounds) {
        this.id = id;
        this.type = type;
        this.archetype = archetype == null ? "" : archetype;
        this.dimension = dimension;
        this.origin = origin.toImmutable();
        this.totalBounds = copy(totalBounds);
    }

    // ------------------------------------------------------------------ mutation (during build)

    public void addFloor(int floorIndex, int minY, int maxY) {
        floors.put(floorIndex, new FloorSpan(floorIndex, Math.min(minY, maxY), Math.max(minY, maxY)));
    }

    public void setBossArena(StructureBoundingBox bounds, BlockPos altar) {
        this.bossArenaBounds = copy(bounds);
        this.bossAltarPos = altar == null ? null : altar.toImmutable();
    }

    public void addSpawnerPoint(BlockPos pos, int floorIndex, int tier) {
        spawnerPoints.add(new SpawnerPoint(pos.toImmutable(), floorIndex, tier));
    }

    public void addChestPoint(BlockPos pos) {
        chestPoints.add(pos.toImmutable());
    }

    public void addRoom(StructureBoundingBox bounds, RoomTag tag, int floorIndex) {
        rooms.add(new TaggedRoom(copy(bounds), tag, floorIndex));
    }

    public void setHasCrypt(boolean hasCrypt) {
        this.hasCrypt = hasCrypt;
    }

    /** Grows the total bounds, e.g. when a crypt is attached below a citadel. */
    public void expandBounds(StructureBoundingBox other) {
        if (other != null) {
            totalBounds.expandTo(other);
        }
    }

    // ------------------------------------------------------------------ queries

    public long getId() { return id; }
    public StructureType getType() { return type; }
    public String getArchetype() { return archetype; }
    public int getDimension() { return dimension; }
    public BlockPos getOrigin() { return origin; }
    public StructureBoundingBox getTotalBounds() { return copy(totalBounds); }
    public Map<Integer, FloorSpan> getFloors() { return Collections.unmodifiableMap(floors); }
    public StructureBoundingBox getBossArenaBounds() { return bossArenaBounds == null ? null : copy(bossArenaBounds); }
    public BlockPos getBossAltarPos() { return bossAltarPos; }
    public List<SpawnerPoint> getSpawnerPoints() { return Collections.unmodifiableList(spawnerPoints); }
    public List<BlockPos> getChestPoints() { return Collections.unmodifiableList(chestPoints); }
    public List<TaggedRoom> getRooms() { return Collections.unmodifiableList(rooms); }
    public boolean hasCrypt() { return hasCrypt; }

    public int getOriginChunkX() { return origin.getX() >> 4; }
    public int getOriginChunkZ() { return origin.getZ() >> 4; }

    public boolean contains(BlockPos pos) {
        return totalBounds.isVecInside(pos);
    }

    /** @return the floor whose Y span contains {@code y}, or null. */
    public FloorSpan floorAt(int y) {
        for (FloorSpan span : floors.values()) {
            if (y >= span.minY && y <= span.maxY) {
                return span;
            }
        }
        return null;
    }

    public int getTopFloorIndex() {
        return floors.isEmpty() ? 0 : floors.lastKey();
    }

    // ------------------------------------------------------------------ NBT

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("id", id);
        tag.setString("type", type.name());
        tag.setString("archetype", archetype);
        tag.setInteger("dim", dimension);
        tag.setLong("origin", origin.toLong());
        tag.setTag("bounds", totalBounds.toNBTTagIntArray());
        NBTTagList floorList = new NBTTagList();
        for (FloorSpan span : floors.values()) {
            floorList.appendTag(new NBTTagIntArray(new int[]{span.index, span.minY, span.maxY}));
        }
        tag.setTag("floors", floorList);
        if (bossArenaBounds != null) {
            tag.setTag("arena", bossArenaBounds.toNBTTagIntArray());
        }
        if (bossAltarPos != null) {
            tag.setLong("altar", bossAltarPos.toLong());
        }
        NBTTagList spawners = new NBTTagList();
        for (SpawnerPoint point : spawnerPoints) {
            NBTTagCompound s = new NBTTagCompound();
            s.setLong("pos", point.pos.toLong());
            s.setInteger("floor", point.floorIndex);
            s.setInteger("tier", point.tier);
            spawners.appendTag(s);
        }
        tag.setTag("spawners", spawners);
        NBTTagList chests = new NBTTagList();
        for (BlockPos pos : chestPoints) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", pos.toLong());
            chests.appendTag(c);
        }
        tag.setTag("chests", chests);
        NBTTagList roomList = new NBTTagList();
        for (TaggedRoom room : rooms) {
            NBTTagCompound r = new NBTTagCompound();
            r.setTag("box", room.bounds.toNBTTagIntArray());
            r.setString("tag", room.tag.name());
            r.setInteger("floor", room.floorIndex);
            roomList.appendTag(r);
        }
        tag.setTag("rooms", roomList);
        tag.setBoolean("crypt", hasCrypt);
        return tag;
    }

    public static CitadelDataRecord readFromNBT(NBTTagCompound tag) {
        CitadelDataRecord record = new CitadelDataRecord(
                tag.getLong("id"),
                StructureType.byName(tag.getString("type")),
                tag.getString("archetype"),
                tag.getInteger("dim"),
                BlockPos.fromLong(tag.getLong("origin")),
                readBox(tag.getIntArray("bounds")));
        NBTTagList floorList = tag.getTagList("floors", Constants.NBT.TAG_INT_ARRAY);
        for (int i = 0; i < floorList.tagCount(); i++) {
            int[] f = floorList.getIntArrayAt(i);
            if (f.length == 3) {
                record.addFloor(f[0], f[1], f[2]);
            }
        }
        if (tag.hasKey("arena", Constants.NBT.TAG_INT_ARRAY)) {
            record.bossArenaBounds = readBox(tag.getIntArray("arena"));
        }
        if (tag.hasKey("altar", Constants.NBT.TAG_LONG)) {
            record.bossAltarPos = BlockPos.fromLong(tag.getLong("altar"));
        }
        NBTTagList spawners = tag.getTagList("spawners", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < spawners.tagCount(); i++) {
            NBTTagCompound s = spawners.getCompoundTagAt(i);
            record.addSpawnerPoint(BlockPos.fromLong(s.getLong("pos")), s.getInteger("floor"), s.getInteger("tier"));
        }
        NBTTagList chests = tag.getTagList("chests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chests.tagCount(); i++) {
            record.addChestPoint(BlockPos.fromLong(chests.getCompoundTagAt(i).getLong("pos")));
        }
        NBTTagList roomList = tag.getTagList("rooms", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < roomList.tagCount(); i++) {
            NBTTagCompound r = roomList.getCompoundTagAt(i);
            record.addRoom(readBox(r.getIntArray("box")), RoomTag.byName(r.getString("tag")), r.getInteger("floor"));
        }
        record.hasCrypt = tag.getBoolean("crypt");
        return record;
    }

    private static StructureBoundingBox readBox(int[] data) {
        return data != null && data.length == 6 ? new StructureBoundingBox(data) : new StructureBoundingBox(0, 0, 0, 0, 0, 0);
    }

    private static StructureBoundingBox copy(StructureBoundingBox box) {
        return box == null ? new StructureBoundingBox(0, 0, 0, 0, 0, 0)
                : new StructureBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    // ------------------------------------------------------------------ value types

    public static final class FloorSpan {
        public final int index;
        public final int minY;
        public final int maxY;

        FloorSpan(int index, int minY, int maxY) {
            this.index = index;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public static final class SpawnerPoint {
        public final BlockPos pos;
        public final int floorIndex;
        public final int tier;

        SpawnerPoint(BlockPos pos, int floorIndex, int tier) {
            this.pos = pos;
            this.floorIndex = floorIndex;
            this.tier = tier;
        }
    }

    public static final class TaggedRoom {
        public final StructureBoundingBox bounds;
        public final RoomTag tag;
        public final int floorIndex;

        TaggedRoom(StructureBoundingBox bounds, RoomTag tag, int floorIndex) {
            this.bounds = bounds;
            this.tag = tag;
            this.floorIndex = floorIndex;
        }
    }
}
