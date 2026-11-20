package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.worldgen.catacomb.CatacombBuilder;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/** Immutable layout decisions for one citadel (tower), made before any block is written. */
public final class CitadelPlan implements StructurePlan {

    public static final String KIND = "citadel";
    public static final int FLOOR_HEIGHT = 8;
    /** Floor slab, 4 blocks of headroom, ceiling slab. */
    public static final int CRYPT_LEVEL_HEIGHT = 6;
    /** How far wings and their annexes may reach out of the tower, in blocks. */
    public static final int WING_REACH = 24;

    public final int minX;
    public final int minZ;
    /** Square footprint, always odd so the citadel has a centre column. */
    public final int width;
    /** Y of the ground-floor slab. */
    public final int baseY;
    public final int floors;
    /** Interior height of the boss arena on the top floor (12-14). */
    public final int arenaHeight;
    public final boolean sky;
    public final CitadelArchetype archetype;
    /** Highest Y the spire reaches (Citadel Above the Clouds), or roof + battlements. */
    public final int spireTop;
    /** Number of catacomb levels below the citadel, 0 = none (NBT key "crypt" for old saves). */
    public final int cryptLevels;
    public final long buildSeed;
    public final int dimension;
    /** Tower grows wings and annexes on its lower floors. */
    public final boolean wings;
    /** Added to every floor index: 1 for the keep of a walled castle (floor 0 is the courtyard). */
    public final int floorOffset;

    public CitadelPlan(int minX, int minZ, int width, int baseY, int floors, int arenaHeight, boolean sky,
                       CitadelArchetype archetype, int spireTop, int cryptLevels, long buildSeed,
                       int dimension, boolean wings, int floorOffset) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.baseY = baseY;
        this.floors = floors;
        this.arenaHeight = arenaHeight;
        this.sky = sky;
        this.archetype = archetype;
        this.spireTop = spireTop;
        this.cryptLevels = cryptLevels;
        this.buildSeed = buildSeed;
        this.dimension = dimension;
        this.wings = wings;
        this.floorOffset = floorOffset;
    }

    public int maxX() { return minX + width - 1; }
    public int maxZ() { return minZ + width - 1; }
    public int centerX() { return minX + width / 2; }
    public int centerZ() { return minZ + width / 2; }

    /** @return Y of the slab of floor {@code index} (0-based, before {@link #floorOffset}). */
    public int floorY(int index) {
        return baseY + index * FLOOR_HEIGHT;
    }

    public int topFloorIndex() {
        return floors - 1;
    }

    /** @return Y of the roof slab above the boss arena. */
    public int roofY() {
        return floorY(topFloorIndex()) + arenaHeight;
    }

    /** @return Y of the floor of catacomb level {@code level} (0 = shallowest). */
    public int cryptLevelY(int level) {
        return CatacombBuilder.levelY(baseY - 10, level);
    }

    public boolean hasCatacombs() {
        return cryptLevels > 0 && !sky;
    }

    public int lowestY() {
        return hasCatacombs() ? cryptLevelY(cryptLevels - 1) - CatacombBuilder.SANCTUM_DEPTH - 1
                : baseY - 1 - CitadelBuilder.FOUNDATION_MAX_DEPTH;
    }

    /** @return the tower itself (without wings or catacombs): the protected part of the tower. */
    public StructureBoundingBox towerBounds() {
        return new StructureBoundingBox(minX, Math.max(BlockWriter.MIN_Y, baseY - 1 - CitadelBuilder.FOUNDATION_MAX_DEPTH), minZ,
                maxX(), Math.min(BlockWriter.MAX_Y, Math.max(spireTop, roofY() + 3)), maxZ());
    }

    /** @return the tower plus the catacombs under it (without wings). */
    public StructureBoundingBox totalBounds() {
        StructureBoundingBox box = towerBounds();
        if (hasCatacombs()) {
            int r = CatacombBuilder.footprintRadius();
            box = new StructureBoundingBox(Math.min(box.minX, centerX() - r), Math.max(BlockWriter.MIN_Y, lowestY()),
                    Math.min(box.minZ, centerZ() - r), Math.max(box.maxX, centerX() + r), box.maxY,
                    Math.max(box.maxZ, centerZ() + r));
        }
        return box;
    }

    // ------------------------------------------------------------------ StructurePlan

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
        StructureBoundingBox box = totalBounds();
        if (wings) {
            box = new StructureBoundingBox(box.minX - WING_REACH, box.minY, box.minZ - WING_REACH,
                    box.maxX + WING_REACH, box.maxY, box.maxZ + WING_REACH);
        }
        return box;
    }

    @Override
    public CitadelInstance build(BlockWriter writer) {
        Random random = new Random(buildSeed);
        CitadelInstance record = new CitadelInstance(MathHelper.getRandomUUID(random),
                CitadelNameGenerator.generate(archetype.key(), random),
                sky ? StructureType.SKY_CITADEL : StructureType.CITADEL, archetype,
                dimension, new BlockPos(minX, baseY, minZ), totalBounds());
        buildInto(writer, record, random);
        return record;
    }

    /** Builds tower, wings and crypt into an existing record (the keep of a walled castle uses this). */
    void buildInto(BlockWriter writer, CitadelInstance record, Random random) {
        BlockPalette palette = ModPaletteRegistry.forCitadel(archetype, buildSeed);
        new CitadelBuilder(writer, this, palette, random, record).build();
        if (hasCatacombs()) {
            new CatacombBuilder(writer, record, palette, random, 2).withSanctum()
                    .build(centerX(), centerZ(), cryptLevelY(0), cryptLevels, CitadelBuilder.shaftX(this),
                            CitadelBuilder.shaftZ(this), floorY(0));
        }
    }

    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND_KEY, KIND);
        tag.setInteger("minX", minX);
        tag.setInteger("minZ", minZ);
        tag.setInteger("width", width);
        tag.setInteger("baseY", baseY);
        tag.setInteger("floors", floors);
        tag.setInteger("arena", arenaHeight);
        tag.setBoolean("sky", sky);
        tag.setString("archetype", archetype.key());
        tag.setInteger("spire", spireTop);
        tag.setInteger("crypt", cryptLevels);
        tag.setLong("seed", buildSeed);
        tag.setInteger("dim", dimension);
        tag.setBoolean("wings", wings);
        tag.setInteger("offset", floorOffset);
        return tag;
    }

    public static CitadelPlan read(NBTTagCompound tag) {
        CitadelArchetype archetype = CitadelArchetype.byKey(tag.getString("archetype"));
        return new CitadelPlan(tag.getInteger("minX"), tag.getInteger("minZ"), tag.getInteger("width"),
                tag.getInteger("baseY"), tag.getInteger("floors"), tag.getInteger("arena"), tag.getBoolean("sky"),
                archetype == null ? CitadelArchetype.KEEPERS_CITADEL : archetype, tag.getInteger("spire"),
                tag.getInteger("crypt"), tag.getLong("seed"), tag.getInteger("dim"), tag.getBoolean("wings"),
                tag.getInteger("offset"));
    }

    @Override
    public String describe() {
        return archetype.key() + (sky ? " sky" : "") + ", " + floors + " floors" + (wings ? " with wings" : "")
                + (cryptLevels > 0 ? ", crypt " + cryptLevels : "") + " at " + minX + "," + baseY + "," + minZ;
    }
}
