# Jungle Temple (+ BOP temple support) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Our temples replace the desert and jungle temples of both the vanilla and the Biomes O' Plenty
scattered-feature generators; the jungle temple is a large overgrown temple with an idol vault or a
Barako shrine.

**Architecture:** `ScatteredFeatureGen` becomes generator-agnostic: a `TempleSites` strategy (vanilla or
BOP via reflection) classifies site biomes and creates the starts we leave alone. Jungle temple =
`JungleTemplePlan` (surface `JungleTemple` with rooms, caches and the top shrine, plus an underground grown
by `StrongholdLayout` with `JungleKind.CATALOGUE`, built by `JungleTempleBuilder extends StrongholdBuilder`).
The idol vault is a record-level lock (`VaultLock`) released only by the sockets.

**Tech Stack:** Forge 1.12.2 (2847 dev), Java 8, JUnit 4, existing `StructureJobs`/`Blueprint` pipeline.

**Spec:** `docs/superpowers/specs/2026-11-23-jungle-temple-design.md`

## Global Constraints
- Build with `-Werror`; no deprecated `getStateFromMeta` (use `withProperty`).
- Tests call `TestBlocks.install()`; no world access inside `StructurePlan.build()`.
- Own code only, no YUNG code or structure files; no compile dependency on BOP, Mowzie's, Dynamic Trees,
  Mekanism or IC2 (reflection / registry names only).
- Overworld only (dimension 0). Swamp huts and igloos keep their generator's start and witch spawning.
- Releases: Phase 1 → 1.8.1, Phase 2 → 1.9.0; each: version in `gradle.properties` and `Reference.java`,
  README (ru), HANDOFF, `dist/shatteredcitadels-<ver>.jar` replacing the previous one, push.
- Every new config option and item/block has en_us and ru_ru lang lines.

## Review Focus
1. BOP world with BOP's `distance` option: our generator keeps BOP's spacing, so `/locate Temple` and the
   Eye-style search still hit real sites (pinned in Task 1: spacing copied from the original generator).
2. A world from 1.8.0/vanilla with jungle temples already started: they keep generating (legacy start
   in the footprint → no plan; pinned in Task 6 by reusing `hasLegacyStartIn`).
3. Vault gates after a restart with the vault still locked: `SealedGates.reconcile` must not open them
   (pinned in Task 3).
4. A jungle site on Dynamic Trees: the temple stands on the ground, not on trunks (pinned in Task 4).
5. Idols placed while some vault gates are in unloaded chunks: those gates open when loaded (pinned in
   Task 3 via reconcile after `vault.isOpen()`).

---

## Phase 1 — BOP support for desert temples (1.8.1)

