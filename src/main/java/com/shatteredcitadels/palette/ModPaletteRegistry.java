package com.shatteredcitadels.palette;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the five archetype palettes against the blocks that are actually registered.
 * Modded blocks are looked up only through {@link ForgeRegistries#BLOCKS}; nothing here
 * references a class of another mod, so a missing mod simply drops its alternatives.
 *
 * <p>Every modded alternative of a slot that resolves becomes an <em>option</em>; each citadel
 * picks one option per slot from its own seed ({@link #forCitadel}), so two citadels of the same
 * archetype look different and use the blocks of whatever mods are installed. The vanilla
 * alternative (always last) is used only when no modded alternative of the slot resolves.
 *
 * <p>Variant syntax: {@code modid:block}, {@code modid:block@meta},
 * {@code modid:block[prop=value]}, {@code modid:block[*keyword*]} (every state whose property
 * values contain the keyword) and the registry scan {@code modid:*keyword*}.
 */
public final class ModPaletteRegistry {

    static final Pattern EXACT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+(@\\d{1,2})?");
    static final Pattern PROPERTY = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)\\[([a-z0-9_]+)=([a-z0-9_]+)]");
    static final Pattern PROPERTY_SCAN = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)\\[\\*([a-z0-9_]+)\\*]");
    static final Pattern SCAN = Pattern.compile("([a-z0-9_.-]+):\\*([a-z0-9_]+)\\*");
    /** Variants collected from one scan, to keep a coherent look. */
    private static final int MAX_SCAN_VARIANTS = 3;
    /** Light blocks must at least stop hostile spawns (light 8+); 10 admits glow plastic. */
    private static final int MIN_LIGHT = 10;

    private static volatile Map<CitadelArchetype, Resolved> resolved = Collections.emptyMap();
    private static volatile boolean initialised;
    private static final java.util.Set<String> LOGGED_REJECTIONS = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private ModPaletteRegistry() {
    }

    /** Builds every palette. Call once all mods registered their blocks (load complete). */
    public static synchronized void init() {
        Map<CitadelArchetype, Resolved> built = new EnumMap<>(CitadelArchetype.class);
        int moddedOptions = 0;
        for (CitadelArchetype archetype : CitadelArchetype.values()) {
            Resolved palette = build(archetype, true);
            built.put(archetype, palette);
            moddedOptions += palette.moddedOptionCount();
            ShatteredCitadelsMod.LOGGER.info("Palette {}: {}", archetype.key(), palette.describe());
        }
        resolved = Collections.unmodifiableMap(built);
        initialised = true;
        ShatteredCitadelsMod.LOGGER.info("Palettes resolved: {} modded block options across {} archetypes",
                moddedOptions, built.size());
    }

    /** @return the archetype's default look (first option of every slot). Never null. */
    public static BlockPalette get(CitadelArchetype archetype) {
        return resolved(archetype).pick(null);
    }

    /** @return a palette whose per-slot options are rolled from the citadel's own seed. */
    public static BlockPalette forCitadel(CitadelArchetype archetype, long seed) {
        return resolved(archetype).pick(new Random(seed ^ 0x5DEECE66DL));
    }

    /** @return human-readable options of every slot, for {@code /citadel palettes}. */
    public static String describe(CitadelArchetype archetype) {
        return resolved(archetype).describe();
    }

    private static Resolved resolved(CitadelArchetype archetype) {
        if (!initialised) {
            init();
        }
        Resolved palette = resolved.get(archetype);
        return palette != null ? palette : build(archetype, false);
    }

    private static final Map<String, IBlockState> DECOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final IBlockState NO_DECOR = Blocks.AIR.getDefaultState();

    /**
     * Resolves room decor (furniture, appliances, storage). Each alternative may use any variant
     * syntax of this class; the first one that yields a placeable block wins. Results are cached.
     *
     * @return the block state, or null if no alternative resolves (mod missing)
     */
    public static IBlockState resolveDecor(String... alternatives) {
        for (String alternative : alternatives) {
            IBlockState cached = DECOR_CACHE.get(alternative);
            if (cached == null) {
                List<IBlockState> states = resolveAlternative(alternative, PaletteSlot.FURNITURE, null);
                cached = states.isEmpty() ? NO_DECOR : states.get(0);
                DECOR_CACHE.put(alternative, cached);
            }
            if (cached != NO_DECOR) {
                return cached;
            }
        }
        return null;
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

    private static Resolved build(CitadelArchetype archetype, boolean logRejections) {
        Map<PaletteSlot, List<Option>> slots = new EnumMap<>(PaletteSlot.class);
        boolean modded = ConfigCache.get().isModdedPalettesEnabled();
        List<String> overrides = ConfigCache.get().getPaletteOverrides();

        for (PaletteSlot slot : PaletteSlot.values()) {
            List<String> overrideLines = new ArrayList<>();
            String prefix = archetype.key() + "." + slot.key() + "=";
            for (String line : overrides) {
                if (line.startsWith(prefix)) {
                    overrideLines.add(line.substring(prefix.length()).trim());
                }
            }
            List<Option> options = new ArrayList<>();
            // Config overrides win outright: the first one that resolves is the only option.
            for (String alternative : overrideLines) {
                List<IBlockState> states = resolveAlternative(alternative, slot, logRejections ? archetype : null);
                if (!states.isEmpty()) {
                    options.add(new Option(alternative, states, false));
                    break;
                }
            }
            if (options.isEmpty()) {
                String[] builtIn = archetype.getAlternatives(slot);
                List<Option> vanilla = new ArrayList<>();
                for (String alternative : builtIn) {
                    boolean vanillaOnly = isVanillaOnly(alternative);
                    if (!modded && !vanillaOnly) {
                        continue;
                    }
                    List<IBlockState> states = resolveAlternative(alternative, slot,
                            logRejections && !vanillaOnly ? archetype : null);
                    if (states.isEmpty()) {
                        continue;
                    }
                    // A mixed group whose modded variants all failed is a vanilla option in disguise.
                    boolean hasModdedBlock = containsModdedBlock(states);
                    (hasModdedBlock ? options : vanilla).add(new Option(alternative, states, hasModdedBlock));
                }
                if (options.isEmpty() && !vanilla.isEmpty()) {
                    options.add(vanilla.get(vanilla.size() - 1));
                }
            }
            if (options.isEmpty()) {
                ShatteredCitadelsMod.LOGGER.error("Palette {}.{} has no valid block, using stone bricks",
                        archetype.key(), slot.key());
                IBlockState safety = slot == PaletteSlot.STAIRS ? Blocks.STONE_BRICK_STAIRS.getDefaultState()
                        : slot == PaletteSlot.LIGHT ? Blocks.GLOWSTONE.getDefaultState()
                        : Blocks.STONEBRICK.getDefaultState();
                options.add(new Option("built-in safety fallback", Collections.singletonList(safety), false));
            }
            slots.put(slot, Collections.unmodifiableList(options));
        }
        return new Resolved(archetype, slots);
    }

    private static boolean containsModdedBlock(List<IBlockState> states) {
        for (IBlockState state : states) {
            ResourceLocation id = state.getBlock().getRegistryName();
            if (id != null && !"minecraft".equals(id.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    static boolean isVanillaOnly(String alternative) {
        for (String variant : alternative.split("\\|")) {
            if (!variant.trim().startsWith("minecraft:")) {
                return false;
            }
        }
        return true;
    }

    /** @return valid states of every variant in the group, in order; empty if none is valid. */
    private static List<IBlockState> resolveAlternative(String alternative, PaletteSlot slot, CitadelArchetype logFor) {
        List<IBlockState> states = new ArrayList<>();
        for (String raw : alternative.split("\\|")) {
            String variant = raw.trim().toLowerCase(Locale.ROOT);
            if (variant.isEmpty()) {
                continue;
            }
            Matcher scan = SCAN.matcher(variant);
            if (scan.matches()) {
                List<IBlockState> found = scanRegistry(scan.group(1), scan.group(2), slot);
                if (found.isEmpty()) {
                    reject(logFor, slot, variant, "no registered block matches");
                }
                states.addAll(found);
                continue;
            }
            Matcher property = PROPERTY.matcher(variant);
            Matcher propertyScan = PROPERTY_SCAN.matcher(variant);
            List<IBlockState> candidates;
            if (property.matches()) {
                candidates = statesWithProperty(property.group(1), property.group(2), property.group(3));
            } else if (propertyScan.matches()) {
                candidates = statesWithValueContaining(propertyScan.group(1), propertyScan.group(2));
            } else {
                IBlockState exact = parseExact(variant);
                candidates = exact == null ? Collections.<IBlockState>emptyList() : Collections.singletonList(exact);
            }
            if (candidates.isEmpty()) {
                reject(logFor, slot, variant, "not registered (disabled in the mod's config or wrong name)");
                continue;
            }
            boolean any = false;
            for (IBlockState state : candidates) {
                if (isValidFor(slot, state)) {
                    states.add(state);
                    any = true;
                    if (candidates.size() > 1 && states.size() >= MAX_SCAN_VARIANTS) {
                        break;
                    }
                }
            }
            if (!any) {
                reject(logFor, slot, variant, "unsuitable for slot: " + describeShape(candidates.get(0)));
            }
        }
        return states;
    }

    /**
     * Logs a skipped variant only when its mod is installed: then the name is wrong, the block is
     * disabled in that mod's config or unsuitable, which is worth knowing. Missing mods stay quiet.
     */
    private static void reject(CitadelArchetype archetype, PaletteSlot slot, String variant, String reason) {
        int colon = variant.indexOf(':');
        if (archetype == null || colon <= 0 || !Loader.isModLoaded(variant.substring(0, colon))) {
            return;
        }
        if (LOGGED_REJECTIONS.add(archetype.key() + "." + slot.key() + "=" + variant)) {
            ShatteredCitadelsMod.LOGGER.info("Palette {}.{}: skipped {} - {}", archetype.key(), slot.key(), variant, reason);
        }
    }

    @SuppressWarnings("deprecation")
    private static String describeShape(IBlockState state) {
        Block block = state.getBlock();
        return "fullCube=" + state.isFullCube() + ", opaque=" + state.isOpaqueCube()
                + ", tileEntity=" + block.hasTileEntity(state) + ", light=" + state.getLightValue()
                + ", material=" + (state.getMaterial() == Material.GLASS ? "glass" : state.getMaterial().isSolid() ? "solid" : "other");
    }

    private static Block registered(String name) {
        ResourceLocation id = new ResourceLocation(name);
        // BLOCKS has air as its default value: containsKey is the only reliable existence check.
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null || block == Blocks.AIR ? null : block;
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
        Block block = registered(name);
        if (block == null) {
            return null;
        }
        try {
            return meta < 0 ? block.getDefaultState() : block.getStateFromMeta(meta);
        } catch (RuntimeException e) {
            // Some modded blocks throw for unused metadata values.
            return null;
        }
    }

    /** @return the state of {@code name} whose property {@code key} has the value {@code value}. */
    private static List<IBlockState> statesWithProperty(String name, String key, String value) {
        Block block = registered(name);
        if (block == null) {
            return Collections.emptyList();
        }
        for (IBlockState state : block.getBlockState().getValidStates()) {
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                if (entry.getKey().getName().equals(key) && value.equals(valueName(entry.getKey(), entry.getValue()))) {
                    return Collections.singletonList(withDefaultsFor(block, key, state));
                }
            }
        }
        return Collections.emptyList();
    }

    /** @return distinct states of {@code name} that have a property value containing {@code keyword}. */
    private static List<IBlockState> statesWithValueContaining(String name, String keyword) {
        Block block = registered(name);
        if (block == null) {
            return Collections.emptyList();
        }
        Map<String, IBlockState> found = new LinkedHashMap<>();
        for (IBlockState state : block.getBlockState().getValidStates()) {
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                String value = valueName(entry.getKey(), entry.getValue());
                if (value.contains(keyword) && !found.containsKey(entry.getKey().getName() + "=" + value)) {
                    found.put(entry.getKey().getName() + "=" + value, withDefaultsFor(block, entry.getKey().getName(), state));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Keeps only the selected property from {@code state}; every other property stays at its default. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState withDefaultsFor(Block block, String key, IBlockState state) {
        IBlockState result = block.getDefaultState();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            if (entry.getKey().getName().equals(key)) {
                result = result.withProperty((IProperty) entry.getKey(), (Comparable) entry.getValue());
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(IProperty property, Comparable value) {
        return property.getName(value).toLowerCase(Locale.ROOT);
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
                // Some modded masonry (connected-texture blocks) reports isOpaqueCube() == false;
                // a solid full cube without a tile entity is good enough for structure blocks.
                return state.isFullCube() && material.isSolid() && material != Material.GLASS && !block.hasTileEntity(state);
            case ACCENT:
                // Glass blocks report isFullCube() == false but fill the whole block space.
                return (state.isFullCube() || material == Material.GLASS) && !block.hasTileEntity(state);
            case STAIRS:
                return block instanceof BlockStairs;
            case LIGHT:
                return (state.isFullCube() || material == Material.GLASS) && state.getLightValue() >= MIN_LIGHT
                        && !block.hasTileEntity(state);
            case FURNITURE:
                return material.isSolid() || material == Material.WOOD || material == Material.IRON;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ resolved data

    /** One resolved alternative: a coherent group of block variants for a slot. */
    private static final class Option {
        final String source;
        final List<IBlockState> states;
        final boolean modded;

        Option(String source, List<IBlockState> states, boolean modded) {
            this.source = source;
            this.states = Collections.unmodifiableList(new ArrayList<>(states));
            this.modded = modded;
        }
    }

    /** Every option of every slot of one archetype. */
    private static final class Resolved {
        final CitadelArchetype archetype;
        final Map<PaletteSlot, List<Option>> slots;

        Resolved(CitadelArchetype archetype, Map<PaletteSlot, List<Option>> slots) {
            this.archetype = archetype;
            this.slots = slots;
        }

        BlockPalette pick(Random random) {
            Map<PaletteSlot, List<IBlockState>> states = new EnumMap<>(PaletteSlot.class);
            Map<PaletteSlot, String> sources = new EnumMap<>(PaletteSlot.class);
            for (PaletteSlot slot : PaletteSlot.values()) {
                List<Option> options = slots.get(slot);
                Option option = random == null || options.size() == 1 ? options.get(0)
                        : options.get(random.nextInt(options.size()));
                states.put(slot, option.states);
                sources.put(slot, option.source);
            }
            return new BlockPalette(archetype, states, sources);
        }

        int moddedOptionCount() {
            int count = 0;
            for (List<Option> options : slots.values()) {
                for (Option option : options) {
                    if (option.modded) {
                        count++;
                    }
                }
            }
            return count;
        }

        String describe() {
            StringBuilder out = new StringBuilder();
            for (PaletteSlot slot : PaletteSlot.values()) {
                List<Option> options = slots.get(slot);
                if (out.length() > 0) {
                    out.append("; ");
                }
                out.append(slot.key()).append('[').append(options.size()).append("]=");
                for (int i = 0; i < options.size(); i++) {
                    out.append(i == 0 ? "" : " / ").append(options.get(i).source);
                }
            }
            return out.toString();
        }
    }
}
