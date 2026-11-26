package com.shatteredcitadels.config;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Forge annotation config, saved to {@code config/shatteredcitadels/shatteredcitadels.cfg}.
 * Each public static object is a top-level category ({@code category = ""}).
 *
 * <p>Gameplay code must read values through {@link ConfigCache#get()}, which holds a
 * validated, immutable snapshot (min/max pairs ordered, IDs parsed). Raw fields here are
 * only the editable storage.
 */
@Config(modid = Reference.MODID, name = Reference.MODID + "/" + Reference.MODID, category = "")
@Config.LangKey("config.shatteredcitadels.title")
public final class ModConfig {


    @Config.Name("castle_generation")
    @Config.LangKey("config.shatteredcitadels.castle_generation")
    @Config.Comment("Citadel placement, rarity and floor counts.")
    public static final CastleGeneration CASTLE_GENERATION = new CastleGeneration();

    @Config.Name("spawner_and_waves")
    @Config.LangKey("config.shatteredcitadels.spawner_and_waves")
    @Config.Comment("Floor waves, entity cap and the mob pool built from installed mods.")
    public static final SpawnerAndWaves SPAWNER_AND_WAVES = new SpawnerAndWaves();

    @Config.Name("boss_settings")
    @Config.LangKey("config.shatteredcitadels.boss_settings")
    @Config.Comment("Floor bosses, their scaling and arena protection.")
    public static final BossSettings BOSS_SETTINGS = new BossSettings();

    @Config.Name("hud_and_loot")
    @Config.LangKey("config.shatteredcitadels.hud_and_loot")
    @Config.Comment("Floor HUD placement (client) and loot rarity (server).")
    public static final HudAndLoot HUD_AND_LOOT = new HudAndLoot();

    @Config.Name("integration")
    @Config.LangKey("config.shatteredcitadels.integration")
    @Config.Comment("Castle Dungeons patching and block palettes taken from installed mods.")
    public static final Integration INTEGRATION = new Integration();

    @Config.Name("bridges")
    @Config.LangKey("config.shatteredcitadels.bridges")
    @Config.Comment("Bridges over rivers and the bandit camps on some of them.")
    public static final Bridges BRIDGES = new Bridges();

    @Config.Name("witchHuts")
    @Config.LangKey("config.shatteredcitadels.witchHuts")
    @Config.Comment("Witch huts on swamps (instead of the vanilla swamp hut) and the cellar lair under them.")
    public static final WitchHuts WITCH_HUTS = new WitchHuts();
    @Config.Name("herobrine")
    @Config.LangKey("config.shatteredcitadels.herobrine")
    @Config.Comment("Herobrine: his lairs, the altar, the fight and the apparitions.")
    public static final Herobrine HEROBRINE = new Herobrine();
    @Config.Name("dimensions")
    @Config.LangKey("config.shatteredcitadels.dimensions")
    @Config.Comment("Structures in the Nether, the End and Atum, and the mod's own Nether fortress.")
    public static final Dimensions DIMENSIONS = new Dimensions();

    private ModConfig() {
    }

    public static final class CastleGeneration {

        @Config.Name("enableCitadelGeneration")
        @Config.LangKey("config.shatteredcitadels.castle_generation.enableCitadelGeneration")
        @Config.Comment("Generate citadels, the Citadel Above the Clouds and underground crypts in new chunks.")
        public boolean enableCitadelGeneration = true;

        @Config.Name("maxFoundationDrop")
        @Config.LangKey("config.shatteredcitadels.castle_generation.maxFoundationDrop")
        @Config.Comment("Maximum height difference of the terrain under a citadel; steeper sites are rejected.")
        @Config.RangeInt(min = 4, max = 24)
        public int maxFoundationDrop = 12;

        @Config.Name("castleSectionSize")
        @Config.LangKey("config.shatteredcitadels.castle_generation.castleSectionSize")
        @Config.Comment({
                "Size of a placement section in chunks: at most one citadel attempt per section.",
                "Castle Dungeons uses 24; 32-40 makes citadels rare landmarks."
        })
        @Config.RangeInt(min = 24, max = 64)
        @Config.RequiresWorldRestart
        public int castleSectionSize = 36;

        @Config.Name("minDistanceBetweenCitadelsChunks")
        @Config.LangKey("config.shatteredcitadels.castle_generation.minDistanceBetweenCitadelsChunks")
        @Config.Comment("Minimum distance in chunks between any two citadels (checked on top of the section grid).")
        @Config.RangeInt(min = 8, max = 1024)
        @Config.RequiresWorldRestart
        public int minDistanceBetweenCitadelsChunks = 64;

        @Config.Name("skyCitadelChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.skyCitadelChance")
        @Config.Comment("Chance that a generated citadel is the 'Citadel Above the Clouds' (0.06 = 6%).")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double skyCitadelChance = 0.06D;

        @Config.Name("replaceVanillaDungeons")
        @Config.LangKey("config.shatteredcitadels.castle_generation.replaceVanillaDungeons")
        @Config.Comment("Replace the vanilla cobblestone spawner dungeons with buried catacomb fragments (Overworld).")
        public boolean replaceVanillaDungeons = true;

        @Config.Name("fragmentRegionChunks")
        @Config.LangKey("config.shatteredcitadels.castle_generation.fragmentRegionChunks")
        @Config.Comment("Side of the square region (in chunks) that holds at most one buried catacomb fragment.")
        @Config.RangeInt(min = 4, max = 64)
        public int fragmentRegionChunks = 10;

        @Config.Name("fragmentChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.fragmentChance")
        @Config.Comment("Chance that a region gets its buried catacomb fragment.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double fragmentChance = 0.6D;

        @Config.Name("replaceStrongholds")
        @Config.LangKey("config.shatteredcitadels.castle_generation.replaceStrongholds")
        @Config.Comment("Replace the vanilla End stronghold with a large explorative one; the eye of ender leads to it.")
        public boolean replaceStrongholds = true;

        @Config.Name("replaceDesertTemples")
        @Config.LangKey("config.shatteredcitadels.castle_generation.replaceDesertTemples")
        @Config.Comment("Replace the vanilla desert pyramid with a pyramid over an underground temple ending in a tomb.")
        public boolean replaceDesertTemples = true;

        @Config.Name("desertTempleBossChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.desertTempleBossChance")
        @Config.Comment("Chance that a desert temple's tomb is a pharaoh's tomb with a boss (Atum's pharaoh, or a husk).")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double desertTempleBossChance = 0.4D;

        @Config.Name("desertTempleSpawnerMobs")
        @Config.LangKey("config.shatteredcitadels.castle_generation.desertTempleSpawnerMobs")
        @Config.Comment("Mobs of the desert temple's spawners.")
        public String[] desertTempleSpawnerMobs = {"minecraft:husk", "minecraft:skeleton", "minecraft:spider"};

        @Config.Name("replaceJungleTemples")
        @Config.LangKey("config.shatteredcitadels.castle_generation.replaceJungleTemples")
        @Config.Comment("Replace the jungle temple with a stepped overgrown temple over an underground: an idol vault, or Barako's shrine on top.")
        public boolean replaceJungleTemples = true;

        @Config.Name("jungleTempleBossChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.jungleTempleBossChance")
        @Config.Comment("Chance that a jungle temple ends in a boss shrine on top (Mowzie's Barako, or a vindicator) instead of the idol vault.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double jungleTempleBossChance = 0.4D;

        @Config.Name("jungleTempleSpawnerMobs")
        @Config.LangKey("config.shatteredcitadels.castle_generation.jungleTempleSpawnerMobs")
        @Config.Comment("Mobs of the jungle temple's spawners.")
        public String[] jungleTempleSpawnerMobs = {"minecraft:zombie", "minecraft:skeleton", "minecraft:cave_spider"};

        @Config.Name("strongholdSpawnerMobs")
        @Config.LangKey("config.shatteredcitadels.castle_generation.strongholdSpawnerMobs")
        @Config.Comment("Mobs of the stronghold's spawners (the portal room always has silverfish).")
        public String[] strongholdSpawnerMobs = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
                "minecraft:cave_spider", "minecraft:silverfish"};

        @Config.Name("standaloneUndergroundDungeonChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.standaloneUndergroundDungeonChance")
        @Config.Comment("Chance per placement section (without a citadel) to generate a standalone underground dungeon.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double standaloneUndergroundDungeonChance = 0.04D;

        @Config.Name("minFloors")
        @Config.LangKey("config.shatteredcitadels.castle_generation.minFloors")
        @Config.Comment("Minimum floors of a regular citadel.")
        @Config.RangeInt(min = 2, max = 30)
        public int minFloors = 3;

        @Config.Name("maxFloors")
        @Config.LangKey("config.shatteredcitadels.castle_generation.maxFloors")
        @Config.Comment("Maximum floors of a regular citadel. Citadels are shortened automatically to stay below Y 250.")
        @Config.RangeInt(min = 2, max = 30)
        public int maxFloors = 9;

        @Config.Name("towerCitadelChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.towerCitadelChance")
        @Config.Comment({
                "Chance that a regular citadel becomes a tower citadel that rises towards the build limit",
                "(towerMinFloors..towerMaxFloors floors, 8 blocks each, roof below Y 250). 0 disables towers."
        })
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double towerCitadelChance = 0.30D;

        @Config.Name("towerWingChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.towerWingChance")
        @Config.Comment("Chance per floor band (every 2-3 floors) that a tower grows a wing with annexes.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double towerWingChance = 0.65D;

        @Config.Name("walledCastleChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.walledCastleChance")
        @Config.Comment({
                "Chance that a citadel site becomes a walled castle: curtain wall with towers, gatehouse, moat,",
                "a courtyard that must be taken first (its waves seal the keep) and the keep in the middle."
        })
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double walledCastleChance = 0.35D;

        @Config.Name("manorChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.manorChance")
        @Config.Comment({
                "Chance that a citadel site becomes a gothic manor (~110 x 140 blocks): zones cleared in order",
                "(forecourt, great hall, library, servants' wing, garden, guest wing, gallery, throne room)."
        })
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double manorChance = 0.25D;

        @Config.Name("catacombTowerChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.catacombTowerChance")
        @Config.Comment("Chance that a tower has catacombs below it (spiral stair, 2-3 wide levels, the guardian mage's sanctum).")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double catacombTowerChance = 0.5D;

        @Config.Name("catacombCastleChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.catacombCastleChance")
        @Config.Comment("Chance that the keep of a walled castle has catacombs below it.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double catacombCastleChance = 0.8D;

        @Config.Name("catacombManorChance")
        @Config.LangKey("config.shatteredcitadels.castle_generation.catacombManorChance")
        @Config.Comment("Chance that a gothic manor has catacombs below its cellar.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double catacombManorChance = 0.8D;

        @Config.Name("catacombMinLevels")
        @Config.LangKey("config.shatteredcitadels.castle_generation.catacombMinLevels")
        @Config.Comment("Minimum catacomb levels (about 95 x 95 blocks each, 10 blocks apart).")
        @Config.RangeInt(min = 1, max = 3)
        public int catacombMinLevels = 2;

        @Config.Name("catacombMaxLevels")
        @Config.LangKey("config.shatteredcitadels.castle_generation.catacombMaxLevels")
        @Config.Comment("Maximum catacomb levels; fewer are built when the ground is too shallow.")
        @Config.RangeInt(min = 1, max = 3)
        public int catacombMaxLevels = 3;

        @Config.Name("generationBudgetMs")
        @Config.LangKey("config.shatteredcitadels.castle_generation.generationBudgetMs")
        @Config.Comment({
                "Milliseconds per server tick spent writing structures into the world. Structures are planned",
                "in the background and written gradually into loaded chunks, so they never freeze the game."
        })
        @Config.RangeInt(min = 1, max = 40)
        public int generationBudgetMs = 5;

        @Config.Name("towerMinFloors")
        @Config.LangKey("config.shatteredcitadels.castle_generation.towerMinFloors")
        @Config.Comment("Minimum floors of a tower citadel.")
        @Config.RangeInt(min = 2, max = 30)
        public int towerMinFloors = 12;

        @Config.Name("towerMaxFloors")
        @Config.LangKey("config.shatteredcitadels.castle_generation.towerMaxFloors")
        @Config.Comment("Maximum floors of a tower citadel; the default 30 means 'as high as the world allows' (about 20-22 floors from sea level).")
        @Config.RangeInt(min = 2, max = 30)
        public int towerMaxFloors = 30;

        @Config.Name("skyCitadelMinFloors")
        @Config.LangKey("config.shatteredcitadels.castle_generation.skyCitadelMinFloors")
        @Config.Comment("Minimum floors of the Citadel Above the Clouds.")
        @Config.RangeInt(min = 6, max = 30)
        public int skyCitadelMinFloors = 10;

        @Config.Name("skyCitadelMaxFloors")
        @Config.LangKey("config.shatteredcitadels.castle_generation.skyCitadelMaxFloors")
        @Config.Comment("Maximum floors of the Citadel Above the Clouds.")
        @Config.RangeInt(min = 6, max = 30)
        public int skyCitadelMaxFloors = 14;

        @Config.Name("dimensionWhitelist")
        @Config.LangKey("config.shatteredcitadels.castle_generation.dimensionWhitelist")
        @Config.Comment("Dimension IDs where citadels may generate.")
        @Config.RequiresWorldRestart
        public int[] dimensionWhitelist = {0};
    }

    public static final class SpawnerAndWaves {

        @Config.Name("wavesPerFloorMin")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.wavesPerFloorMin")
        @Config.Comment("Minimum mob waves per floor.")
        @Config.RangeInt(min = 1, max = 10)
        public int wavesPerFloorMin = 2;

        @Config.Name("wavesPerFloorMax")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.wavesPerFloorMax")
        @Config.Comment("Maximum mob waves per floor.")
        @Config.RangeInt(min = 1, max = 10)
        public int wavesPerFloorMax = 4;

        @Config.Name("maxActiveMobsPerFloor")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.maxActiveMobsPerFloor")
        @Config.Comment("Hard entity cap: a floor never has more living wave mobs than this at once.")
        @Config.RangeInt(min = 1, max = 64)
        public int maxActiveMobsPerFloor = 12;

        @Config.Name("waveMobHealthMultiplierPerFloor")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.waveMobHealthMultiplierPerFloor")
        @Config.Comment("Compounding health multiplier for wave mobs per floor (crypt levels count by depth).")
        @Config.RangeDouble(min = 1.0D, max = 2.0D)
        public double waveMobHealthMultiplierPerFloor = 1.08D;

        @Config.Name("waveMobDamageMultiplierPerFloor")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.waveMobDamageMultiplierPerFloor")
        @Config.Comment("Compounding attack damage multiplier for wave mobs per floor.")
        @Config.RangeDouble(min = 1.0D, max = 2.0D)
        public double waveMobDamageMultiplierPerFloor = 1.05D;

        @Config.Name("waveCooldownSeconds")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.waveCooldownSeconds")
        @Config.Comment("Pause between two waves of the same spawner.")
        @Config.RangeInt(min = 1, max = 60)
        public int waveCooldownSeconds = 5;

        @Config.Name("stuckMobTimeoutSeconds")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.stuckMobTimeoutSeconds")
        @Config.Comment({
                "Anti-stall: a wave mob that has been unloaded/missing, outside its floor, or alive for longer",
                "than this is dropped from the wave (and removed if still present), so waves never hang."
        })
        @Config.RangeInt(min = 30, max = 3600)
        public int stuckMobTimeoutSeconds = 240;

        @Config.Name("mobBlacklist")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.mobBlacklist")
        @Config.Comment({
                "Entities that are never used in waves or as bosses.",
                "Format: modid:entity, or modid:* to exclude a whole mod.",
                "Defaults remove block-breaking, flying-ranged, teleporting and world-boss mobs."
        })
        public String[] mobBlacklist = {
                "minecraft:creeper",
                "minecraft:witch",
                "minecraft:ghast",
                "minecraft:wither",
                "minecraft:ender_dragon",
                "minecraft:enderman",
                "minecraft:silverfish",
                "minecraft:endermite",
                "minecraft:shulker",
                "minecraft:giant",
                "minecraft:illusion_illager",
                "minecraft:vex"
        };

        @Config.Name("enableAutoModMobScanner")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.enableAutoModMobScanner")
        @Config.Comment("Scan hostile mobs of installed mods and assign tiers from their health, damage, armor and XP.")
        public boolean enableAutoModMobScanner = true;

        @Config.Name("lockFloorsUntilCleared")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.lockFloorsUntilCleared")
        @Config.Comment({
                "Seal the stairwell to the next floor (and the ladder to the next crypt level) with an unbreakable",
                "gate until every wave of the current floor is defeated. Applies to newly generated citadels."
        })
        public boolean lockFloorsUntilCleared = true;

        @Config.Name("unbreakableCitadels")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.unbreakableCitadels")
        @Config.Comment({
                "Citadel walls, floors and ceilings cannot be broken by players, explosions or mobs until the",
                "citadel's boss is defeated. Chests, furniture, vases and cobwebs stay breakable; creative ignores it."
        })
        public boolean unbreakableCitadels = true;

        @Config.Name("vaseMobChance")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.vaseMobChance")
        @Config.Comment("Chance that a smashed vase releases a mob of the floor's tier.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double vaseMobChance = 0.12D;

        @Config.Name("manualMobTierOverrides")
        @Config.LangKey("config.shatteredcitadels.spawner_and_waves.manualMobTierOverrides")
        @Config.Comment({
                "Manual tiers that override the scanner. Format: modid:entity=tier (tier 1-5).",
                "Unknown entities are ignored, so IDs of mods that are not installed are safe."
        })
        public String[] manualMobTierOverrides = {
                "minecraft:zombie=1",
                "minecraft:skeleton=1",
                "minecraft:spider=1",
                "minecraft:husk=2",
                "minecraft:stray=2",
                "minecraft:cave_spider=2",
                "minecraft:zombie_villager=2",
                "minecraft:wither_skeleton=3",
                "minecraft:blaze=3",
                "minecraft:vindication_illager=3",
                "minecraft:evocation_illager=4"
        };
    }

    public static final class BossSettings {

        @Config.Name("bossPoolByTier")
        @Config.LangKey("config.shatteredcitadels.boss_settings.bossPoolByTier")
        @Config.Comment({
                "Candidate floor bosses per tier. Format: tier=modid:entity,modid:entity (tier 1-5).",
                "Several lines may share a tier. Entities of missing mods are skipped at load;",
                "if a tier ends up empty the nearest lower tier (then vanilla) is used."
        })
        public String[] bossPoolByTier = {
                "1=minecraft:zombie,minecraft:skeleton",
                "2=minecraft:husk,minecraft:stray",
                "3=minecraft:wither_skeleton,minecraft:vindication_illager",
                "4=minecraft:evocation_illager,minecraft:blaze",
                "5=minecraft:wither_skeleton,minecraft:evocation_illager"
        };

        @Config.Name("useBuiltInModBosses")
        @Config.LangKey("config.shatteredcitadels.boss_settings.useBuiltInModBosses")
        @Config.Comment({
                "Add the bosses of installed mob mods to the pool above: Mowzie's Mobs (Frostmaw,",
                "Ferrous Wroughtnaut, Barako), Atum (Pharaoh, Bandit Warlord, Stonewarden) and",
                "Thermal Foundation elementals. Missing mods are skipped."
        })
        public boolean useBuiltInModBosses = true;

        @Config.Name("preferModdedBosses")
        @Config.LangKey("config.shatteredcitadels.boss_settings.preferModdedBosses")
        @Config.Comment("Try modded bosses of a tier before vanilla ones; vanilla mobs remain the fallback if no modded boss fits the arena.")
        public boolean preferModdedBosses = true;

        @Config.Name("mageHealth")
        @Config.LangKey("config.shatteredcitadels.boss_settings.mageHealth")
        @Config.Comment("Health of the catacomb guardian mage.")
        @Config.RangeDouble(min = 50.0D, max = 5000.0D)
        public double mageHealth = 300.0D;

        @Config.Name("mageArmor")
        @Config.LangKey("config.shatteredcitadels.boss_settings.mageArmor")
        @Config.Comment("Armor of the catacomb guardian mage.")
        @Config.RangeDouble(min = 0.0D, max = 30.0D)
        public double mageArmor = 6.0D;

        @Config.Name("mageLightningWarningTicks")
        @Config.LangKey("config.shatteredcitadels.boss_settings.mageLightningWarningTicks")
        @Config.Comment("Ticks between the mage's lightning warning circle and the strike.")
        @Config.RangeInt(min = 10, max = 100)
        public int mageLightningWarningTicks = 30;

        @Config.Name("mageTeleportMinTicks")
        @Config.LangKey("config.shatteredcitadels.boss_settings.mageTeleportMinTicks")
        @Config.Comment("Shortest time between the mage's teleports (ticks).")
        @Config.RangeInt(min = 40, max = 1200)
        public int mageTeleportMinTicks = 160;

        @Config.Name("mageTeleportMaxTicks")
        @Config.LangKey("config.shatteredcitadels.boss_settings.mageTeleportMaxTicks")
        @Config.Comment("Longest time between the mage's teleports (ticks).")
        @Config.RangeInt(min = 40, max = 1200)
        public int mageTeleportMaxTicks = 240;

        @Config.Name("mageMaxGuards")
        @Config.LangKey("config.shatteredcitadels.boss_settings.mageMaxGuards")
        @Config.Comment("Most skeleton guards the mage keeps alive at once.")
        @Config.RangeInt(min = 3, max = 20)
        public int mageMaxGuards = 6;

        @Config.Name("bossHealthMultiplierPerFloor")
        @Config.LangKey("config.shatteredcitadels.boss_settings.bossHealthMultiplierPerFloor")
        @Config.Comment("Compounding health multiplier per floor: health = base * value^(floor - 1).")
        @Config.RangeDouble(min = 1.0D, max = 3.0D)
        public double bossHealthMultiplierPerFloor = 1.15D;

        @Config.Name("bossDamageMultiplierPerFloor")
        @Config.LangKey("config.shatteredcitadels.boss_settings.bossDamageMultiplierPerFloor")
        @Config.Comment("Compounding damage multiplier per floor: damage = base * value^(floor - 1).")
        @Config.RangeDouble(min = 1.0D, max = 3.0D)
        public double bossDamageMultiplierPerFloor = 1.10D;

        @Config.Name("skyCitadelBossMultiplier")
        @Config.LangKey("config.shatteredcitadels.boss_settings.skyCitadelBossMultiplier")
        @Config.Comment("Extra health and damage multiplier for the final boss of the Citadel Above the Clouds.")
        @Config.RangeDouble(min = 1.0D, max = 20.0D)
        public double skyCitadelBossMultiplier = 3.0D;

        @Config.Name("enableBossCutscene")
        @Config.LangKey("config.shatteredcitadels.boss_settings.enableBossCutscene")
        @Config.Comment("Play the entrance cutscene the first time a player enters a boss arena; the boss appears when it ends.")
        public boolean enableBossCutscene = true;

        @Config.Name("bossCutsceneSeconds")
        @Config.LangKey("config.shatteredcitadels.boss_settings.bossCutsceneSeconds")
        @Config.Comment("Length of the entrance cutscene. Players can skip it with sneak.")
        @Config.RangeInt(min = 3, max = 15)
        public int bossCutsceneSeconds = 6;

        @Config.Name("bossTriggerRadius")
        @Config.LangKey("config.shatteredcitadels.boss_settings.bossTriggerRadius")
        @Config.Comment("Without a cutscene (disabled or already seen) the boss is summoned when a player comes this close to the altar.")
        @Config.RangeInt(min = 2, max = 16)
        public int bossTriggerRadius = 6;

        @Config.Name("strictArenaAntiGrief")
        @Config.LangKey("config.shatteredcitadels.boss_settings.strictArenaAntiGrief")
        @Config.Comment("Forbid bosses (and their projectiles/explosions) from breaking blocks of the arena.")
        public boolean strictArenaAntiGrief = true;
    }

    public static final class HudAndLoot {

        @Config.Name("enableFloorHud")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.enableFloorHud")
        @Config.Comment("Show the floor / wave HUD while inside a citadel (client only).")
        public boolean enableFloorHud = true;

        @Config.Name("hudPosition")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.hudPosition")
        @Config.Comment("HUD anchor. Default keeps clear of Xaero's Minimap, Hwyla/Jade, potion icons and chat.")
        public HudPosition hudPosition = HudPosition.CENTER_LEFT;

        @Config.Name("enableFloorBanner")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.enableFloorBanner")
        @Config.Comment("Show the fading citadel/floor title banner when entering a citadel or changing floors (client only).")
        public boolean enableFloorBanner = true;

        @Config.Name("bannerOffsetY")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.bannerOffsetY")
        @Config.Comment({
                "Vertical offset of the floor banner in GUI pixels. The banner sits below the crosshair,",
                "clear of Traveler's Titles (upper centre) and boss bars (top)."
        })
        @Config.RangeInt(min = -200, max = 200)
        public int bannerOffsetY = 0;

        @Config.Name("hudOffsetX")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.hudOffsetX")
        @Config.Comment("Horizontal HUD offset in GUI pixels, away from the anchored edge.")
        @Config.RangeInt(min = 0, max = 512)
        public int hudOffsetX = 4;

        @Config.Name("hudOffsetY")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.hudOffsetY")
        @Config.Comment("Vertical HUD offset in GUI pixels, away from the anchored edge.")
        @Config.RangeInt(min = 0, max = 512)
        public int hudOffsetY = 0;

        @Config.Name("hudScale")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.hudScale")
        @Config.Comment("HUD scale.")
        @Config.RangeDouble(min = 0.5D, max = 2.0D)
        public double hudScale = 1.0D;

        @Config.Name("lowFloorRareLootChance")
        @Config.LangKey("config.shatteredcitadels.hud_and_loot.lowFloorRareLootChance")
        @Config.Comment("Chance for a rare item in loot of the lower floors (0.025 = 2.5%).")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double lowFloorRareLootChance = 0.025D;
    }

    public static final class Bridges {

        @Config.Name("bridgesEnabled")
        @Config.LangKey("config.shatteredcitadels.bridges.bridgesEnabled")
        @Config.Comment("Generate bridges over rivers in new chunks.")
        public boolean bridgesEnabled = true;

        @Config.Name("bridgeMinDistanceChunks")
        @Config.LangKey("config.shatteredcitadels.bridges.bridgeMinDistanceChunks")
        @Config.Comment("Minimum distance between two bridges, in chunks.")
        @Config.RangeInt(min = 8, max = 128)
        public int bridgeMinDistanceChunks = 24;

        @Config.Name("bridgeChance")
        @Config.LangKey("config.shatteredcitadels.bridges.bridgeChance")
        @Config.Comment("Chance that a suitable river crossing gets a bridge.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double bridgeChance = 0.5D;

        @Config.Name("bridgeFortifiedChance")
        @Config.LangKey("config.shatteredcitadels.bridges.bridgeFortifiedChance")
        @Config.Comment("Chance that a bridge over a wide river is held by bandits (barricades, guards, a chest).")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double bridgeFortifiedChance = 0.2D;

        @Config.Name("bridgeSpecialChance")
        @Config.LangKey("config.shatteredcitadels.bridges.bridgeSpecialChance")
        @Config.Comment("Chance of a rare bridge: ruined, covered or with gate towers.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double bridgeSpecialChance = 0.1D;

        @Config.Name("bridgeDimensions")
        @Config.LangKey("config.shatteredcitadels.bridges.bridgeDimensions")
        @Config.Comment("Dimension ids where bridges generate.")
        public int[] bridgeDimensions = {0};
    }

    public static final class WitchHuts {

        @Config.Name("witchHutsEnabled")
        @Config.LangKey("config.shatteredcitadels.witchHuts.witchHutsEnabled")
        @Config.Comment("Build the witch hut of this mod instead of the vanilla swamp hut (new sites only; restart the world to apply).")
        public boolean witchHutsEnabled = true;

        @Config.Name("witchHutCellar")
        @Config.LangKey("config.shatteredcitadels.witchHuts.witchHutCellar")
        @Config.Comment("Build the cellar lair (rooms, witches, potion traps, final chest) under the hut.")
        public boolean witchHutCellar = true;

        @Config.Name("witchHutMinWitches")
        @Config.LangKey("config.shatteredcitadels.witchHuts.witchHutMinWitches")
        @Config.Comment("Fewest witches placed in a cellar.")
        @Config.RangeInt(min = 0, max = 12)
        public int witchHutMinWitches = 2;

        @Config.Name("witchHutMaxWitches")
        @Config.LangKey("config.shatteredcitadels.witchHuts.witchHutMaxWitches")
        @Config.Comment("Most witches placed in a cellar.")
        @Config.RangeInt(min = 0, max = 12)
        public int witchHutMaxWitches = 5;
    }

    public static final class Herobrine {

        @Config.Name("herobrineEnabled")
        @Config.LangKey("config.shatteredcitadels.herobrine.herobrineEnabled")
        @Config.Comment("Master switch: the altar summons Herobrine. Off = no boss, no lairs, no apparitions.")
        public boolean herobrineEnabled = true;

        @Config.Name("lairsEnabled")
        @Config.LangKey("config.shatteredcitadels.herobrine.lairsEnabled")
        @Config.Comment("Generate lairs of Herobrine (ruined keep, obsidian tower, underground shrine) in new chunks of the overworld.")
        public boolean lairsEnabled = true;

        @Config.Name("lairSpacingChunks")
        @Config.LangKey("config.shatteredcitadels.herobrine.lairSpacingChunks")
        @Config.Comment("Size of the region that holds at most one lair, in chunks (112 = about 1800 blocks).")
        @Config.RangeInt(min = 32, max = 512)
        public int lairSpacingChunks = 112;

        @Config.Name("lairChance")
        @Config.LangKey("config.shatteredcitadels.herobrine.lairChance")
        @Config.Comment("Chance that a region gets a lair at all.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double lairChance = 0.6D;

        @Config.Name("lairSpawnClearanceChunks")
        @Config.LangKey("config.shatteredcitadels.herobrine.lairSpawnClearanceChunks")
        @Config.Comment("No lair closer than this to the world spawn, in chunks.")
        @Config.RangeInt(min = 0, max = 256)
        public int lairSpawnClearanceChunks = 32;

        @Config.Name("playerBuiltAltar")
        @Config.LangKey("config.shatteredcitadels.herobrine.playerBuiltAltar")
        @Config.Comment("Also accept an altar built by a player anywhere in the overworld (gold 3x3, netherrack on the centre, four redstone torches).")
        public boolean playerBuiltAltar = false;

        @Config.Name("herobrineHealth")
        @Config.LangKey("config.shatteredcitadels.herobrine.herobrineHealth")
        @Config.Comment("Health of Herobrine.")
        @Config.RangeDouble(min = 20.0D, max = 1024.0D)
        public double herobrineHealth = 600.0D;

        @Config.Name("herobrineDamage")
        @Config.LangKey("config.shatteredcitadels.herobrine.herobrineDamage")
        @Config.Comment("Melee damage of Herobrine before armour.")
        @Config.RangeDouble(min = 1.0D, max = 100.0D)
        public double herobrineDamage = 11.0D;

        @Config.Name("herobrineArmor")
        @Config.LangKey("config.shatteredcitadels.herobrine.herobrineArmor")
        @Config.Comment("Armour points of Herobrine.")
        @Config.RangeDouble(min = 0.0D, max = 30.0D)
        public double herobrineArmor = 10.0D;

        @Config.Name("herobrineMaxMinions")
        @Config.LangKey("config.shatteredcitadels.herobrine.herobrineMaxMinions")
        @Config.Comment("Most undead servants alive at once in the third phase (0 = none).")
        @Config.RangeInt(min = 0, max = 16)
        public int herobrineMaxMinions = 6;

        @Config.Name("lightningWarningTicks")
        @Config.LangKey("config.shatteredcitadels.herobrine.lightningWarningTicks")
        @Config.Comment("Ticks of warning (particles and a hiss) before his lightning strikes; 20 ticks = 1 second.")
        @Config.RangeInt(min = 20, max = 100)
        public int lightningWarningTicks = 30;

        @Config.Name("lightningDamage")
        @Config.LangKey("config.shatteredcitadels.herobrine.lightningDamage")
        @Config.Comment("Damage of his lightning to everyone within 3 blocks of the marked spot.")
        @Config.RangeDouble(min = 0.0D, max = 40.0D)
        public double lightningDamage = 12.0D;

        @Config.Name("herobrineGriefing")
        @Config.LangKey("config.shatteredcitadels.herobrine.herobrineGriefing")
        @Config.Comment("His lightning sets the ground on fire. Off = no block is ever changed by the fight.")
        public boolean herobrineGriefing = false;

        @Config.Name("apparitions")
        @Config.LangKey("config.shatteredcitadels.herobrine.apparitions")
        @Config.Comment("A silent figure sometimes watches players near a lair whose master is not yet defeated. Harmless.")
        public boolean apparitions = true;

        @Config.Name("whispers")
        @Config.LangKey("config.shatteredcitadels.herobrine.whispers")
        @Config.Comment("A quiet whisper is sometimes heard within 48 blocks of a lair whose master is not yet defeated.")
        public boolean whispers = true;

        @Config.Name("apparitionIntervalMinutes")
        @Config.LangKey("config.shatteredcitadels.herobrine.apparitionIntervalMinutes")
        @Config.Comment("At most one apparition attempt per player in this many minutes.")
        @Config.RangeInt(min = 1, max = 240)
        public int apparitionIntervalMinutes = 8;

        @Config.Name("apparitionChance")
        @Config.LangKey("config.shatteredcitadels.herobrine.apparitionChance")
        @Config.Comment("Chance that a due attempt really shows the figure.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double apparitionChance = 0.0003D;

        @Config.Name("apparitionRadiusBlocks")
        @Config.LangKey("config.shatteredcitadels.herobrine.apparitionRadiusBlocks")
        @Config.Comment("Apparitions happen only this close to a lair, in blocks (0 = never).")
        @Config.RangeInt(min = 0, max = 2048)
        public int apparitionRadiusBlocks = 256;
    }

    public static final class Integration {

        @Config.Name("castleDungeonsOverrideSectionSize")
        @Config.LangKey("config.shatteredcitadels.integration.castleDungeonsOverrideSectionSize")
        @Config.Comment("Apply castle_generation.castleSectionSize and the minimum citadel distance to Castle Dungeons.")
        public boolean castleDungeonsOverrideSectionSize = true;

        @Config.Name("castleDungeonsReplaceSpawners")
        @Config.LangKey("config.shatteredcitadels.integration.castleDungeonsReplaceSpawners")
        @Config.Comment("Replace Castle Dungeons spawners with citadel wave spawners (blacklisted mobs removed).")
        public boolean castleDungeonsReplaceSpawners = true;

        @Config.Name("castleDungeonsPatchConfigFiles")
        @Config.LangKey("config.shatteredcitadels.integration.castleDungeonsPatchConfigFiles")
        @Config.Comment({
                "Fallback if the mixins cannot apply (other Castle Dungeons version): rewrite section-size and",
                "strip blacklisted mobs in config/castle_dungeons/*.cfg before Castle Dungeons reads them.",
                "A one-time backup (*.shatteredcitadels.bak) is written next to each changed file."
        })
        public boolean castleDungeonsPatchConfigFiles = true;

        @Config.Name("enableModdedPalettes")
        @Config.LangKey("config.shatteredcitadels.integration.enableModdedPalettes")
        @Config.Comment("Use blocks from installed mods in citadel palettes (vanilla blocks are always the fallback).")
        @Config.RequiresMcRestart
        public boolean enableModdedPalettes = true;

        @Config.Name("paletteOverrides")
        @Config.LangKey("config.shatteredcitadels.integration.paletteOverrides")
        @Config.Comment({
                "Palette overrides, tried before the built-in candidates. Format: archetype.slot=entry",
                "archetype: keepers_citadel, obsidian_bastille, techno_arcane_spire, titan_forge, sky_citadel",
                "slot: wall, pillar, floor, stairs, accent, light, furniture",
                "entry: modid:block, modid:block@meta, modid:*keyword* (scan), variants joined with |"
        })
        @Config.RequiresMcRestart
        public String[] paletteOverrides = {};
    }

    /**
     * Re-syncs the annotation fields with the {@code .cfg} after in-game edits and rebuilds the
     * validated snapshot. The event is posted by the config GUI, so on a dedicated server this
     * handler simply never fires.
     */
    @Mod.EventBusSubscriber(modid = Reference.MODID)
    public static final class Dimensions {

        @Config.Name("netherEnabled")
        @Config.LangKey("config.shatteredcitadels.dimensions.netherEnabled")
        @Config.Comment("Generate nether brick towers and castles in the Nether.")
        public boolean netherEnabled = true;

        @Config.Name("netherSpacing")
        @Config.LangKey("config.shatteredcitadels.dimensions.netherSpacing")
        @Config.Comment("How much sparser than in the overworld structures are in the Nether (1.5 = sections 1.5 times wider).")
        @Config.RangeDouble(min = 1.0D, max = 8.0D)
        public double netherSpacing = 1.5D;

        @Config.Name("netherCastleChance")
        @Config.LangKey("config.shatteredcitadels.dimensions.netherCastleChance")
        @Config.Comment("Chance that a Nether site holds a walled castle instead of a tower.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double netherCastleChance = 0.25D;

        @Config.Name("netherMobs")
        @Config.LangKey("config.shatteredcitadels.dimensions.netherMobs")
        @Config.Comment("Garrison of structures in the Nether (entity ids). Empty: the usual tier list.")
        public String[] netherMobs = {"minecraft:wither_skeleton", "minecraft:blaze", "minecraft:zombie_pigman",
                "minecraft:magma_cube"};

        @Config.Name("endEnabled")
        @Config.LangKey("config.shatteredcitadels.dimensions.endEnabled")
        @Config.Comment("Generate purpur towers and castles on the outer islands of the End.")
        public boolean endEnabled = true;

        @Config.Name("endSpacing")
        @Config.LangKey("config.shatteredcitadels.dimensions.endSpacing")
        @Config.Comment("How much sparser than in the overworld structures are in the End.")
        @Config.RangeDouble(min = 1.0D, max = 8.0D)
        public double endSpacing = 1.5D;

        @Config.Name("endCastleChance")
        @Config.LangKey("config.shatteredcitadels.dimensions.endCastleChance")
        @Config.Comment("Chance that an End site holds a walled castle instead of a tower (only where the island is wide enough).")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double endCastleChance = 0.15D;

        @Config.Name("endMinDistance")
        @Config.LangKey("config.shatteredcitadels.dimensions.endMinDistance")
        @Config.Comment("Nothing is generated in the End closer than this many blocks to the central island.")
        @Config.RangeInt(min = 600, max = 100000)
        public int endMinDistance = 1000;

        @Config.Name("endMobs")
        @Config.LangKey("config.shatteredcitadels.dimensions.endMobs")
        @Config.Comment("Garrison of structures in the End (entity ids). Empty: the usual tier list.")
        public String[] endMobs = {"minecraft:enderman", "minecraft:shulker", "minecraft:endermite"};

        @Config.Name("atumEnabled")
        @Config.LangKey("config.shatteredcitadels.dimensions.atumEnabled")
        @Config.Comment("Generate limestone towers and the mod's desert temple in Atum (needs the Atum 2 mod).")
        public boolean atumEnabled = true;

        @Config.Name("atumSpacing")
        @Config.LangKey("config.shatteredcitadels.dimensions.atumSpacing")
        @Config.Comment("How much sparser than in the overworld structures are in Atum.")
        @Config.RangeDouble(min = 1.0D, max = 8.0D)
        public double atumSpacing = 1.5D;

        @Config.Name("atumTempleChance")
        @Config.LangKey("config.shatteredcitadels.dimensions.atumTempleChance")
        @Config.Comment("Chance that an Atum site holds a desert temple instead of a tower.")
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double atumTempleChance = 0.4D;

        @Config.Name("atumMobs")
        @Config.LangKey("config.shatteredcitadels.dimensions.atumMobs")
        @Config.Comment("Garrison of structures in Atum (entity ids; unknown ones are skipped). Empty: the usual tier list.")
        public String[] atumMobs = {"atum:mummy", "atum:forsaken", "atum:bonestorm", "atum:wraith", "atum:stoneguard",
                "minecraft:husk"};

        @Config.Name("replaceNetherFortress")
        @Config.LangKey("config.shatteredcitadels.dimensions.replaceNetherFortress")
        @Config.Comment("Build the mod's own large fortress where a vanilla Nether fortress would be (new chunks only). Needs a restart.")
        @Config.RequiresMcRestart
        public boolean replaceNetherFortress = true;

        @Config.Name("fortressBlazeSpawners")
        @Config.LangKey("config.shatteredcitadels.dimensions.fortressBlazeSpawners")
        @Config.Comment("Blaze spawner platforms in one fortress.")
        @Config.RangeInt(min = 0, max = 4)
        public int fortressBlazeSpawners = 3;
    }

    public static final class ChangeHandler {

        private ChangeHandler() {
        }

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!Reference.MODID.equals(event.getModID())) {
                return;
            }
            ConfigManager.sync(Reference.MODID, Config.Type.INSTANCE);
            ConfigCache.rebuild();
            if (ConfigCache.isRegistryPhaseComplete()) {
                ConfigCache.resolveRegistryEntries();
            }
            ShatteredCitadelsMod.LOGGER.info("Configuration reloaded");
        }
    }
}
