# River Bridges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bridges over rivers in newly generated terrain: seven biome styles, three rare specials, one in five fortified with bandits and a chest.

**Architecture:** A pure crossing finder reads the biome map around a river chunk being populated (no chunk loads) and yields an axis-aligned crossing. A `BridgePlan` (a `StructurePlan`) is submitted to `StructureJobs`; its pure `build` draws the bridge into a `BlockWriter`, and everything that depends on real terrain uses the existing write-time column operations (`fillDown`, `clearColumn`, `setIfNotSolid`). Guards are spawned once by a tile action when the camp's chunk is written.

**Tech Stack:** Forge 1.12.2 (14.23.5.2860), Java 8, JUnit as in `src/test/java/com/shatteredcitadels/worldgen/JungleTempleTest.java`.

**Spec:** `docs/superpowers/specs/2026-11-26-river-bridges-design.md`

## Global Constraints

- Build: `JAVA_HOME=$USERPROFILE/.gradle/jdks/azul_systems__inc_-21-amd64-windows.2 ./gradlew build --no-daemon -q`, `-Werror`.
- No compile-time dependency on pack mods; other mods' blocks and entities by registry id at run time.
- Own code only, nothing from YUNG's Bridges.
- en_us and ru_ru for every new string.
- Old worlds: `StructureType` constants never renamed; `BRIDGE` appended last; new NBT fields have defaults.
- `StructurePlan.build` is pure: no world access, all randomness from the plan seed.
- New package `com.shatteredcitadels.worldgen.bridge`; tests in `src/test/java/com/shatteredcitadels/worldgen/` (existing layout).
- Version 1.10.0 in `gradle.properties` and `Reference.VERSION`.

## Review Focus

1. A river that bends inside the bridge's width: the finder must reject it, not build a bridge ending in water (Task 1 test `rejectsBend`).
2. A crossing at a section of frozen river or a river beside ocean or lake biome: the far "bank" is water; reject when either bank column is an ocean, river or beach-less water biome (Task 1 test `rejectsWaterBank`).
3. Server restart while a bridge is half written: the plan must rebuild identically from NBT (Task 5 test `nbtRoundTripBuildsSameBlueprint`).
4. Modern Backports absent: pillager id unresolved, guards fall back; no crash, no empty camp (Task 4 test `guardsFallBackWithoutPillager`).
5. Two river chunks of the same reach populated in the same tick: only one bridge (Task 6: the pending job counts in the distance rule; test `secondCrossingNearPendingBridgeIsSkipped`).

---

