package com.shatteredcitadels.config;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Thread-safe, validated view of {@link ModConfig}. A new immutable {@link Snapshot} is
 * published on every (re)load, so world-generation threads never observe half-applied values.
 */
public final class ConfigCache {

    /** modid:path, lowercase, as accepted by entity registry names. */
    private static final Pattern ENTITY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern MOD_WILDCARD = Pattern.compile("[a-z0-9_.-]+:\\*");

    /**
     * Floors beyond this no longer raise mob and boss stats, so 20-floor towers stay as hard as the
     * 14-floor Citadel Above the Clouds instead of growing ten-fold.
     */
    public static final int MAX_SCALING_FLOOR = 14;

    /** Upper bound for compounding multipliers, keeps attribute maths finite. */
    private static final double MAX_SCALING = 10_000.0D;

    private static volatile Snapshot current = new Snapshot();
    private static volatile boolean registryPhaseComplete;

    private ConfigCache() {
    }

    /** @return the current validated configuration; never null. */
    public static Snapshot get() {
        return current;
    }

    public static boolean isRegistryPhaseComplete() {
        return registryPhaseComplete;
    }

    /** Parses and validates the raw annotation fields into a fresh snapshot. */
    public static synchronized void rebuild() {
        Snapshot previous = current;
        Snapshot next = new Snapshot();
        ModConfig.CastleGeneration gen = ModConfig.CASTLE_GENERATION;
        ModConfig.SpawnerAndWaves waves = ModConfig.SPAWNER_AND_WAVES;
        ModConfig.BossSettings boss = ModConfig.BOSS_SETTINGS;
        ModConfig.HudAndLoot hud = ModConfig.HUD_AND_LOOT;
        ModConfig.Integration integration = ModConfig.INTEGRATION;

        next.enableCitadelGeneration = gen.enableCitadelGeneration;
        next.maxFoundationDrop = clamp(gen.maxFoundationDrop, 4, 24);

        next.castleSectionSize = clamp(gen.castleSectionSize, 24, 64);
        next.minDistanceBetweenCitadelsChunks = clamp(gen.minDistanceBetweenCitadelsChunks, 8, 1024);
        next.skyCitadelChance = clamp01(gen.skyCitadelChance);
        next.undergroundUnderCastleChance = clamp01(gen.undergroundUnderCastleChance);
        int[] cryptLevels = orderedRange("castle_generation.cryptMinLevels/cryptMaxLevels",
                gen.cryptMinLevels, gen.cryptMaxLevels, 1, 8);
        next.cryptMinLevels = cryptLevels[0];
        next.cryptMaxLevels = cryptLevels[1];
        next.standaloneUndergroundDungeonChance = clamp01(gen.standaloneUndergroundDungeonChance);
        int[] floors = orderedRange("castle_generation.minFloors/maxFloors", gen.minFloors, gen.maxFloors, 2, 30);
        next.minFloors = floors[0];
        next.maxFloors = floors[1];
        next.towerCitadelChance = clamp01(gen.towerCitadelChance);
        next.towerWingChance = clamp01(gen.towerWingChance);
        next.walledCastleChance = clamp01(gen.walledCastleChance);
        next.manorChance = clamp01(gen.manorChance);
        next.catacombTowerChance = clamp01(gen.catacombTowerChance);
        next.catacombCastleChance = clamp01(gen.catacombCastleChance);
        next.catacombManorChance = clamp01(gen.catacombManorChance);
        int[] catacombLevels = orderedRange("castle_generation.catacombMinLevels/catacombMaxLevels",
                gen.catacombMinLevels, gen.catacombMaxLevels, 1, 3);
        next.catacombMinLevels = catacombLevels[0];
        next.catacombMaxLevels = catacombLevels[1];
        next.generationBudgetMs = clamp(gen.generationBudgetMs, 1, 40);
        int[] towerFloors = orderedRange("castle_generation.towerMinFloors/towerMaxFloors",
                gen.towerMinFloors, gen.towerMaxFloors, 2, 30);
        next.towerMinFloors = towerFloors[0];
        next.towerMaxFloors = towerFloors[1];
        int[] skyFloors = orderedRange("castle_generation.skyCitadelMinFloors/skyCitadelMaxFloors",
                gen.skyCitadelMinFloors, gen.skyCitadelMaxFloors, 6, 30);
        next.skyCitadelMinFloors = skyFloors[0];
        next.skyCitadelMaxFloors = skyFloors[1];
        Set<Integer> dims = new LinkedHashSet<>();
        if (gen.dimensionWhitelist != null) {
            for (int dim : gen.dimensionWhitelist) {
                dims.add(dim);
            }
        }
        next.dimensionWhitelist = Collections.unmodifiableSet(dims);

        int[] waveRange = orderedRange("spawner_and_waves.wavesPerFloorMin/wavesPerFloorMax",
                waves.wavesPerFloorMin, waves.wavesPerFloorMax, 1, 10);
        next.wavesPerFloorMin = waveRange[0];
        next.wavesPerFloorMax = waveRange[1];
        next.maxActiveMobsPerFloor = clamp(waves.maxActiveMobsPerFloor, 1, 64);
        next.waveMobHealthMultiplierPerFloor = clamp(waves.waveMobHealthMultiplierPerFloor, 1.0D, 2.0D);
        next.waveMobDamageMultiplierPerFloor = clamp(waves.waveMobDamageMultiplierPerFloor, 1.0D, 2.0D);
        next.waveCooldownSeconds = clamp(waves.waveCooldownSeconds, 1, 60);
        next.stuckMobTimeoutSeconds = clamp(waves.stuckMobTimeoutSeconds, 30, 3600);
        parseBlacklist(waves.mobBlacklist, next);
        next.enableAutoModMobScanner = waves.enableAutoModMobScanner;
        next.lockFloorsUntilCleared = waves.lockFloorsUntilCleared;
        next.unbreakableCitadels = waves.unbreakableCitadels;
        next.vaseMobChance = clamp01(waves.vaseMobChance);
        next.manualMobTierOverrides = parseTierOverrides(waves.manualMobTierOverrides);

        next.bossPoolByTier = parseBossPool(boss.bossPoolByTier);
        next.useBuiltInModBosses = boss.useBuiltInModBosses;
        next.preferModdedBosses = boss.preferModdedBosses;
        next.bossHealthMultiplierPerFloor = clamp(boss.bossHealthMultiplierPerFloor, 1.0D, 3.0D);
        next.bossDamageMultiplierPerFloor = clamp(boss.bossDamageMultiplierPerFloor, 1.0D, 3.0D);
        next.skyCitadelBossMultiplier = clamp(boss.skyCitadelBossMultiplier, 1.0D, 20.0D);
        next.strictArenaAntiGrief = boss.strictArenaAntiGrief;
        next.enableBossCutscene = boss.enableBossCutscene;
        next.bossCutsceneSeconds = clamp(boss.bossCutsceneSeconds, 3, 15);
        next.bossTriggerRadius = clamp(boss.bossTriggerRadius, 2, 16);

        next.enableFloorHud = hud.enableFloorHud;
        next.enableFloorBanner = hud.enableFloorBanner;
        next.bannerOffsetY = clamp(hud.bannerOffsetY, -200, 200);
        next.hudPosition = hud.hudPosition == null ? HudPosition.CENTER_LEFT : hud.hudPosition;
        next.hudOffsetX = clamp(hud.hudOffsetX, 0, 512);
        next.hudOffsetY = clamp(hud.hudOffsetY, 0, 512);
        next.hudScale = clamp(hud.hudScale, 0.5D, 2.0D);
        next.lowFloorRareLootChance = clamp01(hud.lowFloorRareLootChance);

        next.castleDungeonsOverrideSectionSize = integration.castleDungeonsOverrideSectionSize;
        next.castleDungeonsReplaceSpawners = integration.castleDungeonsReplaceSpawners;
        next.castleDungeonsPatchConfigFiles = integration.castleDungeonsPatchConfigFiles;
        next.enableModdedPalettes = integration.enableModdedPalettes;
        List<String> overrides = new ArrayList<>();
        if (integration.paletteOverrides != null) {
            for (String line : integration.paletteOverrides) {
                String value = normalize(line);
                if (!value.isEmpty()) {
                    overrides.add(value);
                }
            }
        }
        next.paletteOverrides = Collections.unmodifiableList(overrides);

        // Registry-resolved views survive a rebuild until resolveRegistryEntries() runs again.
        next.resolvedBossPoolByTier = previous.resolvedBossPoolByTier;
        current = next;
    }

