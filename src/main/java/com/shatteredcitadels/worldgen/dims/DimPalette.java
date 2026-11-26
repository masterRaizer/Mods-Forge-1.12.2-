package com.shatteredcitadels.worldgen.dims;

import com.google.common.base.Optional;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.Blueprint;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns the overworld blocks a builder wrote (grass, wood, water, crops, sandstone) into blocks
 * that belong to the dimension. The stone of the walls comes from the dimension's archetype;
 * this covers everything the builders place outside the palette.
 */
public final class DimPalette {

    private DimPalette() {
    }

    /** Remaps a finished (not yet sealed) blueprint; in the Nether terrain clearing stops below the roof. */
    public static void apply(Blueprint blueprint, DimKind kind) {
        Map<Integer, Integer> cache = new HashMap<>();
        int ceiling = kind == DimKind.NETHER ? GroundFinder.NETHER_ROOF + 2 : BlockWriter.MAX_Y;
        blueprint.remap(id -> {
            Integer mapped = cache.get(id);
            if (mapped == null) {
                mapped = mapId(kind, id);
                cache.put(id, mapped);
            }
            return mapped;
        }, ceiling);
    }

    @SuppressWarnings("deprecation")
    private static int mapId(DimKind kind, int id) {
        IBlockState state = Block.getStateById(id);
        IBlockState mapped = remap(kind, state);
        return mapped == state ? id : Block.getStateId(mapped);
    }

