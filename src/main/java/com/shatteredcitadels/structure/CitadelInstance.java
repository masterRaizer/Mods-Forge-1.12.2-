package com.shatteredcitadels.structure;

import com.shatteredcitadels.palette.CitadelArchetype;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Persistent description of one generated structure: identity and name, bounds, boss arena,
 * floors with their live combat state, and the chest/spawner points and tagged rooms placed by
 * the builders. Server thread only.
 */
public final class CitadelInstance {

    private final UUID citadelId;
    private final String displayName;
    private final StructureType type;
    /** Null for structures not built from one of our archetypes (Castle Dungeons castles). */
    private final CitadelArchetype archetype;
    private final boolean skyCitadel;
    private final boolean underground;
    private final int dimension;
    private final BlockPos origin;
    private StructureBoundingBox totalBounds;
    private StructureBoundingBox bossArenaBounds;
    private BlockPos bossAltarPos;
    /** floorIndex (0 = ground floor, -1.. = crypt levels) -> floor. */
    private final TreeMap<Integer, FloorData> floors = new TreeMap<>();
    private final List<SpawnerPoint> spawnerPoints = new ArrayList<>();
    private final List<BlockPos> chestPoints = new ArrayList<>();
    private final List<GatePoint> gates = new ArrayList<>();
    /** Built volumes (tower, wings, walls, buildings); protection applies inside them only. */
    private final List<StructureBoundingBox> parts = new ArrayList<>();
    /** Blocks placed by players inside the structure: always breakable again. */
    private final java.util.Set<Long> playerPlaced = new java.util.HashSet<>();
    private final List<TaggedRoom> rooms = new ArrayList<>();
    private boolean hasCrypt;
    /** A buried catacomb fragment: never blocks a structure (NBT "buried", set since 1.6.2). */
    private boolean buriedFragment;
    private net.minecraft.util.ResourceLocation preferredBoss;
    private VaultLock vault;

    public CitadelInstance(UUID citadelId, String displayName, StructureType type, CitadelArchetype archetype,
                           int dimension, BlockPos origin, StructureBoundingBox totalBounds) {
        this.citadelId = citadelId;
        this.displayName = displayName == null ? "" : displayName;
        this.type = type;
        this.archetype = archetype;
        this.skyCitadel = type == StructureType.SKY_CITADEL;
        this.underground = type == StructureType.CRYPT || type == StructureType.STRONGHOLD;
        this.dimension = dimension;
        this.origin = origin.toImmutable();
        this.totalBounds = copy(totalBounds);
    }

    // ------------------------------------------------------------------ mutation (during build)

    public FloorData addFloor(int floorIndex, int minY, int maxY, int difficultyTier, int totalWaves, boolean bossFloor) {
        FloorData floor = new FloorData(floorIndex, minY, maxY, difficultyTier, totalWaves, bossFloor);
        floors.put(floorIndex, floor);
        return floor;
    }

    public void setBossArena(StructureBoundingBox bounds, BlockPos altar) {
        this.bossArenaBounds = bounds == null ? null : copy(bounds);
        this.bossAltarPos = altar == null ? null : altar.toImmutable();
    }

    public void addSpawnerPoint(BlockPos pos, int floorIndex, int tier) {
        spawnerPoints.add(new SpawnerPoint(pos.toImmutable(), floorIndex, tier));
    }

    /**
     * @param requiredFloor floor whose clearing opens this gate
     * @param restoreStateId {@code Block.getStateId} of the block the gate replaced
     */
    public void addGate(BlockPos pos, int requiredFloor, int restoreStateId) {
        gates.add(new GatePoint(pos.toImmutable(), requiredFloor, restoreStateId));
    }

    public void removeGate(GatePoint gate) {
        gates.remove(gate);
    }

    public List<GatePoint> getGates() { return Collections.unmodifiableList(gates); }

    /** Adds a built volume and grows the total bounds to cover it. */
    public void addPart(StructureBoundingBox box) {
        parts.add(copy(box));
        expandBounds(box);
    }

    public List<StructureBoundingBox> getParts() { return Collections.unmodifiableList(parts); }

    /**
     * A buried catacomb fragment: it never blocks a structure. Fragments saved by 1.6.0/1.6.1 carry
     * no flag; they are the only crypts without parts that span a single low level (crypts from
     * worlds before 1.4.0 have no parts either, but reach up to the surface).
     */
    public boolean isBuriedFragment() {
        return buriedFragment || type == StructureType.CRYPT && parts.isEmpty()
                && totalBounds.maxY - totalBounds.minY <= LEGACY_FRAGMENT_MAX_HEIGHT;
    }