### Task 1: Generator-agnostic `ScatteredFeatureGen` with vanilla and BOP site rules

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/temple/TempleSites.java`
- Create: `src/main/java/com/shatteredcitadels/worldgen/temple/VanillaTempleSites.java`
- Create: `src/main/java/com/shatteredcitadels/worldgen/temple/BopTempleSites.java`
- Modify: `src/main/java/com/shatteredcitadels/worldgen/temple/ScatteredFeatureGen.java`
- Modify: `src/main/java/com/shatteredcitadels/worldgen/temple/TempleReplacer.java`
- Test: `src/test/java/com/shatteredcitadels/worldgen/TempleSitesTest.java`

**Interfaces:**
- Produces:
  - `enum TempleSites.Kind { DESERT, JUNGLE, OTHER, NONE }`
  - `interface TempleSites { Kind kindOf(Biome b); StructureStart otherStart(World w, Random r, int cx, int cz); }`
  - `VanillaTempleSites.INSTANCE` — DESERT/DESERT_HILLS → DESERT, JUNGLE/JUNGLE_HILLS → JUNGLE,
    SWAMPLAND/ICE_PLAINS/COLD_TAIGA → OTHER, else NONE; `otherStart` = `new MapGenScatteredFeature.Start(w, r, cx, cz)`.
  - `BopTempleSites(List<Biome> desert, List<Biome> jungle, List<Biome> swamp, List<Biome> ice, StartFactory f)`
    with `static BopTempleSites from(MapGenScatteredFeature bopGen)` (reflection: private static lists
    `DESERT_BIOMES`, `JUNGLE_BIOMES`, `SWAMP_BIOMES`, `ICE_BIOMES`; start = `BOPMapGenScatteredFeature$Start(World, Random, int, int)`);
    `static int spacingOf(MapGenScatteredFeature bopGen)` (its private int `maxDistanceBetweenScatteredFeatures`).
  - `ScatteredFeatureGen(TempleSites sites, int spacing)`; `static boolean isOurs(Biome)` stays (vanilla DESERT kinds).
  - `ScatteredFeatureGen.handles(Kind kind)`: DESERT when `isReplaceDesertTemples()`; JUNGLE added in Task 6.

- [ ] **Step 1: Write failing tests** `TempleSitesTest`:
  - `vanillaKinds`: `VanillaTempleSites.INSTANCE.kindOf(Biomes.DESERT_HILLS) == DESERT`, `JUNGLE_HILLS == JUNGLE`,
    `SWAMPLAND == OTHER`, `COLD_TAIGA == OTHER`, `PLAINS == NONE`.
  - `bopKindsFromLists`: `new BopTempleSites(list(MESA), list(FOREST), list(SWAMPLAND), list(ICE_PLAINS), f)`
    gives MESA → DESERT, FOREST → JUNGLE, SWAMPLAND → OTHER, PLAINS → NONE.
  - `bopFromNonBopGeneratorFails`: `BopTempleSites.from(new MapGenScatteredFeature())` returns null (no lists).
- [ ] **Step 2:** `./gradlew test --tests '*TempleSitesTest*'` → FAIL (classes missing).
- [ ] **Step 3: Implement.** `ScatteredFeatureGen`:
  - Own grid (same math as vanilla/BOP: region = spacing, separation 8, salt 14357617, `world.setRandomSeed`) in
    `isGridSite(cx, cz)`; set vanilla's `field_82669_g` to `spacing` with `ObfuscationReflectionHelper` so
    `getNearestStructurePos` searches with the same spacing.
  - `canSpawnStructureAtCoords` = grid && kind != NONE && !(dimension 0 && handles(kind)).
  - `getStructureStart(cx, cz)` → `sites.otherStart(world, rand, cx, cz)`.
  - `isSwampHut(pos)`: start at pos non-null with first component `ComponentScatteredFeaturePieces.SwampHut`
    (BOP's start is not a vanilla `Start`).
  - `generateStructure`: as now, with `isOurSite` = grid && dimension 0 && handles(kind) for the site biome;
    DESERT → `DesertTemplePlan` (JUNGLE in Task 6).
  - `TempleReplacer`: exact `MapGenScatteredFeature` → `new ScatteredFeatureGen(VanillaTempleSites.INSTANCE, 32)`;
    class name `biomesoplenty.common.world.BOPMapGenScatteredFeature` → `BopTempleSites.from(gen)` and
    `spacingOf(gen)` (on any reflection failure: log a warning, keep BOP's generator); anything else: log, keep.
- [ ] **Step 4:** `./gradlew test` → all PASS.
- [ ] **Step 5:** Commit `feat: temple sites for vanilla and Biomes O' Plenty generators`.

### Task 2: Forced variants for the spawn command, release 1.8.1

**Files:**
- Modify: `command/CommandCitadel.java` (spawn: optional 3rd arg), `worldgen/temple/DesertTemplePlan.java`
- Test: `DesertTempleTest`

**Interfaces:**
- Produces: `DesertTemplePlan.plan(World, int cx, int cz, long seed, Boolean pharaoh)` (null = config chance);
  `/citadel spawn desert_temple [pharaoh|tomb]`; `planAndQueue(World, String kind, String variant, int cx, int cz)`
  (old signature delegates with null). Tab completion offers the variants after `desert_temple`.

- [ ] **Step 1:** Test `forcedVariantWins`: `DesertTemplePlan.variantFor(Boolean.TRUE, 0.0, rand)` is true,
  `variantFor(Boolean.FALSE, 1.0, rand)` false, `variantFor(null, 1.0, rand)` true.
- [ ] **Step 2:** Run → FAIL. **Step 3:** implement `static boolean variantFor(Boolean forced, double chance, Random r)`
  and use it in `plan`. **Step 4:** `./gradlew build` → PASS.
- [ ] **Step 5: Dev check:** vanilla desert world (`CUSTOMIZED`, `{"fixedBiome":2}`), `setworldspawn 8 80 8`
  restart, `citadel spawn desert_temple pharaoh`, `locate Temple` answers; log shows natural temple and 0 repaired.
  BOP cannot run in dev (Forge 2847) — the user checks BOP in the pack.
- [ ] **Step 6:** Release 1.8.1 (Global Constraints), commit `build: release 1.8.1`, push, verify the link.

## Phase 2 — Jungle temple (1.9.0)

### Task 3: Idol, socket, vault brick and the vault lock

**Files:**
- Create: `item/ItemJungleIdol.java`, `block/BlockIdolSocket.java` (property `FILLED`), `block/BlockVaultBrick.java`
  (hardness -1, resistance 6000000, looks like mossy stone bricks), `structure/VaultLock.java`
- Modify: `item/ModItems.java`, `block/ModBlocks.java`, `structure/CitadelInstance.java` (field `vault`, NBT key
  `"vault"`), `structure/SealedGates.java`, `structure/CitadelProtectionHandler.java`, lang, models, textures
  (16×16 own art: idol, socket empty/filled, vault brick = recoloured mossy brick).
