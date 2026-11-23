package com.shatteredcitadels.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The idol vault of a jungle temple: its box stays protected and its gates ({@link #GATE_FLOOR})
 * closed until an idol stands on every socket. Nothing else opens it: not conquest, not reconcile.
 */
public final class VaultLock {

    /** {@code requiredFloor} of the vault's sealed gates: no floor has it, only the lock opens them. */
    public static final int GATE_FLOOR = -100;

    private final StructureBoundingBox box;
    private final List<BlockPos> sockets;
    private final boolean[] filled;

    public VaultLock(StructureBoundingBox box, List<BlockPos> sockets) {
        this.box = new StructureBoundingBox(box);
        this.sockets = new ArrayList<>();
        for (BlockPos pos : sockets) {
            this.sockets.add(pos.toImmutable());
        }
        this.filled = new boolean[sockets.size()];
    }

    public List<BlockPos> sockets() {
        return Collections.unmodifiableList(sockets);
    }

    public StructureBoundingBox box() {
        return box;
    }

    /** @return true if an idol was placed: {@code socket} is one of ours and was empty. */
    public boolean place(BlockPos socket) {
        int i = sockets.indexOf(socket);
        if (i < 0 || filled[i]) {
            return false;
        }
        filled[i] = true;
        return true;
    }

    public boolean isFilled(BlockPos socket) {
        int i = sockets.indexOf(socket);
        return i >= 0 && filled[i];
    }

    public boolean isOpen() {
        for (boolean f : filled) {
            if (!f) {
                return false;
            }
        }
        return filled.length > 0;
    }

    /** @return true while the vault is closed and {@code pos} is part of it. */
    public boolean locks(BlockPos pos) {
        return !isOpen() && box.isVecInside(pos);
    }

    /** @return the structure whose vault has {@code socket} among its sockets, or null. */
    public static CitadelInstance ownerOf(Iterable<CitadelInstance> citadels, BlockPos socket) {
        for (CitadelInstance citadel : citadels) {
            if (citadel.getVault() != null && citadel.getVault().sockets().contains(socket)) {
                return citadel;
            }
        }
        return null;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("box", new int[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ});
        NBTTagList list = new NBTTagList();
        for (BlockPos pos : sockets) {
            list.appendTag(new NBTTagLong(pos.toLong()));
        }
        tag.setTag("sockets", list);
        byte[] state = new byte[filled.length];
        for (int i = 0; i < filled.length; i++) {
            state[i] = (byte) (filled[i] ? 1 : 0);
        }
        tag.setByteArray("filled", state);
        return tag;
    }

    /** @return the lock, or null if the tag is malformed. */
    public static VaultLock read(NBTTagCompound tag) {
        int[] b = tag.getIntArray("box");
        if (b.length != 6) {
            return null;
        }
        NBTTagList list = tag.getTagList("sockets", net.minecraftforge.common.util.Constants.NBT.TAG_LONG);
        List<BlockPos> sockets = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            sockets.add(BlockPos.fromLong(((NBTTagLong) list.get(i)).getLong()));
        }
        VaultLock lock = new VaultLock(new StructureBoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), sockets);
        byte[] state = tag.getByteArray("filled");
        for (int i = 0; i < state.length && i < lock.filled.length; i++) {
            lock.filled[i] = state[i] != 0;
        }
        return lock;
    }
}