    /**
     * Strongholds, buried fragments and desert temples without a boss tomb are never protected
     * (mineable like vanilla); a pharaoh's tomb protects only its tomb part.
     */
    public boolean isUnprotected() {
        return type == StructureType.STRONGHOLD || type == StructureType.BRIDGE || type == StructureType.HEROBRINE_LAIR || isBuriedFragment() || (type == StructureType.DESERT_TEMPLE || type == StructureType.JUNGLE_TEMPLE) && parts.isEmpty();
    }

    /** @return the boss the altar summons first (e.g. Atum's pharaoh), or null for the tier pool. */
    public net.minecraft.util.ResourceLocation getPreferredBoss() {
        return preferredBoss;
    }

    /** The idol vault of a jungle temple, or null. */
    public VaultLock getVault() {
        return vault;
    }

    public void setVault(VaultLock vault) {
        this.vault = vault;
    }

    public void setPreferredBoss(net.minecraft.util.ResourceLocation preferredBoss) {
        this.preferredBoss = preferredBoss;
    }

    public void setBuriedFragment(boolean buriedFragment) {
        this.buriedFragment = buriedFragment;
    }

    /** Height of a fragment's bounds: one level (floor to raised ceilings) plus a margin. */
    private static final int LEGACY_FRAGMENT_MAX_HEIGHT = 12;

    /** @return true if the position is inside a built part (or anywhere, for records without parts). */
    public boolean isInPart(BlockPos pos) {
        if (parts.isEmpty()) {
            return contains(pos);
        }
        for (StructureBoundingBox box : parts) {
            if (box.isVecInside(pos)) {
                return true;
            }
        }
        return false;
    }

    public void markPlayerPlaced(BlockPos pos) {
        playerPlaced.add(pos.toLong());
    }

    /** @return true (and forgets the position) if a player placed the block at {@code pos}. */
    public boolean consumePlayerPlaced(BlockPos pos) {
        return playerPlaced.remove(pos.toLong());
    }

    public boolean isPlayerPlaced(BlockPos pos) {
        return playerPlaced.contains(pos.toLong());
    }