### Task 1: Crossing finder

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/bridge/RiverCrossing.java`
- Test: `src/test/java/com/shatteredcitadels/worldgen/RiverCrossingTest.java`

**Interfaces:**
- Produces:
  - `interface RiverCrossing.BiomeMap { boolean isRiver(int x, int z); boolean isWater(int x, int z); }` (`isWater` = river, ocean, deep ocean, frozen ocean; river implies water)
  - `static RiverCrossing find(BiomeMap map, int x, int z)` returns null or a crossing through the river column `(x, z)`
  - fields `final boolean alongX` (deck runs along X), `final int start, end` (first and last river coordinate on the deck axis), `final int cross` (fixed coordinate on the other axis), `int width()` = `end - start + 1`
  - constants `MIN_WIDTH = 5`, `MAX_WIDTH = 24`

Rules: from `(x, z)` measure the river run on both axes (stop at 41). The deck axis is the shorter run; it must be 5..24 and the other run at least twice as long (41 counts as long enough). The runs at `cross - 3` and `cross + 3` must each differ from the centre run by at most 4 in both start and end. The two columns just outside the run (`start - 1`, `end + 1`) and two further out must not be water. `cross` is moved to the given coordinate unchanged (the caller picks the chunk centre).

- [ ] **Step 1: failing tests** on maps built from string rows (`'~'` river, `'o'` ocean, `'.'` land): `findsStraightNorthSouthRiver` (width 7 → `alongX == true`, `width() == 7`), `findsEastWestRiver`, `rejectsTooNarrow` (4), `rejectsTooWide` (25), `rejectsBend`, `rejectsWaterBank` (ocean beside the river), `rejectsLakeLikeBlob` (9×9 river patch), `nullWhenStartIsNotRiver`.
- [ ] **Step 2: run** `./gradlew test --no-daemon -q --tests '*RiverCrossingTest'`, expect compile failure.
- [ ] **Step 3: implement** `RiverCrossing`.
- [ ] **Step 4: run**, expect 8 passed.
- [ ] **Step 5: commit** `feat(bridges): river crossing finder`.

### Task 2: Styles and their choice

**Files:**
- Create: `worldgen/bridge/BridgeStyle.java`
- Test: `src/test/java/com/shatteredcitadels/worldgen/BridgeStyleTest.java`

**Interfaces:**
- Produces `enum BridgeStyle { TIMBER, STONE_ARCH, SPRUCE, SANDSTONE, ROPE, BOARDWALK, SNOW_STONE, RUINED, COVERED, TURRETED }` with
  - `final int width` (3: TIMBER, SPRUCE, ROPE, BOARDWALK; 5: the rest), `final int deckRise` (deck y above sea level: BOARDWALK 1, ROPE 3, TIMBER/SPRUCE/SANDSTONE/SNOW_STONE/COVERED/RUINED 3, STONE_ARCH/TURRETED 4), `final boolean solidDeck` (false for ROPE, BOARDWALK, RUINED), `final boolean special` (RUINED, COVERED, TURRETED), `String id()` lower-case name, `static BridgeStyle byId(String)` (null if unknown)
  - `enum Bank { FOREST, TAIGA, DESERT, JUNGLE, SWAMP, SNOW }`
  - `static BridgeStyle choose(Bank bank, int riverWidth, Random rand, double specialChance)`
  - `static boolean fortified(BridgeStyle style, int riverWidth, Random rand, double fortifiedChance)`

Rules: special roll first (`rand.nextDouble() < specialChance`): RUINED, COVERED or TURRETED equally; TURRETED needs river width ≥ 9, else COVERED. Otherwise by bank: FOREST → STONE_ARCH when width ≥ 9 and a coin flip, else TIMBER; TAIGA → SPRUCE; DESERT → SANDSTONE; JUNGLE → ROPE; SWAMP → BOARDWALK when width ≤ 16 else TIMBER; SNOW → SNOW_STONE. `fortified`: TURRETED always; other specials never; else `solidDeck && riverWidth >= 9 && rand.nextDouble() < fortifiedChance`.

- [ ] **Step 1: failing tests**: `everyBankHasItsStyle`, `specialChanceOneGivesOnlySpecials`, `specialChanceZeroGivesNone`, `turretedNeedsWidth9`, `turretedAlwaysFortified`, `ropeAndBoardwalkNeverFortified`, `fortifiedShareIsAboutOneFifth` (10 000 rolls of TIMBER width 12 at 0.2 → 1 700..2 300), `byIdRoundTrips`.
- [ ] **Step 2: run**, expect failure. **Step 3: implement.** **Step 4: run**, expect pass.
- [ ] **Step 5: commit** `feat(bridges): styles and selection`.

### Task 3: Bridge shapes

**Files:**
- Create: `worldgen/bridge/BridgeBuilder.java`, `worldgen/bridge/BridgePalette.java`
- Test: `src/test/java/com/shatteredcitadels/worldgen/BridgeBuilderTest.java`

**Interfaces:**
- Consumes: `BridgeStyle`, `BlockWriter` (`set`, `fill`, `fillDown`, `clearColumn`, `setIfNotSolid`), `ModPaletteRegistry.resolveDecor` for optional blocks (lanterns, chains).
- Produces:
  - `BridgeBuilder(BlockWriter w, Random rand, BridgeStyle style, boolean alongX, int start, int end, int cross, int seaLevel)`
  - `void build()`
  - `static int deckY(BridgeStyle style, int seaLevel)` = `seaLevel + style.deckRise`
  - `static int ABUTMENT = 3` (deck blocks past each end of the river run), `static int approachLength(BridgeStyle)` = `deckRise` (one stair per block down to `seaLevel + 1`... the last step is at `seaLevel + 1`)
  - `static StructureBoundingBox footprint(BridgeStyle style, boolean alongX, int start, int end, int cross, int seaLevel)` (deck + abutments + approaches + 6 extra on each end for a camp, half-width + 2 sideways, y from `seaLevel - 12` to `deckY + 9`)
  - `BlockPos campOrigin()` (bank end at `end` side, ground level `seaLevel + 1`, beside the approach), `int deckFirst()`, `int deckLast()`

Shape rules common to all styles: deck from `start - ABUTMENT` to `end + ABUTMENT` at `deckY`; `clearColumn` over the deck and approaches from `deckY + 1` to `deckY + 4`; under each abutment `fillDown` (depth 8); piers every 6..8 blocks over the run with `fillDown` to the bed (depth 12), none within 2 blocks of the middle for arch styles; approaches descend one block per block of length to `seaLevel + 1`, each step `fillDown` beneath (depth 6) and placed with `set` (stairs or slabs facing the deck); railings on both edges except at the ends. Per style:
- TIMBER / SPRUCE: oak / spruce planks and slabs, log piers, fence railing, torch posts at the ends.
- STONE_ARCH / SNOW_STONE: stone brick deck, arch underside rising 2 toward the middle so that at the middle three columns the lowest bridge block is at `seaLevel + 3` or higher (boat clearance 2 above water), wall railing; SNOW_STONE adds lantern posts every 5 blocks (`resolveDecor("modernbackports:lantern")`, fallback torch on a fence) and stone brick slabs.
- SANDSTONE: smooth sandstone deck, sandstone wall or slab parapet, chiselled end posts.
- ROPE: slab deck sagging 1 block in the middle third, fence "ropes" both sides, two log posts 3 high at each end, vines hanging 1..3 below the deck edge; no piers.
- BOARDWALK: slab deck at `seaLevel + 1`, log piles every 3 blocks on both edges, railing on one side only.
- RUINED: STONE_ARCH with the middle `max(3, width/4)` deck columns absent, cracked and mossy brick mixed in, railing missing in random places.
- COVERED: TIMBER 5 wide with posts every 3 blocks and a stair-block gable roof at `deckY + 4`, headroom 3 kept.
- TURRETED: STONE_ARCH with a 5×5 turret 6 high at each end over the abutment, the deck passing through a 3-wide 3-high gate in each.

- [ ] **Step 1: failing tests**, each run for `alongX` true and false and river widths 5, 12, 24, reading the blueprint through `BlockWriter.get`: `deckIsContinuousForEveryStyleExceptRuined` (every deck-axis position from first approach step to last has a walkable top within 1 block of its neighbour along the centre line), `ruinedHasGap`, `headroomIsThree` (3 non-solid planned-or-untouched blocks above each centre-line deck block; COVERED and TURRETED included), `archLeavesBoatClearance`, `everythingInsideFootprint` (`getRejected() == 0` with the footprint as the writer's bounds), `boardwalkDeckIsOneAboveSea`, `buildIsDeterministic` (same seed → same block count and same blocks on the centre line).
- [ ] **Step 2: run**, expect failure. **Step 3: implement** (private method per style, shared helpers for deck, piers, approaches, railing). **Step 4: run**, expect pass.
- [ ] **Step 5: commit** `feat(bridges): ten bridge shapes`.

### Task 4: Fortified camp

**Files:**
- Create: `worldgen/bridge/BridgeCamp.java`, `src/main/resources/assets/shatteredcitadels/loot_tables/chests/bridge_camp.json`
- Modify: the class that registers the mod's loot tables (find with `grep -rn "LootTableList.register" src/main/java`)
- Test: `src/test/java/com/shatteredcitadels/worldgen/BridgeCampTest.java`

**Interfaces:**
- Consumes: `BridgeBuilder.campOrigin()`, `deckFirst()`, `deckLast()`, `BlockWriter.afterPlace`.
- Produces:
  - `BridgeCamp(BlockWriter w, Random rand, BridgeBuilder bridge, boolean alongX, int cross, int deckY, int halfWidth)`; `void build()`
  - `static final ResourceLocation LOOT = new ResourceLocation("shatteredcitadels", "chests/bridge_camp")`
  - `static int guardCount(Random rand)` → 3..5
  - `static ResourceLocation[] guardIds(java.util.function.Predicate<ResourceLocation> registered)` → `[melee, ranged]`: melee `minecraft:vindication_illager`; ranged `modernbackports:pillager` when registered, else `minecraft:skeleton`
  - `BlockPos chestPos()`

Camp: two barricades across the deck at one third and two thirds of the run (fence and hay/log blocks) each leaving a 1-wide passage on alternating sides; a 5×5 hut (planks, log corners, slab roof, door facing the approach) on `fillDown` footing at `campOrigin()`; inside a chest with `LOOT` and a bed-less bench (slab); for TURRETED no hut: the chest stands in the far turret. Guards: one `afterPlace` action on the chest position spawns `guardCount` mobs (first two melee, rest ranged; with the skeleton fallback give a bow) around the chest on the deck side, calls `enablePersistence()`, skips when the world difficulty is Peaceful. Entity creation by id through `EntityList.createEntityByIDFromName`; a null entity is skipped. Confirm the exact pillager id with `grep -rn "pillager" F:/minecraft-mods/modernbackports/src/main/java | head` before fixing the constant.

Loot table: one pool of 4..7 rolls: bread, cooked beef, apple (weight 8 each, 1..4), arrow (weight 8, 4..12), iron ingot (weight 6, 1..4), iron nugget (weight 6, 3..9), emerald (weight 2, 1..2), saddle (weight 1), enchanted book via `enchant_randomly` (weight 1).

- [ ] **Step 1: failing tests**: `guardCountIsThreeToFive`, `guardsFallBackWithoutPillager`, `guardsUsePillagerWhenRegistered`, `barricadesLeaveAPassage` (each barricade row across the deck has exactly one non-solid column at deck level + 1 and + 2), `chestIsPlannedOnce`, `lootTableFileIsValidJson` (parses, has `pools`).
- [ ] **Step 2: run**, expect failure. **Step 3: implement.** **Step 4: run**, expect pass.
- [ ] **Step 5: commit** `feat(bridges): fortified camp, guards and loot`.

### Task 5: Plan, record, type

**Files:**
- Create: `worldgen/bridge/BridgePlan.java`
- Modify: `structure/StructureType.java` (append `BRIDGE`), `worldgen/StructurePlans.java` (case `BridgePlan.KIND`), `structure/CitadelInstance.java` (`isUnprotected()` true for `BRIDGE`), and every consumer that shows or tracks a record: `grep -rn "StructureType\.\(STRONGHOLD\|DESERT_TEMPLE\)" src/main/java` and the HUD/title code (`grep -rn "getCitadelAt" src/main/java`): a `BRIDGE` record shows no HUD panel, no entry title, no floors, no advancement.
- Test: `src/test/java/com/shatteredcitadels/worldgen/BridgePlanTest.java`

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces:
  - `BridgePlan(BridgeStyle style, boolean fortified, boolean alongX, int start, int end, int cross, int seaLevel, long seed, int dimension)`
  - `static final String KIND = "bridge"`; `static BridgePlan read(NBTTagCompound tag)`
  - `kind()`, `dimension()`, `footprint()` (= `BridgeBuilder.footprint`), `build(BlockWriter)` (builder, then camp when fortified; returns a `CitadelInstance` of type `BRIDGE` built the way `JungleTemplePlan.build` builds its record, with no gates and no parts), `writeToNBT()`, `describe()` ("bridge timber fortified at x,z")
  - getters `style()`, `fortified()`

- [ ] **Step 1: failing tests**: `nbtRoundTripBuildsSameBlueprint` (write, read, build both, equal block counts and equal `describe()`), `recordIsBridgeAndUnprotected`, `unknownStyleInNbtFallsBackToTimber`, `structureTypeBridgeIsLast` (`values()[values().length - 1] == BRIDGE`, `byName("BRIDGE") == BRIDGE`).
- [ ] **Step 2: run**, expect failure. **Step 3: implement and wire the exemptions.** **Step 4: run the whole suite**, expect all pass.
- [ ] **Step 5: commit** `feat(bridges): bridge plan and record`.

### Task 6: Config and world generation

**Files:**
- Create: `worldgen/bridge/BridgeGenerator.java`
- Modify: `config/ModConfig.java` (new category `bridges`), `config/ConfigCache.java` (snapshot getters), `ShatteredCitadelsMod.java` or wherever `CitadelWorldGenerator` is registered (`GameRegistry.registerWorldGenerator(new BridgeGenerator(), <weight after the citadel generator>)`), `structure/CitadelWorldSavedData.java` (`boolean isFarFromType(StructureType type, int chunkX, int chunkZ, int minDistanceChunks)`), `worldgen/StructureJobs.java` (`boolean isNearKind(String kind, int chunkX, int chunkZ, int minDistanceChunks)` over pending plans), lang files en_us and ru_ru (config keys).
- Test: `src/test/java/com/shatteredcitadels/worldgen/BridgeGeneratorTest.java`

**Interfaces:**
- Consumes: `RiverCrossing.find`, `BridgeStyle.choose/fortified`, `BridgePlan`.
- Produces:
  - config: `bridgesEnabled = true`, `bridgeMinDistanceChunks = 24` (8..128), `bridgeChance = 0.5` (per suitable river chunk), `bridgeFortifiedChance = 0.2`, `bridgeSpecialChance = 0.1`, `bridgeDimensions = {0}`
  - `static BridgePlan BridgeGenerator.planAt(RiverCrossing.BiomeMap map, java.util.function.BiFunction<Integer, Integer, BridgeStyle.Bank> bankAt, int chunkX, int chunkZ, long worldSeed, int seaLevel, int dimension, double chance, double fortified, double special)`: pure; null when the chunk centre `(chunkX*16+8, chunkZ*16+8)` is no crossing, the seeded chunk roll (`new Random(worldSeed ^ ChunkPos.asLong(chunkX, chunkZ) ^ 0xB21D6E5L)`) fails, or `bankAt` returns null for either bank (mountain, ocean, mushroom, unknown)
  - `static BridgeStyle.Bank bankOf(Biome biome)` by `BiomeDictionary` types: SNOWY → SNOW (checked first), JUNGLE → JUNGLE, SWAMP → SWAMP, SANDY or MESA or SAVANNA → DESERT, CONIFEROUS → TAIGA, MOUNTAIN or OCEAN or MUSHROOM or NETHER or END → null, anything else → FOREST. The bank is sampled 6 blocks outside each end of the river run; when the two banks differ, the `start` side wins.
  - `generate(...)` (IWorldGenerator): return unless enabled, dimension listed, `world.getBiomeProvider()` biome of the chunk centre is `BiomeDictionary.Type.RIVER`; build the `BiomeMap` on `world.getBiomeProvider().getBiome(pos)` (no chunk access); `planAt`; then skip when `!data.isFarFromType(BRIDGE, …, minDistance)`, `jobs.isNearKind(BridgePlan.KIND, …, minDistance)`, or `!CitadelWorldGenerator.isAreaFree(world, plan.footprint())`; else `jobs.submit(plan)`. Wrap in the same try/catch-and-log as `CitadelWorldGenerator.generate`.

- [ ] **Step 1: failing tests** (pure parts): `planAtRiverChunkGivesPlan`, `planAtLandChunkIsNull`, `chanceZeroGivesNoPlan`, `mountainBankGivesNoPlan`, `sameSeedSamePlan`, `secondCrossingNearPendingBridgeIsSkipped` (unit test of `StructureJobs.isNearKind` with one submitted `BridgePlan`; if `StructureJobs` cannot be constructed in a unit test, test a static helper `isNear(Iterable<StructurePlan>, kind, chunkX, chunkZ, dist)` instead), `configLangKeysExistInBothLanguages`.
- [ ] **Step 2: run**, expect failure. **Step 3: implement.** **Step 4: run the whole suite and `./gradlew build`**, expect pass.
- [ ] **Step 5: commit** `feat(bridges): config and generation at river crossings`.

### Task 7: Command

**Files:**
- Modify: `command/CommandCitadel.java` (kind `bridge`, optional style id and `fortified`; tab completion like `desert_temple`), lang files (usage and messages).
- Test: extend `BridgeGeneratorTest` with `commandPlanOverNonRiverUsesFallbackSpan`.

**Interfaces:**
- Produces `static BridgePlan BridgeGenerator.planForCommand(RiverCrossing.BiomeMap map, int x, int z, boolean alongX, BridgeStyle styleOrNull, Boolean fortifiedOrNull, BridgeStyle.Bank bank, long seed, int seaLevel, int dimension)`: uses the crossing found within 24 blocks ahead of `(x, z)` on the look axis; when none, a span of 12 centred 10 blocks ahead. `/citadel spawn bridge [style] [fortified]` submits it through the same path as `jungle_temple` and reports `describe()`.

- [ ] **Step 1: failing test. Step 2: run. Step 3: implement. Step 4: run whole suite + build.**
- [ ] **Step 5: commit** `feat(bridges): /citadel spawn bridge`.

### Task 8: Verification and release 1.10.0

- [ ] **Step 1:** bump the version in both places, `./gradlew build`, record the test count.
- [ ] **Step 2: server** (shared test server, LOCK.txt, more than 4 GB free; output to files, read counters only): new default world and new Biomes O' Plenty world, 1 500+ chunks each; count `bridge` plans submitted (log line at INFO from `BridgeGenerator`), `loaded a new chunk` lines naming the mod (expect 0), TPS (expect 20), exceptions naming the mod (expect 0); verify from the log that Biomes O' Plenty worlds do produce bridges (if 0: read which biome ids its rivers use and widen `isRiver`). Load the old `world_taiga`. Restore `server.properties`.
- [ ] **Step 3: game** (autotest clone `1.12.2-test-citadels`, flat or default test world): `/citadel spawn bridge <style>` for all ten styles and one `fortified`; screenshots of each; walk across TIMBER and STONE_ARCH by teleport steps along the deck; open the camp chest; check guards are present, then reload the world and check their number did not grow. Look at 3–5 screenshots.
- [ ] **Step 4:** whole-branch review (superpowers:requesting-code-review), fix findings.
- [ ] **Step 5:** `git mv` the dist jar to `dist/shatteredcitadels-1.10.0.jar`, commit, push, verify the raw link sha1; install into `1.12.2-edit` (game closed, saves backup) and the shared server; update `TRANSFER.md`, handoff, notes; report to the owner with the list of what to check.
