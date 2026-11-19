package com.shatteredcitadels.palette;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Resolved, immutable palette of one archetype. Every slot holds at least one state. */
public final class BlockPalette {

    private final CitadelArchetype archetype;
    private final Map<PaletteSlot, List<IBlockState>> slots;
    private final Map<PaletteSlot, String> sources;

    BlockPalette(CitadelArchetype archetype, Map<PaletteSlot, List<IBlockState>> slots,
                 Map<PaletteSlot, String> sources) {
        this.archetype = archetype;
        this.slots = Collections.unmodifiableMap(new EnumMap<>(slots));
        this.sources = Collections.unmodifiableMap(new EnumMap<>(sources));
    }

    public CitadelArchetype getArchetype() {
        return archetype;
    }

    /** @return the primary (first) state of the slot. */
    public IBlockState get(PaletteSlot slot) {
        List<IBlockState> states = slots.get(slot);
        return states == null || states.isEmpty() ? Blocks.STONEBRICK.getDefaultState() : states.get(0);
    }

    /** @return a random variant of the slot, for texture variety. */
    public IBlockState get(PaletteSlot slot, Random random) {
        List<IBlockState> states = slots.get(slot);
        if (states == null || states.isEmpty()) {
            return Blocks.STONEBRICK.getDefaultState();
        }
        return states.size() == 1 ? states.get(0) : states.get(random.nextInt(states.size()));
    }

    /** @return the stairs block facing the given direction (the direction a player walks up). */
    public IBlockState stairs(EnumFacing facing, boolean upsideDown) {
        IBlockState state = get(PaletteSlot.STAIRS);
        if (!(state.getBlock() instanceof BlockStairs)) {
            state = Blocks.STONE_BRICK_STAIRS.getDefaultState();
        }
        EnumFacing horizontal = facing.getAxis().isHorizontal() ? facing : EnumFacing.NORTH;
        return state.withProperty(BlockStairs.FACING, horizontal)
                .withProperty(BlockStairs.HALF, upsideDown ? BlockStairs.EnumHalf.TOP : BlockStairs.EnumHalf.BOTTOM);
    }

    /** @return human-readable origin of each slot, for the palette report. */
    public Map<PaletteSlot, String> getSources() {
        return sources;
    }

    public List<IBlockState> getVariants(PaletteSlot slot) {
        List<IBlockState> states = slots.get(slot);
        return states == null ? Collections.<IBlockState>emptyList() : states;
    }
}