    /** @return true once the structure's boss is defeated: gates open and protection lifts. */
    public boolean isConquered() {
        for (FloorData floor : floors.values()) {
            // Crypt bosses live on a regular level: the altar marks whichever floor holds the arena.
            if (floor.isBossDefeated() && !floor.isSideBoss()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Conquest as it applies at {@code pos}: below the ground floor, catacombs whose guardian mage
     * still lives stay sealed and protected even after the surface boss has fallen.
     */
    public boolean isConqueredAt(BlockPos pos) {
        if (!isConquered()) {
            return false;
        }
        if (pos.getY() < getOrigin().getY() - 1) {
            for (FloorData floor : floors.values()) {
                if (floor.isSideBoss() && !floor.isBossDefeated()) {
                    return false;
                }
            }
        }
        return true;
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

    public UUID getCitadelId() { return citadelId; }
    /** Name in the form produced by {@link CitadelNameGenerator}; localised on display. */
    public String getDisplayName() { return displayName; }
    public StructureType getType() { return type; }
    public CitadelArchetype getArchetype() { return archetype; }
    public boolean isSkyCitadel() { return skyCitadel; }
    public boolean isUnderground() { return underground; }
    public int getDimension() { return dimension; }
    public BlockPos getOrigin() { return origin; }
    public StructureBoundingBox getTotalBounds() { return copy(totalBounds); }
    public StructureBoundingBox getBossArenaBounds() { return bossArenaBounds == null ? null : copy(bossArenaBounds); }
    public BlockPos getBossAltarPos() { return bossAltarPos; }
    public Map<Integer, FloorData> getFloors() { return Collections.unmodifiableMap(floors); }
    public FloorData getFloor(int index) { return floors.get(index); }
    public List<SpawnerPoint> getSpawnerPoints() { return Collections.unmodifiableList(spawnerPoints); }
    public List<BlockPos> getChestPoints() { return Collections.unmodifiableList(chestPoints); }
    public List<TaggedRoom> getRooms() { return Collections.unmodifiableList(rooms); }
    public boolean hasCrypt() { return hasCrypt; }

    /** @return archetype key, or the structure type for structures without an archetype. */
    public String getArchetypeKey() {
        return archetype != null ? archetype.key() : type.name().toLowerCase(java.util.Locale.ROOT);
    }

    public int getOriginChunkX() { return origin.getX() >> 4; }
    public int getOriginChunkZ() { return origin.getZ() >> 4; }

    public boolean contains(BlockPos pos) {
        return totalBounds.isVecInside(pos);
    }

    /**
     * @return the floor whose Y span contains {@code y}, ignoring horizontal areas (floors without
     * an area win, so the courtyard never shadows the keep), or null.
     */
    public FloorData floorAt(int y) {
        FloorData fallback = null;
        for (FloorData floor : floors.values()) {
            if (floor.containsY(y)) {
                if (!floor.hasArea()) {
                    return floor;
                }
                fallback = fallback == null ? floor : fallback;
            }
        }
        return fallback;
    }

    /** @return the smallest floor containing the position (keep floor over courtyard), or null. */
    public FloorData floorAt(BlockPos pos) {
        FloorData best = null;
        for (FloorData floor : floors.values()) {
            if (floor.contains(pos) && (best == null || floor.areaSize() < best.areaSize())) {
                best = floor;
            }
        }
        return best;
    }

    /** @return true unless the floor waits for another floor that is not cleared yet. */
    public boolean isUnlocked(FloorData floor) {
        if (floor == null || floor.getRequiresFloor() == Integer.MIN_VALUE) {
            return true;
        }
        FloorData required = floors.get(floor.getRequiresFloor());
        return required == null || required.isCleared();
    }

    /** @return true if floor 0 of this structure is a castle courtyard. */
    public boolean hasCourtyard() {
        FloorData ground = floors.get(0);
        return ground != null && ground.isCourtyard();
    }

    /** @return number of floors above ground (index &gt;= 0). */
    public int getSurfaceFloorCount() {
        return floors.tailMap(0, true).size();
    }

    /** @return number of crypt levels (index &lt; 0). */
    public int getUndergroundLevelCount() {
        return floors.headMap(0, false).size();
    }

    /** @return the floor that holds the boss arena (crypt bosses live on their deepest level). */
    public FloorData getBossFloor() {
        if (bossArenaBounds != null) {
            FloorData floor = floorAt(bossArenaBounds.minY + 1);
            if (floor != null) {
                return floor;
            }
        }
        return floors.isEmpty() ? null : floors.lastEntry().getValue();
    }

    public int getTopFloorIndex() {
        return floors.isEmpty() ? 0 : floors.lastKey();
    }

    // ------------------------------------------------------------------ NBT

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("id", citadelId);
        tag.setString("name", displayName);
        tag.setString("type", type.name());
        tag.setString("archetype", archetype == null ? "" : archetype.key());
        tag.setBoolean("sky", skyCitadel);
        tag.setBoolean("underground", underground);
        tag.setInteger("dim", dimension);
        tag.setLong("origin", origin.toLong());
        tag.setTag("bounds", totalBounds.toNBTTagIntArray());
        if (bossArenaBounds != null) {
            tag.setTag("arena", bossArenaBounds.toNBTTagIntArray());
        }
        if (bossAltarPos != null) {
            tag.setLong("altar", bossAltarPos.toLong());
        }
        NBTTagList floorList = new NBTTagList();
        for (FloorData floor : floors.values()) {
            floorList.appendTag(floor.writeToNBT());
        }
        tag.setTag("floors", floorList);
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
        NBTTagList gateList = new NBTTagList();
        for (GatePoint gate : gates) {
            NBTTagCompound g = new NBTTagCompound();
            g.setLong("pos", gate.pos.toLong());
            g.setInteger("floor", gate.requiredFloor);
            g.setInteger("restore", gate.restoreStateId);
            gateList.appendTag(g);
        }
        tag.setTag("gates", gateList);
        NBTTagList partList = new NBTTagList();
        for (StructureBoundingBox box : parts) {
            partList.appendTag(box.toNBTTagIntArray());
        }
        tag.setTag("parts", partList);
        NBTTagList placedList = new NBTTagList();
        for (Long pos : playerPlaced) {
            placedList.appendTag(new net.minecraft.nbt.NBTTagLong(pos));
        }
        tag.setTag("placed", placedList);
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
        if (buriedFragment) {
            tag.setBoolean("buried", true);
        }
        if (preferredBoss != null) {
            tag.setString("preferredBoss", preferredBoss.toString());
        }
        if (vault != null) {
            tag.setTag("vault", vault.write());
        }
        return tag;
    }

    /** @return the instance, or null if the tag lacks an identity (corrupt entry). */
    public static CitadelInstance readFromNBT(NBTTagCompound tag) {
        if (!tag.hasUniqueId("id")) {
            return null;
        }
        StructureType type = StructureType.byName(tag.getString("type"));
        CitadelInstance instance = new CitadelInstance(
                tag.getUniqueId("id"),
                tag.getString("name"),
                type,
                CitadelArchetype.byKey(tag.getString("archetype")),
                tag.getInteger("dim"),
                BlockPos.fromLong(tag.getLong("origin")),
                readBox(tag.getIntArray("bounds")));
        if (tag.hasKey("arena", Constants.NBT.TAG_INT_ARRAY)) {
            instance.bossArenaBounds = readBox(tag.getIntArray("arena"));
        }
        if (tag.hasKey("altar", Constants.NBT.TAG_LONG)) {
            instance.bossAltarPos = BlockPos.fromLong(tag.getLong("altar"));
        }
        NBTTagList floorList = tag.getTagList("floors", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < floorList.tagCount(); i++) {
            FloorData floor = FloorData.readFromNBT(floorList.getCompoundTagAt(i));
            instance.floors.put(floor.getIndex(), floor);
        }
        NBTTagList spawners = tag.getTagList("spawners", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < spawners.tagCount(); i++) {
            NBTTagCompound s = spawners.getCompoundTagAt(i);
            instance.addSpawnerPoint(BlockPos.fromLong(s.getLong("pos")), s.getInteger("floor"), s.getInteger("tier"));
        }
        NBTTagList chests = tag.getTagList("chests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chests.tagCount(); i++) {
            instance.addChestPoint(BlockPos.fromLong(chests.getCompoundTagAt(i).getLong("pos")));
        }
        NBTTagList gateList = tag.getTagList("gates", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < gateList.tagCount(); i++) {
            NBTTagCompound g = gateList.getCompoundTagAt(i);
            instance.addGate(BlockPos.fromLong(g.getLong("pos")), g.getInteger("floor"), g.getInteger("restore"));
        }
        NBTTagList partList = tag.getTagList("parts", Constants.NBT.TAG_INT_ARRAY);
        for (int i = 0; i < partList.tagCount(); i++) {
            instance.parts.add(readBox(partList.getIntArrayAt(i)));
        }
        NBTTagList placedList = tag.getTagList("placed", Constants.NBT.TAG_LONG);
        for (int i = 0; i < placedList.tagCount(); i++) {
            net.minecraft.nbt.NBTBase entry = placedList.get(i);
            if (entry instanceof net.minecraft.nbt.NBTTagLong) {
                instance.playerPlaced.add(((net.minecraft.nbt.NBTTagLong) entry).getLong());
            }
        }
        NBTTagList roomList = tag.getTagList("rooms", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < roomList.tagCount(); i++) {
            NBTTagCompound r = roomList.getCompoundTagAt(i);
            instance.addRoom(readBox(r.getIntArray("box")), RoomTag.byName(r.getString("tag")), r.getInteger("floor"));
        }
        instance.hasCrypt = tag.getBoolean("crypt");
        instance.buriedFragment = tag.getBoolean("buried");
        instance.preferredBoss = tag.hasKey("preferredBoss") ? new net.minecraft.util.ResourceLocation(tag.getString("preferredBoss")) : null;
        instance.vault = tag.hasKey("vault") ? VaultLock.read(tag.getCompoundTag("vault")) : null;
        return instance;
    }

    private static StructureBoundingBox readBox(int[] data) {
        return data != null && data.length == 6 ? new StructureBoundingBox(data) : new StructureBoundingBox(0, 0, 0, 0, 0, 0);
    }

    private static StructureBoundingBox copy(StructureBoundingBox box) {
        return box == null ? new StructureBoundingBox(0, 0, 0, 0, 0, 0)
                : new StructureBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    // ------------------------------------------------------------------ value types

    /** A sealed gate placed at generation; removed from the record once opened. */
    public static final class GatePoint {
        public final BlockPos pos;
        public final int requiredFloor;
        public final int restoreStateId;

        GatePoint(BlockPos pos, int requiredFloor, int restoreStateId) {
            this.pos = pos;
            this.requiredFloor = requiredFloor;
            this.restoreStateId = restoreStateId;
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
