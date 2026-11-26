package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A lair: one of three small buildings around the classic altar, with one chest. Its record only
 * marks the place; the altar itself is remembered in {@link HerobrineSavedData} once it is written.
 */
public final class HerobrineLairPlan implements StructurePlan {

    public static final String KIND = "herobrine_lair";
    public static final ResourceLocation LOOT = new ResourceLocation(Reference.MODID, "chests/herobrine_lair");

    private final LairVariant variant;
    private final int x;
    private final int z;
    private final int ground;
    private final long seed;
    private final int dimension;

    /** @param ground level of the top terrain block at the centre column */
    public HerobrineLairPlan(LairVariant variant, int x, int z, int ground, long seed, int dimension) {
        this.variant = variant == null ? LairVariant.KEEP : variant;
        this.x = x;
        this.z = z;
        this.ground = Math.max(BlockWriter.MIN_Y + this.variant.below, Math.min(BlockWriter.MAX_Y - this.variant.above, ground));
        this.seed = seed;
        this.dimension = dimension;
    }

    public LairVariant variant() {
        return variant;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public StructureBoundingBox footprint() {
        return new StructureBoundingBox(x - variant.half, ground - variant.below, z - variant.half,
                x + variant.half, ground + variant.above, z + variant.half);
    }

    /** @return the netherrack of the altar */
    public BlockPos altarPos() {
        return new BlockPos(x, variant == LairVariant.SHRINE ? ground - LairBuilder.SHRINE_DEPTH + 1 : ground + 1, z);
    }

    /** Builds into the writer and hands the builder back; used by the tests. */
    LairBuilder write(BlockWriter writer, Random random) {
        LairBuilder builder = new LairBuilder(writer, random, x, z, ground);
        builder.build(variant);
        return builder;
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("crypt", random), StructureType.HEROBRINE_LAIR,
                CitadelArchetype.KEEPERS_CITADEL, dimension, altarPos(), footprint());
        LairBuilder builder = write(writer, random);
        final long lootSeed = random.nextLong();
        writer.afterPlace(builder.chest(), (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof TileEntityLockableLoot) {
                ((TileEntityLockableLoot) tile).setLootTable(LOOT, lootSeed);
            }
        });
        writer.afterPlace(builder.altar(), (world, at) -> {
            if (!world.isRemote) {
                HerobrineSavedData.get(world).register(at);
            }
        });
        record.addChestPoint(builder.chest());
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setString("variant", variant.id());
        tag.setInteger("x", x);
        tag.setInteger("z", z);
        tag.setInteger("ground", ground);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        return tag;
    }

    public static HerobrineLairPlan read(NBTTagCompound tag) {
        return new HerobrineLairPlan(LairVariant.byId(tag.getString("variant")), tag.getInteger("x"), tag.getInteger("z"),
                tag.getInteger("ground"), tag.getLong("seed"), tag.getInteger("dim"));
    }

    @Override
    public String describe() {
        return "herobrine lair " + variant.id() + " at " + x + "," + ground + "," + z;
    }
}