- Test: `src/test/java/com/shatteredcitadels/structure/VaultLockTest.java`

**Interfaces:**
- Produces:
  - `VaultLock { static final int GATE_FLOOR = -100; List<BlockPos> sockets(); boolean place(BlockPos socket);
    boolean isOpen(); StructureBoundingBox box(); NBTTagCompound write(); static VaultLock read(NBTTagCompound) }`
    — `place` returns true only for an empty socket of this lock; `isOpen` when every socket is filled.
  - `CitadelInstance.setVault(VaultLock)`, `getVault()`.
  - Protection: `protects(citadel, pos)` is true inside `vault.box()` while `!vault.isOpen()`, regardless of
    conquest and parts.
  - Gates with `requiredFloor == VaultLock.GATE_FLOOR`: never opened by `open`, `openAll`, or `reconcile` unless
    the vault is open; `reconcile` opens them once it is.
  - `BlockIdolSocket.onBlockActivated` with an idol in hand: finds the citadel at pos, `vault.place(pos)`,
    shrinks the stack (not in creative), sets `FILLED`, marks data dirty, and when `isOpen()` opens the vault
    gates (`SealedGates.open(world, citadel, GATE_FLOOR)` path that bypasses the guard) and plays a sound.
  - `CitadelProtectionHandler`: `PlayerInteractEvent.RightClickBlock` with item registry name
    `mekanism:cardboardbox` on a protected position → cancelled.
- [ ] **Step 1:** Tests `VaultLockTest`: `placeOnlyOnceAndOnlyOwnSockets`, `opensWhenAllThreeFilled`,
  `roundTripKeepsFilledSockets` (via `CitadelInstance` NBT), `vaultProtectedUntilOpenEvenWhenConquered`,
  `vaultGatesIgnoredByConquestAndReconcileWhileLocked` (pure check through `SealedGates.opensOnConquest` and a
  new `static boolean SealedGates.mayOpen(CitadelInstance, GatePoint)`).
- [ ] **Step 2:** Run → FAIL. **Step 3:** implement. **Step 4:** `./gradlew build` → PASS.
- [ ] **Step 5:** Commit `feat: jungle idol, idol sockets, vault brick and vault lock`.

### Task 4: Jungle site sampling and levelling

**Files:**
- Create: `worldgen/temple/JungleSite.java`
- Test: `src/test/java/com/shatteredcitadels/worldgen/JungleSiteTest.java`

**Interfaces:**
- Produces:
  - `interface JungleSite.Column { IBlockState at(int x, int y, int z); }` (world adapter; tests use a map).
  - `static int groundY(Column c, int x, int z, int top)` — scans down from `top`, skipping air, liquids
    (returns -1 marker for water), and materials WOOD, LEAVES, PLANTS, VINE, GOURD, CACTUS, WEB; any other solid
    block (including `dynamictrees:rootydirt`) is ground.
  - `static Integer siteY(Column c, int cx, int cz, int half)` — samples a 4-block grid over ±half; null if more than
    1/8 water or max−min > 12; else the median.
- [ ] **Step 1:** Tests: `skipsTrunksBranchesAndLeaves` (column of log/leaves over dirt → dirt's y; a block named like
  Dynamic Trees branch modelled by `Blocks.LOG` material WOOD), `rootyDirtIsGround` (stone-like solid
  non-wood block counts), `waterSiteRejected`, `steepSiteRejected` (13 difference), `medianOfGentleSlope`.
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS. **Step 5:** Commit `feat: jungle site sampling`.

### Task 5: Jungle temple builders (surface, underground, both endings)

**Files:**
- Create: `worldgen/temple/JunglePalette.java` (BlockSet: mossy cobble, mossy/cracked/plain stone bricks, chiseled
  accent, jungle log pillar), `worldgen/temple/JungleKind.java` (PieceSpec + `CATALOGUE`),
  `worldgen/temple/JungleTempleBuilder.java` (underground rooms, vault or storeroom goal, one cache),
  `worldgen/temple/JungleTemple.java` (surface temple)
- Test: `src/test/java/com/shatteredcitadels/worldgen/JungleTempleTest.java`

**Interfaces:**
- Consumes: `VaultLock`, `ModBlocks.IDOL_SOCKET`, `ModBlocks.VAULT_BRICK`, `ModItems.JUNGLE_IDOL`, `StrongholdBuilder`
  protected helpers, `StructurePieces.chest`.