    /**
     * Validates entity IDs against the entity registry. Must run after every mod has
     * registered its entities (FMLLoadCompleteEvent); unknown IDs are dropped with a warning,
     * never with a crash.
     */
    public static synchronized void resolveRegistryEntries() {
        registryPhaseComplete = true;
        Snapshot snapshot = current;
        Map<Integer, List<ResourceLocation>> resolved = new TreeMap<>();
        int skipped = 0;
        Map<Integer, List<ResourceLocation>> pool = snapshot.useBuiltInModBosses
                ? BuiltInModBosses.merge(snapshot.bossPoolByTier, BuiltInModBosses.byTier())
                : snapshot.bossPoolByTier;
        for (Map.Entry<Integer, List<ResourceLocation>> entry : pool.entrySet()) {
            List<ResourceLocation> valid = new ArrayList<>();
            for (ResourceLocation id : entry.getValue()) {
                EntityEntry registered = ForgeRegistries.ENTITIES.getValue(id);
                if (registered == null || snapshot.isBlacklisted(id)) {
                    skipped++;
                    ShatteredCitadelsMod.LOGGER.debug("Boss candidate {} skipped (not registered or blacklisted)", id);
                    continue;
                }
                valid.add(id);
            }
            if (!valid.isEmpty()) {
                resolved.put(entry.getKey(), Collections.unmodifiableList(valid));
            }
        }
        if (resolved.isEmpty()) {
            // Absolute fallback: vanilla mobs always exist.
            resolved.put(MobTiers.MIN_TIER, Collections.singletonList(new ResourceLocation("minecraft", "zombie")));
            ShatteredCitadelsMod.LOGGER.warn("boss_settings.bossPoolByTier resolved to nothing; falling back to minecraft:zombie");
        }
        Snapshot next = snapshot.copy();
        next.resolvedBossPoolByTier = Collections.unmodifiableMap(resolved);
        current = next;
        ShatteredCitadelsMod.LOGGER.info("Boss pool resolved: {} tiers, {} candidates skipped (built-in mod bosses {}, modded first {})",
                resolved.size(), skipped, snapshot.useBuiltInModBosses ? "on" : "off", snapshot.preferModdedBosses ? "on" : "off");
        for (Map.Entry<Integer, List<ResourceLocation>> entry : resolved.entrySet()) {
            ShatteredCitadelsMod.LOGGER.info("  boss tier {}: {}", entry.getKey(), entry.getValue());
        }
    }

