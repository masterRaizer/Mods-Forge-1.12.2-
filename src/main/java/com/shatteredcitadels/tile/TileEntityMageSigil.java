package com.shatteredcitadels.tile;

import com.shatteredcitadels.Reference;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Hidden under the dais of a catacomb sanctum. Knows its structure, the sanctum floor, the sanctum
 * area and the teleport anchors of the guardian mage, and whether the mage was already summoned.
 */
public class TileEntityMageSigil extends TileEntity {

    public static final ResourceLocation ID = new ResourceLocation(Reference.MODID, "mage_sigil");

    private UUID structureId;
    private int floorIndex;
    private StructureBoundingBox area;
    private final List<BlockPos> anchors = new ArrayList<>();
    private boolean spawned;
    private UUID mageId;

    public void configure(UUID structureId, int floorIndex, StructureBoundingBox area, List<BlockPos> anchors) {
        this.structureId = structureId;
        this.floorIndex = floorIndex;
        this.area = area;
        this.anchors.clear();
        for (BlockPos anchor : anchors) {
            this.anchors.add(anchor.toImmutable());
        }
        markDirty();
    }

    public UUID getStructureId() { return structureId; }
    public int getFloorIndex() { return floorIndex; }
    public StructureBoundingBox getArea() { return area; }
    public List<BlockPos> getAnchors() { return Collections.unmodifiableList(anchors); }
    public boolean isSpawned() { return spawned; }
    public UUID getMageId() { return mageId; }

    public void setSpawned(boolean spawned, UUID mageId) {
        this.spawned = spawned;
        this.mageId = mageId;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        structureId = compound.hasUniqueId("structure") ? compound.getUniqueId("structure") : null;
        floorIndex = compound.getInteger("floor");
        int[] box = compound.getIntArray("area");
        area = box.length == 6 ? new StructureBoundingBox(box) : null;
        anchors.clear();
        NBTTagList list = compound.getTagList("anchors", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            anchors.add(NBTUtil.getPosFromTag(list.getCompoundTagAt(i)));
        }
        spawned = compound.getBoolean("spawned");
        mageId = compound.hasUniqueId("mage") ? compound.getUniqueId("mage") : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (structureId != null) {
            compound.setUniqueId("structure", structureId);
        }
        compound.setInteger("floor", floorIndex);
        if (area != null) {
            compound.setTag("area", area.toNBTTagIntArray());
        }
        NBTTagList list = new NBTTagList();
        for (BlockPos anchor : anchors) {
            list.appendTag(NBTUtil.createPosTag(anchor));
        }
        compound.setTag("anchors", list);
        compound.setBoolean("spawned", spawned);
        if (mageId != null) {
            compound.setUniqueId("mage", mageId);
        }
        return compound;
    }
}