- Produces:
  - `JungleKind`: `ENTRY_HALL` (start), `GOAL` (vault/storeroom, deepest), `CISTERN`, `SPIDER_DEN`, `STOREROOM`,
    `PIT_HALL`; `CATALOGUE = new Catalogue(ENTRY_HALL, GOAL, [CORRIDOR, TURN, T_JUNCTION, STAIRS_DOWN] + rooms,
    30, 12, 20, 8)`.
  - `JungleTempleBuilder(BlockWriter, CitadelInstance, Random, boolean boss)`; `build(layout)`; in the vault
    variant the GOAL room is the vault: shell of `VAULT_BRICK`, sealed gate door (`GATE_FLOOR`), 3 sockets
    beside the door, chests (`CHESTS_JUNGLE_TEMPLE`), gold and emerald blocks; sets `rec.setVault(lock)`.
    In the boss variant the GOAL room is a storeroom with chests.
  - `JungleTemple(BlockWriter, Random, int cx, int sy, int cz, boolean boss, CitadelInstance rec)`; `build()`:
    3 stepped tiers (base half 15–18), front stair to the entry on tier 2, main hall, 3–5 rooms, 2 caches,
    vines/leaves, top shrine reaching about sy+30; clears columns in footprint + 4 ring; foundation `fillDown`
    not under the shaft. Boss variant: shrine = arena (`addFloor(0,…,true)`, `setBossArena`, altar,
    `setPreferredBoss(mowziesmobs:barako)`, reward chest sealed with requiredFloor 0, `addPart(shrine)`).
  - Caches: `cache(BlockPos, Kind)` with `FALSE_WALL` (a cracked brick with a torch on the wall opposite),
    `UNDER_FLOOR` (a chiseled tile over a one-block hole), `BEHIND_PAINTING` (a painting over a 1×2 niche);
    each holds a chest with an idol (vault variant) plus loot.
- [ ] **Step 1:** Tests `JungleTempleTest`:
  - `exactlyOneGoal` and pieces within ±30 for seeds 1..100;
  - `shellIsClosed` (as `DesertTempleTest`) for the underground;
  - `vaultVariantHasThreeIdolsAndThreeSockets`: idol items in cache chests' scheduled contents == 3 (count via
    a test hook `JungleTempleBuilder.idolCaches()` / `JungleTemple.idolCaches()` totals 3), sockets == 3,
    `rec.getVault().sockets().size() == 3`, vault box made of `VAULT_BRICK` on all six faces except gate blocks;
  - `bossVariantHasNoVaultAndPrefersBarako`: `rec.getVault() == null`, preferred boss `mowziesmobs:barako`,
    arena above `sy + 20` (open to the sky: no planned solid block above the arena centre);
  - `foundationFillsNeverPourIntoOpenings` (as in `DesertTempleTest`);
  - `tombChestsCanOpen`-style: no chest has a solid block on top.
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS. **Step 5:** Commit
  `feat: jungle temple surface, underground, vault and shrine`.

### Task 6: Plan, generator hookup, config, command, loot, names

**Files:**
- Create: `worldgen/temple/JungleTemplePlan.java` (KIND `jungle_temple`; NBT `x y z seed dim boss`)
- Modify: `ScatteredFeatureGen` (JUNGLE → plan with `JungleSite.siteY`; skip site when null), `StructurePlans`,
  `ModConfig`/`ConfigCache` (`replaceJungleTemples` true, `jungleTempleBossChance` 0.4,
  `jungleTempleSpawnerMobs` {minecraft:zombie, minecraft:skeleton, minecraft:cave_spider}), `CommandCitadel`
  (`jungle_temple [boss|vault]`), `ModLootInjector` (`CHESTS_JUNGLE_TEMPLE` modded pool), lang (config, zone
  `barako_shrine`, names group `jungle`: Temple/Ziggurat/Shrine/Sanctum/Altar/Pyramid and ru), `StructureType.JUNGLE_TEMPLE`.
- Test: `JungleTempleTest`
- [ ] **Step 1:** Tests `planRoundTrip` (NBT equal after `StructurePlans.read`, type JUNGLE_TEMPLE, name
  `@name:jungle:`), `jungleSitesHandledOnlyWhenEnabled` (`ScatteredFeatureGen.handles(JUNGLE)` follows config).
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** `./gradlew build` PASS. **Step 5:** Commit
  `feat: jungle temple plan and generator hookup`.

### Task 7: Dev check, review, release 1.9.0
- [ ] `CUSTOMIZED` `{"fixedBiome":21}` world: natural jungle temple logged and written with 0 repaired;
  `citadel spawn jungle_temple vault` and `boss`; render top and slices; check shaft and vault blocks from
  region files.
- [ ] Final review by a fresh reviewer; fix Critical/Important with a test first.
- [ ] Release 1.9.0 (Global Constraints), push, verify link; report to the user in Russian with what to check in
  the pack (BOP jungles, Dynamic Trees ground, Barako's sun strike, idols, cardboard box).
