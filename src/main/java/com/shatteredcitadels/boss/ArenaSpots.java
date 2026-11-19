package com.shatteredcitadels.boss;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * Position validation for everything the boss moves or summons: the full entity box must lie
 * inside the arena interior (one block in from the walls, below the ceiling), stand on a solid
 * floor, and collide with nothing. Nothing is ever placed inside walls or outside the arena.
 */
public final class ArenaSpots {

    private ArenaSpots() {
    }

    /** @return true if {@code entity} placed with its feet at (x, y, z) is fully valid. */
    public static boolean isValid(World world, Entity entity, StructureBoundingBox arena, double x, double y, double z) {
        float halfWidth = entity.width / 2.0F;
        AxisAlignedBB box = new AxisAlignedBB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + entity.height, z + halfWidth);
        if (box.minX < arena.minX + 1 || box.maxX > arena.maxX || box.minZ < arena.minZ + 1 || box.maxZ > arena.maxZ
                || box.minY < arena.minY + 1 || box.maxY > arena.maxY) {
            return false;
        }
        BlockPos feet = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(feet)) {
            return false;
        }
        BlockPos below = feet.down();
        if (!world.getBlockState(below).isSideSolid(world, below, EnumFacing.UP)) {
            return false;
        }
        if (world.containsAnyLiquid(box) || !world.getCollisionBoxes(entity, box).isEmpty()) {
            return false;
        }
        for (int bx = MathHelper.floor(box.minX); bx <= MathHelper.floor(box.maxX - 1.0E-3); bx++) {
            for (int by = MathHelper.floor(box.minY); by <= MathHelper.floor(box.maxY - 1.0E-3); by++) {
                for (int bz = MathHelper.floor(box.minZ); bz <= MathHelper.floor(box.maxZ - 1.0E-3); bz++) {
                    Material material = world.getBlockState(new BlockPos(bx, by, bz)).getMaterial();
                    if (material == Material.FIRE || material == Material.WEB || material == Material.LAVA) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Searches around (cx, cz) on the arena floor level {@code y} for a valid spot.
     *
     * @return {x, y, z} block-centred coordinates, or null
     */
    public static double[] findAround(World world, Entity entity, StructureBoundingBox arena, double cx, int y, double cz,
                                      int radius, Random random, int attempts) {
        for (int i = 0; i < attempts; i++) {
            double x = MathHelper.floor(cx + random.nextInt(radius * 2 + 1) - radius) + 0.5D;
            double z = MathHelper.floor(cz + random.nextInt(radius * 2 + 1) - radius) + 0.5D;
            if (isValid(world, entity, arena, x, y, z)) {
                return new double[]{x, y, z};
            }
        }
        return null;
    }

    public static boolean isInside(StructureBoundingBox arena, Entity entity) {
        return entity.posX >= arena.minX && entity.posX < arena.maxX + 1 && entity.posZ >= arena.minZ
                && entity.posZ < arena.maxZ + 1 && entity.posY >= arena.minY - 1 && entity.posY < arena.maxY + 1;
    }

    public static AxisAlignedBB toBox(StructureBoundingBox arena) {
        return new AxisAlignedBB(arena.minX, arena.minY, arena.minZ, arena.maxX + 1, arena.maxY + 1, arena.maxZ + 1);
    }
}
