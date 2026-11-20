package com.shatteredcitadels.tile;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.entity.EntityGuardianMage;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.structure.MageSanctum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
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
public class TileEntityMageSigil extends TileEntity implements ITickable {

    /** A summoned mage that vanished (killed by a command, lost to a bug) is summoned again after this long. */
    private static final int LOST_MAGE_TICKS = 600;

    public static final ResourceLocation ID = new ResourceLocation(Reference.MODID, "mage_sigil");

    private UUID structureId;
    private int floorIndex;
    private StructureBoundingBox area;
    private final List<BlockPos> anchors = new ArrayList<>();
    private boolean spawned;
    private UUID mageId;
    private int missingTicks;

    /** True when the mage should appear now. */
    public static boolean shouldSpawn(boolean spawned, boolean unlocked, boolean bossDefeated, boolean playerInside,
                                      boolean mageAlive) {
        return !spawned && unlocked && !bossDefeated && playerInside && !mageAlive;
    }

    @Override
    public void update() {
        if (world.isRemote || world.getTotalWorldTime() % 20 != 0 || structureId == null || area == null
                || !(world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) world;
        CitadelInstance rec = CitadelWorldSavedData.get(world).getById(structureId);
        FloorData floor = rec == null ? null : rec.getFloor(floorIndex);
        if (floor == null) {
            return;
        }
        List<EntityPlayer> inside = playersInside();
        boolean playerInside = !inside.isEmpty();
        Entity mage = mageId == null ? null : server.getEntityFromUuid(mageId);
        boolean mageAlive = mage != null && mage.isEntityAlive();
        if (spawned && !mageAlive && !floor.isBossDefeated() && playerInside) {
            missingTicks += 20;
            if (missingTicks >= LOST_MAGE_TICKS) {
                spawned = false;
                missingTicks = 0;
            }
        } else {
            missingTicks = 0;
        }
        if (shouldSpawn(spawned, rec.isUnlocked(floor), floor.isBossDefeated(), playerInside, mageAlive)) {
            summon(server, inside);
        }
    }

    private List<EntityPlayer> playersInside() {
        AxisAlignedBB box = new AxisAlignedBB(area.minX, area.minY, area.minZ, area.maxX + 1, area.maxY + 1, area.maxZ + 1);
        return world.getEntitiesWithinAABB(EntityPlayer.class, box, p -> p != null && !p.isSpectator() && p.isEntityAlive());
    }

    private void summon(WorldServer server, List<EntityPlayer> witnesses) {
        EntityGuardianMage mage = new EntityGuardianMage(server);
        mage.setLocationAndAngles(pos.getX() + 0.5D, pos.getY() + 2, pos.getZ() + 0.5D, server.rand.nextFloat() * 360.0F, 0.0F);
        mage.setHomeArea(area, anchors, structureId, floorIndex);
        mage.onInitialSpawn(server.getDifficultyForLocation(pos), null);
        if (!server.spawnEntity(mage)) {
            return;
        }
        server.spawnParticle(EnumParticleTypes.PORTAL, mage.posX, mage.posY + 1.0D, mage.posZ, 80, 0.6D, 1.0D, 0.6D, 0.3D);
        server.playSound(null, pos, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2.0F, 0.7F);
        setSpawned(true, mage.getUniqueID());
        for (EntityPlayer player : witnesses) {
            MageSanctum.title((EntityPlayerMP) player, "shatteredcitadels.mage.title", "shatteredcitadels.mage.subtitle");
        }
    }

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