    // ------------------------------------------------------------------ parsing

    private static void parseBlacklist(String[] raw, Snapshot target) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        Set<String> mods = new LinkedHashSet<>();
        if (raw != null) {
            for (String line : raw) {
                String value = normalize(line);
                if (value.isEmpty()) {
                    continue;
                }
                if (MOD_WILDCARD.matcher(value).matches()) {
                    mods.add(value.substring(0, value.indexOf(':')));
                } else if (ENTITY_ID.matcher(value).matches()) {
                    ids.add(new ResourceLocation(value));
                } else {
                    warnInvalid("spawner_and_waves.mobBlacklist", line, "expected modid:entity or modid:*");
                }
            }
        }
        target.blacklistedEntities = Collections.unmodifiableSet(ids);
        target.blacklistedMods = Collections.unmodifiableSet(mods);
    }

    private static Map<ResourceLocation, Integer> parseTierOverrides(String[] raw) {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        if (raw == null) {
            return Collections.unmodifiableMap(result);
        }
        for (String line : raw) {
            String value = normalize(line);
            if (value.isEmpty()) {
                continue;
            }
            int eq = value.lastIndexOf('=');
            if (eq <= 0 || eq == value.length() - 1) {
                warnInvalid("spawner_and_waves.manualMobTierOverrides", line, "expected modid:entity=tier");
                continue;
            }
            String id = value.substring(0, eq).trim();
            Integer tier = parseTier(value.substring(eq + 1).trim());
            if (!ENTITY_ID.matcher(id).matches() || tier == null) {
                warnInvalid("spawner_and_waves.manualMobTierOverrides", line,
                        "expected modid:entity=tier with tier " + MobTiers.MIN_TIER + "-" + MobTiers.MAX_TIER);
                continue;
            }
            result.put(new ResourceLocation(id), tier);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, List<ResourceLocation>> parseBossPool(String[] raw) {
        Map<Integer, Set<ResourceLocation>> merged = new TreeMap<>();
        if (raw != null) {
            for (String line : raw) {
                String value = normalize(line);
                if (value.isEmpty()) {
                    continue;
                }
                int eq = value.indexOf('=');
                Integer tier = eq > 0 ? parseTier(value.substring(0, eq).trim()) : null;
                if (tier == null) {
                    warnInvalid("boss_settings.bossPoolByTier", line,
                            "expected tier=modid:entity,... with tier " + MobTiers.MIN_TIER + "-" + MobTiers.MAX_TIER);
                    continue;
                }
                Set<ResourceLocation> bucket = merged.get(tier);
                if (bucket == null) {
                    bucket = new LinkedHashSet<>();
                    merged.put(tier, bucket);
                }
                for (String part : value.substring(eq + 1).split(",")) {
                    String id = part.trim();
                    if (id.isEmpty()) {
                        continue;
                    }
                    if (ENTITY_ID.matcher(id).matches()) {
                        bucket.add(new ResourceLocation(id));
                    } else {
                        warnInvalid("boss_settings.bossPoolByTier", id, "expected modid:entity");
                    }
                }
            }
        }
        Map<Integer, List<ResourceLocation>> result = new TreeMap<>();
        for (Map.Entry<Integer, Set<ResourceLocation>> entry : merged.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Integer parseTier(String text) {
        try {
            int tier = Integer.parseInt(text);
            return tier >= MobTiers.MIN_TIER && tier <= MobTiers.MAX_TIER ? tier : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalize(String line) {
        return line == null ? "" : line.trim().toLowerCase(Locale.ROOT);
    }

    private static void warnInvalid(String key, String value, String expected) {
        ShatteredCitadelsMod.LOGGER.warn("Ignoring invalid {} entry '{}': {}", key, value, expected);
    }

    private static int[] orderedRange(String key, int min, int max, int lower, int upper) {
        int a = clamp(min, lower, upper);
        int b = clamp(max, lower, upper);
        if (a > b) {
            ShatteredCitadelsMod.LOGGER.warn("{}: minimum {} is above maximum {}, swapping", key, a, b);
            return new int[]{b, a};
        }
        return new int[]{a, b};
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return value < min ? min : (value > max ? max : value);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    // ------------------------------------------------------------------ snapshot

    /** Immutable once published. Fields are package-private for the builder above. */
    public static final class Snapshot {

        boolean enableCitadelGeneration = true;
        int maxFoundationDrop = 12;
        int castleSectionSize = 36;
        int minDistanceBetweenCitadelsChunks = 64;
        double skyCitadelChance = 0.06D;
        double undergroundUnderCastleChance = 0.35D;
        int cryptMinLevels = 3;
        int cryptMaxLevels = 5;
        double standaloneUndergroundDungeonChance = 0.04D;
        int minFloors = 3;
        int maxFloors = 7;
        int skyCitadelMinFloors = 10;
        int skyCitadelMaxFloors = 14;
        double towerCitadelChance = 0.30D;
        double towerWingChance = 0.65D;
        double walledCastleChance = 0.35D;
        double manorChance = 0.25D;
        double catacombTowerChance = 0.5D;
        double catacombCastleChance = 0.8D;
        double catacombManorChance = 0.8D;
        int catacombMinLevels = 2;
        int catacombMaxLevels = 3;
        int generationBudgetMs = 5;
        int towerMinFloors = 12;
        int towerMaxFloors = 30;
        Set<Integer> dimensionWhitelist = Collections.singleton(0);

        int wavesPerFloorMin = 2;
        int wavesPerFloorMax = 4;
        int maxActiveMobsPerFloor = 12;
        double waveMobHealthMultiplierPerFloor = 1.08D;
        double waveMobDamageMultiplierPerFloor = 1.05D;
        int waveCooldownSeconds = 5;
        int stuckMobTimeoutSeconds = 240;
        Set<ResourceLocation> blacklistedEntities = Collections.emptySet();
        Set<String> blacklistedMods = Collections.emptySet();
        boolean enableAutoModMobScanner = true;
        boolean lockFloorsUntilCleared = true;
        boolean unbreakableCitadels = true;
        double vaseMobChance = 0.12D;
        Map<ResourceLocation, Integer> manualMobTierOverrides = Collections.emptyMap();

        Map<Integer, List<ResourceLocation>> bossPoolByTier = Collections.emptyMap();
        Map<Integer, List<ResourceLocation>> resolvedBossPoolByTier = Collections.emptyMap();
        boolean useBuiltInModBosses = true;
        boolean preferModdedBosses = true;
        double bossHealthMultiplierPerFloor = 1.15D;
        double bossDamageMultiplierPerFloor = 1.10D;
        double skyCitadelBossMultiplier = 3.0D;
        boolean strictArenaAntiGrief = true;
        boolean enableBossCutscene = true;
        int bossCutsceneSeconds = 6;
        int bossTriggerRadius = 6;

        boolean enableFloorHud = true;
        boolean enableFloorBanner = true;
        int bannerOffsetY = 0;
        HudPosition hudPosition = HudPosition.CENTER_LEFT;
        int hudOffsetX = 4;
        int hudOffsetY = 0;
        double hudScale = 1.0D;
        double lowFloorRareLootChance = 0.025D;

        boolean castleDungeonsOverrideSectionSize = true;
        boolean castleDungeonsReplaceSpawners = true;
        boolean castleDungeonsPatchConfigFiles = true;
        boolean enableModdedPalettes = true;
        List<String> paletteOverrides = Collections.emptyList();

        Snapshot() {
        }

        Snapshot copy() {
            Snapshot c = new Snapshot();
            c.enableCitadelGeneration = enableCitadelGeneration;
            c.maxFoundationDrop = maxFoundationDrop;
            c.castleSectionSize = castleSectionSize;
            c.minDistanceBetweenCitadelsChunks = minDistanceBetweenCitadelsChunks;
            c.skyCitadelChance = skyCitadelChance;
            c.undergroundUnderCastleChance = undergroundUnderCastleChance;
            c.cryptMinLevels = cryptMinLevels;
            c.cryptMaxLevels = cryptMaxLevels;
            c.standaloneUndergroundDungeonChance = standaloneUndergroundDungeonChance;
            c.minFloors = minFloors;
            c.maxFloors = maxFloors;
            c.skyCitadelMinFloors = skyCitadelMinFloors;
            c.skyCitadelMaxFloors = skyCitadelMaxFloors;
            c.towerCitadelChance = towerCitadelChance;
            c.towerWingChance = towerWingChance;
            c.walledCastleChance = walledCastleChance;
            c.manorChance = manorChance;
            c.catacombTowerChance = catacombTowerChance;
            c.catacombCastleChance = catacombCastleChance;
            c.catacombManorChance = catacombManorChance;
            c.catacombMinLevels = catacombMinLevels;
            c.catacombMaxLevels = catacombMaxLevels;
            c.generationBudgetMs = generationBudgetMs;
            c.towerMinFloors = towerMinFloors;
            c.towerMaxFloors = towerMaxFloors;
            c.dimensionWhitelist = dimensionWhitelist;
            c.wavesPerFloorMin = wavesPerFloorMin;
            c.wavesPerFloorMax = wavesPerFloorMax;
            c.maxActiveMobsPerFloor = maxActiveMobsPerFloor;
            c.waveMobHealthMultiplierPerFloor = waveMobHealthMultiplierPerFloor;
            c.waveMobDamageMultiplierPerFloor = waveMobDamageMultiplierPerFloor;
            c.waveCooldownSeconds = waveCooldownSeconds;
            c.stuckMobTimeoutSeconds = stuckMobTimeoutSeconds;
            c.blacklistedEntities = blacklistedEntities;
            c.blacklistedMods = blacklistedMods;
            c.enableAutoModMobScanner = enableAutoModMobScanner;
            c.lockFloorsUntilCleared = lockFloorsUntilCleared;
            c.unbreakableCitadels = unbreakableCitadels;
            c.vaseMobChance = vaseMobChance;
            c.manualMobTierOverrides = manualMobTierOverrides;
            c.bossPoolByTier = bossPoolByTier;
            c.resolvedBossPoolByTier = resolvedBossPoolByTier;
            c.useBuiltInModBosses = useBuiltInModBosses;
            c.preferModdedBosses = preferModdedBosses;
            c.bossHealthMultiplierPerFloor = bossHealthMultiplierPerFloor;
            c.bossDamageMultiplierPerFloor = bossDamageMultiplierPerFloor;
            c.skyCitadelBossMultiplier = skyCitadelBossMultiplier;
            c.strictArenaAntiGrief = strictArenaAntiGrief;
            c.enableBossCutscene = enableBossCutscene;
            c.bossCutsceneSeconds = bossCutsceneSeconds;
            c.bossTriggerRadius = bossTriggerRadius;
            c.enableFloorHud = enableFloorHud;
            c.enableFloorBanner = enableFloorBanner;
            c.bannerOffsetY = bannerOffsetY;
            c.hudPosition = hudPosition;
            c.hudOffsetX = hudOffsetX;
            c.hudOffsetY = hudOffsetY;
            c.hudScale = hudScale;
            c.lowFloorRareLootChance = lowFloorRareLootChance;
            c.castleDungeonsOverrideSectionSize = castleDungeonsOverrideSectionSize;
            c.castleDungeonsReplaceSpawners = castleDungeonsReplaceSpawners;
            c.castleDungeonsPatchConfigFiles = castleDungeonsPatchConfigFiles;
            c.enableModdedPalettes = enableModdedPalettes;
            c.paletteOverrides = paletteOverrides;
            return c;
        }

        // --- castle generation
        public boolean isCitadelGenerationEnabled() { return enableCitadelGeneration; }
        public int getMaxFoundationDrop() { return maxFoundationDrop; }
        public int getCastleSectionSize() { return castleSectionSize; }
        public int getMinDistanceBetweenCitadelsChunks() { return minDistanceBetweenCitadelsChunks; }
        public double getSkyCitadelChance() { return skyCitadelChance; }
        public double getUndergroundUnderCastleChance() { return undergroundUnderCastleChance; }

        /** @return a crypt level count within the configured inclusive range. */
        public int rollCryptLevels(Random random) {
            return cryptMinLevels + random.nextInt(cryptMaxLevels - cryptMinLevels + 1);
        }
        public double getStandaloneUndergroundDungeonChance() { return standaloneUndergroundDungeonChance; }
        public int getMinFloors() { return minFloors; }
        public int getMaxFloors() { return maxFloors; }
        public int getSkyCitadelMinFloors() { return skyCitadelMinFloors; }
        public int getSkyCitadelMaxFloors() { return skyCitadelMaxFloors; }
        public double getTowerCitadelChance() { return towerCitadelChance; }
        public double getTowerWingChance() { return towerWingChance; }
        public double getWalledCastleChance() { return walledCastleChance; }
        public double getManorChance() { return manorChance; }
        public double getCatacombTowerChance() { return catacombTowerChance; }
        public double getCatacombCastleChance() { return catacombCastleChance; }
        public double getCatacombManorChance() { return catacombManorChance; }

        /** @return a catacomb level count within the configured inclusive range. */
        public int rollCatacombLevels(Random random) {
            return catacombMinLevels + random.nextInt(catacombMaxLevels - catacombMinLevels + 1);
        }
        public int getGenerationBudgetMs() { return generationBudgetMs; }
        public int getTowerMinFloors() { return towerMinFloors; }
        public int getTowerMaxFloors() { return towerMaxFloors; }

        /** @return a tower floor count within the configured inclusive range (before the height cap). */
        public int rollTowerFloorCount(Random random) {
            return towerMinFloors + random.nextInt(towerMaxFloors - towerMinFloors + 1);
        }
        public Set<Integer> getDimensionWhitelist() { return dimensionWhitelist; }

        public boolean isDimensionAllowed(int dimension) {
            return dimensionWhitelist.contains(dimension);
        }

        /** @return a floor count within the configured inclusive range. */
        public int rollFloorCount(Random random, boolean skyCitadel) {
            int min = skyCitadel ? skyCitadelMinFloors : minFloors;
            int max = skyCitadel ? skyCitadelMaxFloors : maxFloors;
            return min + random.nextInt(max - min + 1);
        }

        // --- waves
        public int getWavesPerFloorMin() { return wavesPerFloorMin; }
        public int getWavesPerFloorMax() { return wavesPerFloorMax; }
        public int getMaxActiveMobsPerFloor() { return maxActiveMobsPerFloor; }
        public int getWaveCooldownTicks() { return waveCooldownSeconds * 20; }
        public int getStuckMobTimeoutTicks() { return stuckMobTimeoutSeconds * 20; }

        /** @param depth floor index for surface floors, |index| for crypt levels */
        public double waveMobHealthScale(int depth) {
            return Math.min(MAX_SCALING, Math.pow(waveMobHealthMultiplierPerFloor, scalingDepth(depth)));
        }

        public double waveMobDamageScale(int depth) {
            return Math.min(MAX_SCALING, Math.pow(waveMobDamageMultiplierPerFloor, scalingDepth(depth)));
        }
        private int scalingDepth(int depth) {
            return Math.max(0, Math.min(depth, MAX_SCALING_FLOOR));
        }

        public boolean isAutoModMobScannerEnabled() { return enableAutoModMobScanner; }
        public boolean isLockFloorsUntilCleared() { return lockFloorsUntilCleared; }
        public boolean isUnbreakableCitadels() { return unbreakableCitadels; }
        public double getVaseMobChance() { return vaseMobChance; }
        public Map<ResourceLocation, Integer> getManualMobTierOverrides() { return manualMobTierOverrides; }

        public int rollWaveCount(Random random) {
            return wavesPerFloorMin + random.nextInt(wavesPerFloorMax - wavesPerFloorMin + 1);
        }

        public boolean isBlacklisted(ResourceLocation entityId) {
            return entityId != null
                    && (blacklistedEntities.contains(entityId) || blacklistedMods.contains(entityId.getNamespace()));
        }

        // --- bosses
        /** @return tier -> boss IDs parsed from config, not yet checked against the registry. */
        public Map<Integer, List<ResourceLocation>> getConfiguredBossPool() { return bossPoolByTier; }

        /** @return tier -> registered, non-blacklisted boss IDs; empty before load completes. */
        public Map<Integer, List<ResourceLocation>> getResolvedBossPool() { return resolvedBossPoolByTier; }

        /**
         * @return boss candidates for the tier, falling back to the nearest lower tier, then the
         * lowest available tier; empty only before the registry phase completes.
         */
        public List<ResourceLocation> getBossCandidates(int tier) {
            Map<Integer, List<ResourceLocation>> pool = resolvedBossPoolByTier;
            for (int t = Math.min(tier, MobTiers.MAX_TIER); t >= MobTiers.MIN_TIER; t--) {
                List<ResourceLocation> list = pool.get(t);
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            }
            for (List<ResourceLocation> list : pool.values()) {
                if (!list.isEmpty()) {
                    return list;
                }
            }
            return Collections.emptyList();
        }

        public boolean isUseBuiltInModBosses() { return useBuiltInModBosses; }
        public boolean isPreferModdedBosses() { return preferModdedBosses; }
        public double getBossHealthMultiplierPerFloor() { return bossHealthMultiplierPerFloor; }
        public double getBossDamageMultiplierPerFloor() { return bossDamageMultiplierPerFloor; }
        public double getSkyCitadelBossMultiplier() { return skyCitadelBossMultiplier; }
        public boolean isStrictArenaAntiGrief() { return strictArenaAntiGrief; }
        public boolean isBossCutsceneEnabled() { return enableBossCutscene; }
        public int getBossCutsceneTicks() { return bossCutsceneSeconds * 20; }
        public int getBossTriggerRadius() { return bossTriggerRadius; }

        /** @param floor 1-based floor index. */
        public double bossHealthScale(int floor, boolean skyCitadelFinalBoss) {
            return scale(bossHealthMultiplierPerFloor, floor, skyCitadelFinalBoss);
        }

        /** @param floor 1-based floor index. */
        public double bossDamageScale(int floor, boolean skyCitadelFinalBoss) {
            return scale(bossDamageMultiplierPerFloor, floor, skyCitadelFinalBoss);
        }

        private double scale(double perFloor, int floor, boolean skyCitadelFinalBoss) {
            int exponent = Math.max(0, Math.min(floor, MAX_SCALING_FLOOR) - 1);
            double value = Math.pow(perFloor, exponent);
            if (skyCitadelFinalBoss) {
                value *= skyCitadelBossMultiplier;
            }
            return Math.min(value, MAX_SCALING);
        }

        // --- hud & loot
        public boolean isFloorHudEnabled() { return enableFloorHud; }
        public boolean isFloorBannerEnabled() { return enableFloorBanner; }
        public int getBannerOffsetY() { return bannerOffsetY; }
        public HudPosition getHudPosition() { return hudPosition; }
        public int getHudOffsetX() { return hudOffsetX; }
        public int getHudOffsetY() { return hudOffsetY; }
        public double getHudScale() { return hudScale; }
        public double getLowFloorRareLootChance() { return lowFloorRareLootChance; }

        // --- integration
        public boolean isCastleDungeonsSectionOverride() { return castleDungeonsOverrideSectionSize; }
        public boolean isCastleDungeonsSpawnerReplacement() { return castleDungeonsReplaceSpawners; }
        public boolean isCastleDungeonsConfigPatching() { return castleDungeonsPatchConfigFiles; }
        public boolean isModdedPalettesEnabled() { return enableModdedPalettes; }
        public List<String> getPaletteOverrides() { return paletteOverrides; }
    }
}
