package com.shatteredcitadels.herobrine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The lair altars of a dimension and what became of each: waiting, used, or its master defeated. */
public final class HerobrineSavedData extends WorldSavedData {

    public static final String DATA_NAME = "shatteredcitadels_herobrine";

    /** The altar waits to be lit. */
    public static final int ACTIVE = 0;
    /** The boss was summoned; the altar is dormant. */
    public static final int SUMMONED = 1;
    /** The boss of this lair is dead. */
    public static final int DEFEATED = 2;

    /** One lair altar. */
    public static final class Lair {
        /** The netherrack of the altar. */
        public final BlockPos altar;
        private int state;

        Lair(BlockPos altar, int state) {
            this.altar = altar;
            this.state = Math.max(ACTIVE, Math.min(DEFEATED, state));
        }

        public int state() {
            return state;
        }

        public boolean isActive() {
            return state == ACTIVE;
        }

        public boolean isDefeated() {
            return state == DEFEATED;
        }
    }

    private final Map<Long, Lair> lairs = new LinkedHashMap<>();

    public HerobrineSavedData(String name) {
        super(name);
    }

    public HerobrineSavedData() {
        this(DATA_NAME);
    }

    /** @return the store of the dimension of that world; created on first use. Server side only. */
    public static HerobrineSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        HerobrineSavedData data = (HerobrineSavedData) storage.getOrLoadData(HerobrineSavedData.class, DATA_NAME);
        if (data == null) {
            data = new HerobrineSavedData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** Remembers a lair altar; an altar already known keeps its state. */
    public void register(BlockPos altar) {
        if (!lairs.containsKey(altar.toLong())) {
            lairs.put(altar.toLong(), new Lair(altar.toImmutable(), ACTIVE));
            markDirty();
        }
    }

    /** @return the lair whose netherrack is at that position, or null */
    public Lair lairAt(BlockPos altar) {
        return altar == null ? null : lairs.get(altar.toLong());
    }

    /** Moves a lair forward to {@code state}; a lair never goes back to an earlier state. */
    public void advance(BlockPos altar, int state) {
        Lair lair = lairAt(altar);
        if (lair != null && state > lair.state && state <= DEFEATED) {
            lair.state = state;
            markDirty();
        }
    }

    /** Makes a used altar wait again (its boss was removed without a fight); a defeated lair stays defeated. */
    public void rearm(BlockPos altar) {
        Lair lair = lairAt(altar);
        if (lair != null && lair.state == SUMMONED) {
            lair.state = ACTIVE;
            markDirty();
        }
    }

    public List<Lair> lairs() {
        return Collections.unmodifiableList(new ArrayList<>(lairs.values()));
    }

    /** @return true if the position lies within {@code radius} blocks of a lair whose master still lives */
    public boolean isHaunted(double x, double z, int radius) {
        for (Lair lair : lairs.values()) {
            if (ApparitionRule.isHaunted(x, z, lair.altar.getX(), lair.altar.getZ(), lair.isDefeated(), radius)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        lairs.clear();
        NBTTagList list = nbt.getTagList("lairs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            BlockPos altar = BlockPos.fromLong(tag.getLong("altar"));
            lairs.put(altar.toLong(), new Lair(altar, tag.getInteger("state")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Lair lair : lairs.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("altar", lair.altar.toLong());
            tag.setInteger("state", lair.state);
            list.appendTag(tag);
        }
        nbt.setTag("lairs", list);
        return nbt;
    }
}