    /** @return the state the dimension uses instead, or the same state */
    public static IBlockState remap(DimKind kind, IBlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR) {
            return state;
        }
        switch (kind) {
            case NETHER:
                return nether(state, block);
            case END:
                return end(state, block);
            default:
                return atum(state, block);
        }
    }

    private static IBlockState nether(IBlockState state, Block block) {
        Material material = state.getMaterial();
        if (block instanceof BlockBed || block instanceof BlockFenceGate) {
            // Beds explode here.
            return Blocks.AIR.getDefaultState();
        }
        if (block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.GRASS_PATH || block == Blocks.SAND) {
            return Blocks.NETHERRACK.getDefaultState();
        }
        if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE || block == Blocks.PLANKS
                || block == Blocks.DOUBLE_WOODEN_SLAB) {
            return Blocks.NETHER_BRICK.getDefaultState();
        }
        if (block == Blocks.GRAVEL || block == Blocks.FARMLAND) {
            return Blocks.SOUL_SAND.getDefaultState();
        }
        if (material == Material.WATER) {
            return Blocks.LAVA.getDefaultState();
        }
        if (block == Blocks.WOODEN_SLAB) {
            return copy(state, Blocks.STONE_SLAB.getDefaultState()
                    .withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.NETHERBRICK));
        }
        if (block instanceof BlockStairs && material == Material.WOOD) {
            return copy(state, Blocks.NETHER_BRICK_STAIRS.getDefaultState());
        }
        if (block instanceof BlockFence && material == Material.WOOD) {
            return Blocks.NETHER_BRICK_FENCE.getDefaultState();
        }
        if (block instanceof BlockLog) {
            return Blocks.RED_NETHER_BRICK.getDefaultState();
        }
        if (block == Blocks.HAY_BLOCK || material == Material.LEAVES) {
            return Blocks.NETHER_WART_BLOCK.getDefaultState();
        }
        if (block instanceof BlockCrops) {
            return Blocks.NETHER_WART.getDefaultState().withProperty(BlockNetherWart.AGE, 2);
        }
        if ((material == Material.PLANTS || material == Material.VINE) && block != Blocks.NETHER_WART) {
            return Blocks.AIR.getDefaultState();
        }
        if (block == Blocks.GLASS_PANE || block == Blocks.STAINED_GLASS_PANE) {
            return Blocks.IRON_BARS.getDefaultState();
        }
        return state;
    }

    private static IBlockState end(IBlockState state, Block block) {
        Material material = state.getMaterial();
        if (block instanceof BlockBed || block instanceof BlockFenceGate || material == Material.WATER
                || material == Material.LEAVES || material == Material.PLANTS || material == Material.VINE) {
            return Blocks.AIR.getDefaultState();
        }
        if (block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.GRASS_PATH || block == Blocks.FARMLAND
                || block == Blocks.SAND) {
            return Blocks.END_STONE.getDefaultState();
        }
        if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) {
            return Blocks.END_BRICKS.getDefaultState();
        }
        if (block == Blocks.GRAVEL || block == Blocks.PLANKS || block == Blocks.DOUBLE_WOODEN_SLAB
                || block == Blocks.HAY_BLOCK) {
            return Blocks.PURPUR_BLOCK.getDefaultState();
        }
        if (block == Blocks.WOODEN_SLAB) {
            return copy(state, Blocks.PURPUR_SLAB.getDefaultState());
        }
        if (block instanceof BlockStairs && material == Material.WOOD) {
            return copy(state, Blocks.PURPUR_STAIRS.getDefaultState());
        }
        if (block instanceof BlockFence && material == Material.WOOD) {
            return Blocks.IRON_BARS.getDefaultState();
        }
        if (block instanceof BlockLog) {
            return copy(state, Blocks.PURPUR_PILLAR.getDefaultState());
        }
        if (block == Blocks.TORCH) {
            return copy(state, Blocks.END_ROD.getDefaultState());
        }
        return state;
    }

    private static IBlockState atum(IBlockState state, Block block) {
        if (block == Blocks.GRASS || block == Blocks.GRASS_PATH || block == Blocks.SAND) {
            return mod("atum:sand", Blocks.SAND.getDefaultState());
        }
        if (block == Blocks.DIRT) {
            return mod("atum:limestone", Blocks.SANDSTONE.getDefaultState());
        }
        if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) {
            return mod("atum:limestone_cracked", Blocks.SANDSTONE.getDefaultState());
        }
        if (block == Blocks.SANDSTONE) {
            switch (state.getValue(BlockSandStone.TYPE)) {
                case CHISELED:
                    return mod("atum:limestone_brick_carved", state);
                case SMOOTH:
                    return mod("atum:limestone", state);
                default:
                    return mod("atum:limestone_brick_large", state);
            }
        }
        if (block == Blocks.STONE_SLAB && state.getValue(BlockStoneSlab.VARIANT) == BlockStoneSlab.EnumType.SAND) {
            return copy(state, mod("atum:limestone_smooth_slab", state));
        }
        if (block == Blocks.PLANKS) {
            return mod("atum:palm_planks", state);
        }
        if (block instanceof BlockLog) {
            return copy(state, mod("atum:palm_log", state));
        }
        if (block instanceof BlockFence && state.getMaterial() == Material.WOOD) {
            return mod("atum:palm_fence", state);
        }
        return state;
    }

    /** @return the default state of a mod block by registry name, or the fallback when the mod is absent */
    static IBlockState mod(String id, IBlockState fallback) {
        ResourceLocation name = new ResourceLocation(id);
        if (!Block.REGISTRY.containsKey(name)) {
            return fallback;
        }
        Block block = Block.REGISTRY.getObject(name);
        return block == Blocks.AIR ? fallback : block.getDefaultState();
    }

    /** Copies every property both states share by name (stairs facing, slab half, pillar axis). */
    static IBlockState copy(IBlockState from, IBlockState to) {
        if (from == to) {
            return to;
        }
        IBlockState result = to;
        for (IProperty<?> property : from.getPropertyKeys()) {
            IProperty<?> target = to.getBlock().getBlockState().getProperty(property.getName());
            if (target != null) {
                result = with(result, target, valueName(from, property));
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> String valueName(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> IBlockState with(IBlockState state, IProperty<T> property, String value) {
        Optional<T> parsed = property.parseValue(value);
        return parsed.isPresent() ? state.withProperty(property, parsed.get()) : state;
    }
}
