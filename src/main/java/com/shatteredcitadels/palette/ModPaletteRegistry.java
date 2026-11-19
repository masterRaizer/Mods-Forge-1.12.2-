package com.shatteredcitadels.palette;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the five archetype palettes against the blocks that are actually registered.
 * Modded blocks are looked up only through {@link ForgeRegistries#BLOCKS}; nothing here
 * references a class of another mod, so a missing mod simply falls through to the next
 * alternative and finally to vanilla 1.12.2 blocks.
 */
public final class ModPaletteRegistry {

    private static final Pattern EXACT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+(@\\d{1,2})?");
    private static final Pattern SCAN = Pattern.compile("([a-z0-9_.-]+):\\*([a-z0-9_]+)\\*");
    /** Variants collected from one registry scan, to keep a coherent look. */
    private static final int MAX_SCAN_VARIANTS = 3;

    private static volatile Map<CitadelArchetype, BlockPalette> palettes = Collections.emptyMap();
    private static volatile boolean initialised;

    private ModPaletteRegistry() {
    }

    /** Builds every palette. Call once all mods registered their blocks (load complete). */
    public static synchronized void init() {
        Map<CitadelArchetype, BlockPalette> built = new EnumMap<>(CitadelArchetype.class);
        for (CitadelArchetype archetype : CitadelArchetype.values()) {
            BlockPalette palette = build(archetype);
            built.put(archetype, palette);
            ShatteredCitadelsMod.LOGGER.info("Palette {}: {}", archetype.key(), palette.getSources());
        }
        palettes = Collections.unmodifiableMap(built);
        initialised = true;
    }

    /** @return the resolved palette; built on demand if {@link #init()} has not run. Never null. */
    public static BlockPalette get(CitadelArchetype archetype) {
        if (!initialised) {
            init();
        }
        BlockPalette palette = palettes.get(archetype);
        return palette != null ? palette : build(archetype);
    }

    /**
     * Looks up {@code modid:block} or {@code modid:block@meta}. Never returns null: unknown
     * names, malformed input and missing mods all yield {@code fallback} (or stone bricks if
     * the fallback itself is null).
     */
    public static IBlockState resolveBlock(String registryName, IBlockState fallback) {
        IBlockState safeFallback = fallback != null ? fallback : Blocks.STONEBRICK.getDefaultState();
        if (registryName == null) {
            return safeFallback;
        }
        IBlockState state = parseExact(registryName.trim().toLowerCase(Locale.ROOT));
        return state != null ? state : safeFallback;
    }

    // ------------------------------------------------------------------ building

    private static BlockPalette build(CitadelArchetype archetype) {
        Map<PaletteSlot, List<IBlockState>> slots = new EnumMap<>(PaletteSlot.class);
        Map<PaletteSlot, String> sources = new EnumMap<>(PaletteSlot.class);
        boolean modded = ConfigCache.get().isModdedPalettesEnabled();
        List<String> overrides = ConfigCache.get().getPaletteOverrides();

        for (PaletteSlot slot : PaletteSlot.values()) {
            List<String> candidates = new ArrayList<>();
            String prefix = archetype.key() + "." + slot.key() + "=";
            for (String line : overrides) {
                if (line.startsWith(prefix)) {
                    candidates.add(line.substring(prefix.length()).trim());
                }
            }
            String[] builtIn = archetype.getAlternatives(slot);
            for (int i = 0; i < builtIn.length; i++) {
                boolean isLast = i == builtIn.length - 1;
                if (modded || isLast || isVanillaOnly(builtIn[i])) {
                    candidates.add(builtIn[i]);
                }
            }

            List<IBlockState> chosen = null;
            String source = null;
            for (String alternative : candidates) {
                List<IBlockState> states = resolveAlternative(alternative, slot);
                if (!states.isEmpty()) {
                    chosen = states;
                    source = alternative;
                    break;
                }
            }
            if (chosen == null) {
                ShatteredCitadelsMod.LOGGER.error("Palette {}.{} has no valid block, using stone bricks",
                        archetype.key(), slot.key());
                chosen = Collections.singletonList(slot == PaletteSlot.STAIRS
                        ? Blocks.STONE_BRICK_STAIRS.getDefaultState()
                        : slot == PaletteSlot.LIGHT ? Blocks.GLOWSTONE.getDefaultState()
                        : Blocks.STONEBRICK.getDefaultState());
                source = "built-in safety fallback";
            }
            slots.put(slot, Collections.unmodifiableList(chosen));
            sources.put(slot, source);
        }
        return new BlockPalette(archetype, slots, sources);
    }

    private static boolean isVanillaOnly(String alternative) {
        for (String variant : alternative.split("\\|")) {
            if (!variant.trim().startsWith("minecraft:")) {
                return false;
            }
        }
        return true;
    }

    /** @return valid states of every variant in the group, in order; empty if none is valid. */
    private static List<IBlockState> resolveAlternative(String alternative, PaletteSlot slot) {
        List<IBlockState> states = new ArrayList<>();
        for (String raw : alternative.split("\\|")) {
            String variant = raw.trim().toLowerCase(Locale.ROOT);
            if (variant.isEmpty()) {
                continue;
            }
            Matcher scan = SCAN.matcher(variant);
            if (scan.matches()) {
                states.addAll(scanRegistry(scan.group(1), scan.group(2), slot));
                continue;
            }
            IBlockState state = parseExact(variant);
            if (state != null && isValidFor(slot, state)) {
                states.add(state);
            }
        }
        return states;
    }

    @SuppressWarnings("deprecation")
    private static IBlockState parseExact(String text) {
        if (!EXACT.matcher(text).matches()) {
            return null;
        }
        String name = text;
        int meta = -1;
        int at = text.indexOf('@');
        if (at > 0) {
            name = text.substring(0, at);
            try {
                meta = Integer.parseInt(text.substring(at + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (meta < 0 || meta > 15) {
                return null;
            }
        }
        ResourceLocation id = new ResourceLocation(name);
        // BLOCKS has air as its default value: containsKey is the only reliable existence check.
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        try {
            return meta < 0 ? block.getDefaultState() : block.getStateFromMeta(meta);
        } catch (RuntimeException e) {
            // Some modded blocks throw for unused metadata values.
            return null;
        }
    }

    private static List<IBlockState> scanRegistry(String modid, String keyword, PaletteSlot slot) {
        List<IBlockState> found = new ArrayList<>();
        List<ResourceLocation> keys = new ArrayList<>();
        for (ResourceLocation key : ForgeRegistries.BLOCKS.getKeys()) {
            if (key.getNamespace().equals(modid) && key.getPath().contains(keyword)) {
                keys.add(key);
            }
        }
        // Registry iteration order depends on registration; sort for deterministic palettes.
        Collections.sort(keys, (a, b) -> a.toString().compareTo(b.toString()));
        for (ResourceLocation key : keys) {
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            if (block == null || block == Blocks.AIR) {
                continue;
            }
            IBlockState state = block.getDefaultState();
            if (isValidFor(slot, state)) {
                found.add(state);
                if (found.size() >= MAX_SCAN_VARIANTS) {
                    break;
                }
            }
        }
        return found;
    }

    /** Rejects blocks that would break, fall, flow or look wrong in the given role. */
    @SuppressWarnings("deprecation")
    static boolean isValidFor(PaletteSlot slot, IBlockState state) {
        Block block = state.getBlock();
        Material material = state.getMaterial();
        if (block == Blocks.AIR || material == Material.AIR || material.isLiquid()
                || block instanceof BlockLiquid || block instanceof IFluidBlock || block instanceof BlockFalling
                || block == Blocks.TNT || block == Blocks.FIRE || block == Blocks.MOB_SPAWNER) {
            return false;
        }
        switch (slot) {
            case WALL:
            case PILLAR:
            case FLOOR:
                return state.isFullCube() && state.isOpaqueCube() && !block.hasTileEntity(state);
            case ACCENT:
                // Glass blocks report isFullCube() == false but fill the whole block space.
                return (state.isFullCube() || material == Material.GLASS) && !block.hasTileEntity(state);
            case STAIRS:
                return block instanceof BlockStairs;
            case LIGHT:
                return state.isFullCube() && state.getLightValue() >= 12 && !block.hasTileEntity(state);
            case FURNITURE:
                return material.isSolid() || material == Material.WOOD || material == Material.IRON;
            default:
                return false;
        }
    }
}
