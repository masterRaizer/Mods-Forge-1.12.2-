package com.shatteredcitadels.worldgen.bridge;

import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A bridge over a river crossing. Its record only marks the place (spacing, free-area checks): a bridge
 * has no floors, no gates and no protection.
 */
public final class BridgePlan implements StructurePlan {

    public static final String KIND = "bridge";

    private final BridgeStyle style;
    private final boolean fortified;
    private final boolean alongX;
    private final int start;
    private final int end;
    private final int cross;
    private final int seaLevel;
    private final long seed;
    private final int dimension;

    /** @param fortified ignored for styles without a solid deck: a camp needs something to stand on */
    public BridgePlan(BridgeStyle style, boolean fortified, boolean alongX, int start, int end, int cross, int seaLevel,
                      long seed, int dimension) {
        this.style = style;
        this.fortified = fortified && style.solidDeck;
        this.alongX = alongX;
        this.start = start;
        this.end = end;
        this.cross = cross;
        this.seaLevel = seaLevel;
        this.seed = seed;
        this.dimension = dimension;
    }

    public BridgeStyle style() {
        return style;
    }

    public boolean fortified() {
        return fortified;
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
        return BridgeBuilder.footprint(style, alongX, start, end, cross, seaLevel);
    }

    private BlockPos centre() {
        int middle = (start + end) / 2;
        return alongX ? new BlockPos(middle, seaLevel, cross) : new BlockPos(cross, seaLevel, middle);
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(seed);
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate("crypt", random), StructureType.BRIDGE, CitadelArchetype.KEEPERS_CITADEL,
                dimension, centre(), footprint());
        BridgeBuilder builder = new BridgeBuilder(writer, random, style, alongX, start, end, cross, seaLevel);
        builder.build();
        if (fortified) {
            BridgeCamp camp = new BridgeCamp(writer, random, builder, style, seaLevel);
            camp.build();
            record.addChestPoint(camp.chestPos());
        }
        return record;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setString("style", style.id());
        tag.setBoolean("fortified", fortified);
        tag.setBoolean("alongX", alongX);
        tag.setInteger("start", start);
        tag.setInteger("end", end);
        tag.setInteger("cross", cross);
        tag.setInteger("sea", seaLevel);
        tag.setLong("seed", seed);
        tag.setInteger("dim", dimension);
        return tag;
    }

    public static BridgePlan read(NBTTagCompound tag) {
        BridgeStyle style = BridgeStyle.byId(tag.getString("style"));
        return new BridgePlan(style == null ? BridgeStyle.TIMBER : style, tag.getBoolean("fortified"), tag.getBoolean("alongX"),
                tag.getInteger("start"), tag.getInteger("end"), tag.getInteger("cross"), tag.getInteger("sea"),
                tag.getLong("seed"), tag.getInteger("dim"));
    }

    @Override
    public String describe() {
        BlockPos centre = centre();
        return "bridge " + style.id() + (fortified ? " fortified" : "") + " at " + centre.getX() + "," + centre.getZ();
    }
}
