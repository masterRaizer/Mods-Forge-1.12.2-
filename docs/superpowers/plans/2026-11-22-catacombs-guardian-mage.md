# Catacombs and Guardian Mage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wide multi-level catacombs under towers, castle keeps and manors, with a guardian mage
boss and his sanctum, new staff and relics, and catacomb fragments in place of vanilla spawner
dungeons.

**Architecture:**
- A pure layout class (`CatacombLayout`) decides halls, maze and loops on a 5-block cell grid.
- `CatacombBuilder` writes the layout into the existing `Blueprint` through `BlockWriter`. It
  registers floors, gates, spawners and the sanctum, and is called by `CitadelPlan`, `ManorPlan`
  and the fragment plans.
- The mage is a new `EntityMob`. It is spawned once by a hidden sigil tile in the sanctum, and its
  death opens the reward gates.

**Tech Stack:** Forge 1.12.2 (RetroFuturaGradle, `-Werror`), JUnit 4, existing
`StructurePlan`/`Blueprint`/`StructureJobs` generator.

**Spec:** `docs/superpowers/specs/2026-11-22-catacombs-guardian-mage-design.md`

## Global Constraints
- **Generation path:** everything goes through `StructurePlan` → `Blueprint` → `StructureJobs`.
  Generation code never loads chunks or touches the `World`.
- **Determinism:** a plan rebuilt from NBT produces the same blueprint.
- **Build:** `./gradlew build` passes, with javac `-Werror` and all tests.
- **Levels:**
  - 2-3 levels, each about 100x100, centred under the structure.
  - Level 0 sits about 10 blocks under the base.
  - Level floors stay ≥ y 6 (`BlockWriter.MIN_Y + 1`).
  - Cell grid 5 blocks; corridors 3 wide and 4 high; 1-block shell around every carved space.
- **Floor indices:**
  - Levels -1, -2, -3. Level k+1 `requiresFloor` level k. Level tier = base tier + 1 + level.
  - Mage sanctum -10, with an area. It `requiresFloor` the deepest level and has zone key
    `mage_sanctum`. Level zone keys are `catacombs_level_N` (N from 1).
- **Stairs:** the entrance spiral is never sealed; the stair to the next level is a sealed gate.
- **Mage:**
  - about 300 HP, armour 6;
  - stays 8-12 blocks away;
  - burst of 3 small fireballs;
  - slowing bolt with Slowness II for 4 s;
  - lightning after a 1.5 s warning;
  - teleports after 2 s in melee range and every 8-12 s otherwise;
  - summons 3 guards, at most 6 alive;
  - one shield phase below 50 % with 4 guards;
  - leashed to the sanctum area; no despawn.
- **Staff:** small fireball for about 6 damage, sets fire, 60-tick cooldown, 250 durability,
  repaired with blaze rods.
- **Folio:** TRINKET slot; staff cooldown -25 %, magic damage taken -20 %.
- **Ring of Ashen Sight:** RING slot; Night Vision below y 40 or in darkness; undead deal -15 %.
- **Chances (config):**
  - catacombs under towers 0.5, under castles and manors 0.8;
  - fragment: one per 10x10-chunk region at 0.6, y 12-40, never within 2 chunks of a footprint;
  - vanilla dungeons cancelled in the Overworld (toggle, default on).
- **Conquest:** the surface boss only. The sanctum floor never makes `isConquered()` true.
- **Commits:** end with the session's Co-Authored-By / Claude-Session lines. No model names in
  commits or code.

## Review Focus
1. **Sanctum floor auto-clear.** It has no spawners, so neither `openUnguardedFloors` nor
   `FloorCombatState` may mark it cleared, or the pedestal gates would open before the mage dies.
   Test in Task 1 and Task 4.
2. **Ground below y≈40** (deep ocean, low base): fewer levels are built and no level goes below
   y 6. If even one level does not fit, there are no catacombs and the plan stays valid.
   Test in Task 3.
3. **Server restart mid-fight or before the first visit.** The mage must not respawn after its
   death or duplicate after a reload. The sigil persists `spawned` and the mage's UUID, and a mage
   already alive in the area counts. Test in Task 8.
4. **Catacombs meet caves, ravines or mineshafts.** The shell keeps liquids out, and a dig into the
   catacomb parts from outside is blocked by protection until conquest. Fragments (no boss) are not
   protected, so they can always be mined, like vanilla dungeons. Test in Task 5.
5. **Two structures close together.** The footprints, including the catacomb area, must reserve
   space so two catacombs never intersect. Test in Task 4.

---

### Task 1: Side-boss floors do not conquer a structure

**Files:**
- Modify: `src/main/java/com/shatteredcitadels/structure/FloorData.java` (field, getter/setter, NBT
  key `sideBoss`)
- Modify: `src/main/java/com/shatteredcitadels/structure/CitadelInstance.java:127` (`isConquered`)
- Modify: `src/main/java/com/shatteredcitadels/worldgen/CitadelWorldGenerator.java:224`
  (`openUnguardedFloors` already skips boss floors; the sanctum is registered as a boss floor)
- Test: `src/test/java/com/shatteredcitadels/structure/CitadelPersistenceTest.java`

**Interfaces:**
- Produces: `FloorData setSideBoss(boolean)`, `boolean isSideBoss()`, saved in NBT.
  `isConquered()` ignores floors with `isSideBoss()`.

- [ ] **Step 1: Write the failing test** `sideBossFloorDoesNotConquer`:
  - Floor -10 with `bossFloor=true`, `setSideBoss(true)`, `setBossDefeated(true)` gives
    `assertFalse(rec.isConquered())`.
  - After a write/read NBT round trip, `isSideBoss()` is still true.
  - Floor 3 with `bossDefeated` gives `assertTrue(rec.isConquered())`.
- [ ] **Step 2: Run it.** Run `./gradlew test --tests '*CitadelPersistenceTest*'`. Expected:
  FAIL (no `setSideBoss`).
- [ ] **Step 3: Implement** the flag and the `isConquered` exclusion.
- [ ] **Step 4: Run it.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat: side-boss floors that do not conquer a structure`.

### Task 2: Catacomb layout (pure grid logic)

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/catacomb/CatacombLayout.java`
- Test: `src/test/java/com/shatteredcitadels/worldgen/CatacombLayoutTest.java`

**Interfaces:**
- Produces:
  - `enum HallKind { CRYPT, SPIDER_CAVE, SKELETON_HALL, STOREROOM, GUARD_POST, SANCTUM, STAIR }`
  - `final class Hall { int kind; int ci0, cj0, ci1, cj1; }` (cell rectangle, inclusive), with
    `HallKind kind()`.
  - `static CatacombLayout generate(long seed, int cells, int stairCi, int stairCj, boolean sanctum, boolean downStair)`.
    - `cells`: cells per side, odd (≈ 19 for 95 blocks; 7 for a fragment).
    - `sanctum`: whether this level gets the sanctum.
    - `downStair`: whether this level gets a stair hall to the next level.
  - Accessors:
    - `boolean open(int ci, int cj)` — the cell is carved (corridor or hall);
    - `boolean link(int ci, int cj, EnumFacing dir)` — passage to the neighbour;
    - `List<Hall> halls()`, `Hall entrance()`, `Hall sanctum()` (null if none), `Hall downStair()`
      (null if none).
- Algorithm:
  - Place the entrance hall at the stair cell (1x1 cell; the spiral lives here).
  - Place 2-4 themed halls of 2x2 to 4x3 cells with a gap of ≥ 1 cell, no overlap.
  - If `sanctum`: a 5x5-cell sanctum in the quadrant farthest from the entrance.
  - If `downStair`: a 1x1 stair hall, at least `cells/3` away from the entrance.
  - Then a randomized DFS maze over the remaining cells.
  - Then one extra link per ~12 cells to make loops.
  - Each hall opens to the maze through 1-2 links; the sanctum through exactly 1.

- [ ] **Step 1: Write failing tests:**
  - `everyHallReachableFromEntrance`: BFS over `link` from `entrance()` reaches every hall's cells,
    for seeds 1..50, `cells=19`, `sanctum=true`, `downStair=true`.
  - `sanctumHasSingleDoorAndIsFarthestQuadrant`: exactly one link crosses the sanctum border, and
    the sanctum centre lies in the quadrant opposite the entrance.
  - `deterministic`: two `generate` calls with the same arguments give equal `open`/`link` grids.
  - `hasLoops`: number of links > number of open cells − 1, for seeds 1..20.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*CatacombLayoutTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement `CatacombLayout`.** Use only `java.util.Random(seed)` and plain arrays,
  with no Minecraft world access.
- [ ] **Step 4: Run them.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat: catacomb cell layout with halls, maze and loops`.

### Task 3: Catacomb builder — corridors, shell, dressing, stairs, floors, gates

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/catacomb/CatacombBuilder.java`
- Create: `src/main/java/com/shatteredcitadels/worldgen/catacomb/CatacombPalette.java`
  - Mossy, cracked and plain stone bricks, cobblestone, andesite, bone block.
  - Optional modded stones come through `ModPaletteRegistry` keys, resolved like other palettes.
- Test: `src/test/java/com/shatteredcitadels/worldgen/CatacombBuilderTest.java`

**Interfaces:**
- Consumes: `CatacombLayout.generate(...)` (Task 2); `BlockWriter`; `StructurePieces.spawner`,
  `StructurePieces.chest`; `CitadelInstance.addFloor/addGate/addPart/addRoom`; `BlockVase`
  variants; `RoomFurnisher`.
- Produces:
  - `CatacombBuilder(BlockWriter w, CitadelInstance rec, BlockPalette surface, Random rand, int baseTier)`
  - `int build(int centreX, int centreZ, int topLevelY, int levels, int entranceX, int entranceZ, int entranceTopY)`
    - Returns the number of levels built: 0 when even one level does not fit above y 6.
    - Builds a spiral stair at `(entranceX, entranceZ)` from `entranceTopY` down to level 0.
    - Each level spans `cells = 19` (95 blocks) and is centred at `(centreX, centreZ)`.
    - Level y step `LEVEL_STEP = 10`; corridors are 4 high.
    - Registers floors -1..-N: zone key `catacombs_level_N`; `requiresFloor` chain, level -1
      `Integer.MIN_VALUE`; tier = `baseTier + 1 + k`.
    - Places 1-2 wave spawners per themed hall and 1 per ~20 corridor cells.
    - Places the down-stair hall with a sealed gate that needs the current level.
    - Calls `rec.addPart` for the whole catacomb box.
  - `static int footprintRadius()` = 49 (half the size, plus the shell) for planners.
  - Hook for Task 4: `protected void buildHall(CatacombLayout.Hall hall, int level, int y)`.
    Themed halls are a plain carved room until Task 4.
- Notes:
  - Carving sets AIR explicitly, so it overwrites stone underground.
  - The shell is written only where the blueprint has no planned state yet, so it never covers a
    corridor of the same catacomb.
  - Dressing per corridor cell, with probabilities as the builder's call (`CatacombBuilder`
    constants): bone niche, skull, cobweb, gravel cave-in pile (never blocking: at most 1 block
    high, only at the sides), torch about 1 in 6 cells.

- [ ] **Step 1: Write failing tests** (with `Bootstrap.register()` as in `BlueprintTest`):
  - `buildsThreeLevelsWithChainedFloors`:
    - `topLevelY=54`, `levels=3`;
    - floors -1, -2, -3 exist; `getFloor(-2).getRequiresFloor() == -1`;
    - zone keys are `catacombs_level_1..3`.
  - `stopsAboveWorldBottom`:
    - `topLevelY=20`, `levels=3` builds 1 level (20 − 10·k ≥ 6);
    - `topLevelY=5` returns 0 and registers no floors.
  - `spiralConnectsEntranceToLevelZero`: every y from `entranceTopY-1` down to the level-0 floor
    + 1 has AIR or a stair/slab block inside the 5x5 stair box at the spiral path. Check by
    walking the path that `CatacombBuilder.spiralPath()` returns (make it package-visible).
  - `downStairIsGated`: `rec.getGates()` contains a gate with `requiredFloor == -1` and one with
    `-2` when 3 levels are built.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*CatacombBuilderTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `CatacombPalette` and `CatacombBuilder` (without halls content and
  sanctum).
- [ ] **Step 4: Run them.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat: catacomb levels with spiral entrance, gated stairs and dressing`.

### Task 4: Themed halls, sanctum, pedestals, structure integration

**Files:**
- Modify: `src/main/java/com/shatteredcitadels/worldgen/catacomb/CatacombBuilder.java` (`buildHall`
  per `HallKind`, `buildSanctum`)
- Create: `src/main/java/com/shatteredcitadels/block/BlockMageSigil.java`
- Create: `src/main/java/com/shatteredcitadels/tile/TileEntityMageSigil.java`. Logic comes in Task
  8; here only the fields, NBT and a `configure(...)` method.
- Modify: `src/main/java/com/shatteredcitadels/block/ModBlocks.java` (register `mage_sigil`, its
  tile entity, item model, lang)
- Modify: `src/main/java/com/shatteredcitadels/worldgen/CitadelPlan.java` (`cryptLevels` now means
  catacomb levels 2-3; `buildInto` calls `CatacombBuilder` instead of `DungeonCryptBuilder`;
  `footprint()` covers `±CatacombBuilder.footprintRadius()` when levels > 0; entrance from
  `CitadelBuilder.shaftX/shaftZ`, top = `floorY(0)`; sky citadels never get catacombs)
- Modify: `src/main/java/com/shatteredcitadels/worldgen/CitadelBuilder.java` (ground-floor opening
  for a 5x5 spiral instead of the ladder shaft)
- Modify: `src/main/java/com/shatteredcitadels/worldgen/ManorPlan.java`,
  `ManorPlanner.java`, `ManorBuilder.java`:
  - new `catacombLevels` field in NBT, default 0 when the key is absent;
  - entrance from the cellar;
  - centre under the main block;
  - footprint grows accordingly.
- Modify: `src/main/java/com/shatteredcitadels/worldgen/CitadelWorldGenerator.java:173` (roll
  catacombs with the new chances: tower 0.5, castle keep and manor 0.8)
- Modify: `src/main/java/com/shatteredcitadels/config/ModConfig.java` and `ConfigCache.java`:
  `catacombTowerChance` 0.5, `catacombCastleChance` 0.8, `catacombManorChance` 0.8,
  `catacombMinLevels` 2, `catacombMaxLevels` 3. These replace `undergroundUnderCastleChance`,
  `cryptMinLevels` and `cryptMaxLevels`, with the old keys still read as a fallback.
- Delete: `src/main/java/com/shatteredcitadels/worldgen/DungeonCryptBuilder.java`, once nothing
  uses it after Task 5.
- Test: `CatacombBuilderTest`, `BlueprintTest`, `TowerPlanningTest`

**Interfaces:**
- Consumes: Task 1 `setSideBoss`; Task 3 builder.
- Produces:
  - Sanctum floor `-10`: `bossFloor=true`, `sideBoss=true`, `totalWaves=0`, area set, zone key
    `mage_sanctum`, `requiresFloor` = deepest level.
  - The sanctum entrance gate needs the deepest level.
  - Pedestal gate rings need floor `-10`.
  - Sigil tile at the dais centre, one block under the floor, through `afterPlace`:
    `TileEntityMageSigil.configure(UUID structureId, int floorIndex, StructureBoundingBox area, List<BlockPos> anchors)`.
    Anchors are 6-8 open floor positions next to bookshelf stacks.
  - Pedestal chests use `ModLootTables.MAGE_PEDESTAL` (Task 6 adds the table; until then reference
    the id constant added here). One pedestal is flagged to get the Folio guarantee by using
    `ModLootTables.MAGE_PEDESTAL_FOLIO`.

- [ ] **Step 1: Write failing tests:**
  - `sanctumRegisteredAsSideBoss`:
    - floor -10 `isBossFloor() && isSideBoss()`, `hasArea()`, `getRequiresFloor()==-3`;
    - after `CitadelWorldGenerator.openUnguardedFloors(writer, rec)`, `getFloor(-10).isCleared()` is
      false and the pedestal gates (requiredFloor -10) are still in `rec.getGates()`.
  - `hallsHaveContent`: the blueprint contains ≥ 1 web per spider cave hall, and ≥ 1 bone block in
    a skeleton hall. Read the hall positions from `layout.halls()` through a package-visible
    getter.
  - In `BlueprintTest`:
    - `manorWithCatacombsRegistersLevels`: a manor plan with `catacombLevels=2` builds floors -1,
      -2, -10 next to zones 0..7, and its footprint contains the catacomb box.
    - `plansRoundTripCatacombLevels`: CitadelPlan and ManorPlan NBT round trips keep the levels.
  - `TowerPlanningTest.footprintsOfNeighbouringCatacombsDoNotIntersect`: two planned towers with
    catacombs whose surface footprints are 7 chunks apart have catacomb footprints that report
    `intersectsWith` only if they really overlap. `isAreaFree` uses the widened footprint.
- [ ] **Step 2: Run them.** Run
  `./gradlew test --tests '*CatacombBuilderTest*' --tests '*BlueprintTest*' --tests '*TowerPlanningTest*'`.
  Expected: FAIL.
- [ ] **Step 3: Implement:**
  - the halls:
    - crypt: sarcophagi made of slab and stair pairs, wall tombs as 1x2 recesses with bone
      blocks and skulls;
    - spider cave: carved ellipsoid with noise ±1, webs on 25 % of the air ceiling-adjacent
      blocks, clay vases as egg clutches;
    - skeleton hall: double colonnade, tomb rows, ossuary wall of bone blocks;
    - storeroom / guard post: through `RoomFurnisher`;
  - the sanctum:
    - 27x27, floor dug one `LEVEL_STEP` lower;
    - bookshelf walls and 8 freestanding stacks;
    - 2 enchanting tables, 2 brewing stands, cauldrons, a raised 7x7 dais, palette light blocks;
    - 4-6 pedestals: quartz pillar plus chest plus a ring of sealed gates;
    - chests on the deepest level use `ModLootTables.CATACOMB_DEEP`, other catacomb chests `UNDERGROUND_CRYPT`;
    - the sigil;
  - the plan integration and the config listed above.
- [ ] **Step 4: Run them.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 5: Commit.** Message: `feat: catacomb halls, mage sanctum and catacombs under towers, keeps and manors`.

### Task 5: Fragments and vanilla dungeon replacement

**Files:**
- Modify: `src/main/java/com/shatteredcitadels/worldgen/CryptPlan.java` (kind `crypt` kept for old
  saves). It becomes the shrine fragment:
  - one level, `cells=7`, one themed hall plus a boss altar in that hall as before;
  - surface shrine (reuse the ruin code moved from `DungeonCryptBuilder.buildSurfaceRuin` into
    `CatacombBuilder.buildSurfaceShrine`);
  - spiral from the shrine.
- Create: `src/main/java/com/shatteredcitadels/worldgen/catacomb/FragmentPlan.java`. Kind
  `fragment`; buried; one level, `cells=7`; one hall; no entrance, no boss, **no `addPart`**
  (minable).
- Modify: `src/main/java/com/shatteredcitadels/worldgen/StructurePlans.java` (dispatch `fragment`)
- Create: `src/main/java/com/shatteredcitadels/worldgen/catacomb/VanillaDungeonReplacer.java`
  (`@Mod.EventBusSubscriber`):
  - cancels `PopulateChunkEvent.Populate` with `EventType.DUNGEON` in dim 0 when
    `replaceVanillaDungeons`;
  - on `PopulateChunkEvent.Post`, for the region grid cell's chosen chunk, plans a
    `FragmentPlan` (y from `12 + rand.nextInt(29)`) and submits it if
    `CitadelWorldGenerator.isAreaFree` holds on the footprint grown by 32 blocks.
- Modify: `ModConfig`/`ConfigCache`: `replaceVanillaDungeons` true, `fragmentRegionChunks` 10,
  `fragmentChance` 0.6.
- Modify: `src/main/java/com/shatteredcitadels/command/CommandCitadel.java` (kind `fragment` for
  testing; `crypt`/`dungeon` builds the shrine fragment)
- Test: `BlueprintTest`

**Interfaces:**
- Consumes: Task 3/4 `CatacombBuilder`. Add
  `int buildFragment(int centreX, int centreZ, int levelY, boolean shrine, int surfaceY)`, which
  registers floor -1 only, plus the boss altar floor when `shrine`.
- Produces: `FragmentPlan(int centreX, int centreZ, int levelY, long seed, int dimension)`,
  `static FragmentPlan read(NBTTagCompound)`.

- [ ] **Step 1: Write failing tests:**
  - `fragmentRoundTripAndNoParts`: a FragmentPlan NBT round trip gives an equal `describe()`;
    `build` gives a record with floor -1, spawner points ≥ 1, and `getParts()` empty.
  - `shrineFragmentHasAltarAndPart`: a CryptPlan build has a boss floor and ≥ 1 part.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*BlueprintTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Then delete `DungeonCryptBuilder.java` and move its helpers that are
  still needed (traps, dispenser fill) into `CatacombBuilder`.
- [ ] **Step 4: Run it.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: `feat: catacomb fragments replace vanilla spawner dungeons and standalone crypts`.

### Task 6: Staff, Folio, Ring, loot tables, modded pool

**Files:**
- Create: `src/main/java/com/shatteredcitadels/item/ItemGuardianStaff.java`
- Modify: `src/main/java/com/shatteredcitadels/item/RelicKind.java`
  (`GUARDIAN_FOLIO("TRINKET")`, `ASHEN_SIGHT_RING("RING")`)
- Modify: `src/main/java/com/shatteredcitadels/item/RelicEffects.java`. These are the existing
  equip checks; add:
  - the folio cooldown factor;
  - magic damage reduction in `LivingHurtEvent` when `source.isMagicDamage()`;
  - ring night vision (refresh 220 ticks) when `posY < 40` or light < 5;
  - the undead reduction when the attacker is `EnumCreatureAttribute.UNDEAD`.
- Modify: `src/main/java/com/shatteredcitadels/item/ModItems.java` (ids `guardian_staff`,
  `guardian_folio`, `ashen_sight_ring`)
- Create: textures `assets/shatteredcitadels/textures/items/{guardian_staff,guardian_folio,ashen_sight_ring}.png`
  (16x16, drawn with a Python PIL script under the scratchpad, same style as the existing items),
  item models, and `en_us.lang`/`ru_ru.lang` entries.
- Modify: `src/main/java/com/shatteredcitadels/loot/ModLootTables.java`: `MAGE_PEDESTAL`,
  `MAGE_PEDESTAL_FOLIO`, `CATACOMB_DEEP`.
- Create: loot JSONs `chests/mage_pedestal.json`, `chests/mage_pedestal_folio.json`,
  `chests/catacomb_deep.json`.
- Modify: `src/main/java/com/shatteredcitadels/loot/ModLootInjector.java`: a modded pool on
  `MAGE_PEDESTAL*` and `CATACOMB_DEEP`.
  - Candidates are taken from the namespaces `artifacts`, `xat`, `mowziesmobs`, `gobber2`,
    `ironapples`.
  - The exact ids are confirmed from the user's pack log before release. Unknown ids are skipped
    and logged once.
- Test: `src/test/java/com/shatteredcitadels/...LootTableItemsTest`, extended (the existing test
  that checks every item id in loot JSON resolves).

**Interfaces:**
- Produces:
  - `ItemGuardianStaff.COOLDOWN_TICKS = 60`; `static int cooldownFor(EntityPlayer)`, which applies
    the folio -25 % → 45.
  - Staff fireball: `EntitySmallFireball` with the owner set, damage about 6 via a small subclass
    `EntityStaffFireball` (sets fire like vanilla).
  - Repair item: `Items.BLAZE_ROD`.

- [ ] **Step 1: Write failing tests:**
  - `LootTableItemsTest` covers the three new tables (every vanilla/mod-own item id resolves; the
    folio table always yields `shatteredcitadels:guardian_folio`).
  - `GuardianStaffTest.cooldownWithFolio`: `ItemGuardianStaff.cooldownFor(60, true) == 45` and
    `cooldownFor(60, false) == 60`. Expose `static int cooldownFor(int base, boolean folio)` for
    the test.
- [ ] **Step 2: Run them.** Run
  `./gradlew test --tests '*LootTableItemsTest*' --tests '*GuardianStaffTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the items, effects, textures, models, lang, tables and injector pool.
- [ ] **Step 4: Run it.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: `feat: guardian staff, folio and ring of ashen sight, mage pedestal loot`.

### Task 7: Guardian Mage entity

**Files:**
- Create: `src/main/java/com/shatteredcitadels/entity/EntityGuardianMage.java`
  - `EntityMob`, `BossInfoServer` bar, `enablePersistence()`, `isNonBoss()=false`, fall immune.
  - Persists `anchors`, `area`, `structureId`, `floorIndex`, `shieldUsed`, `shieldGuards`
    (UUIDs).
  - `setHomeArea(StructureBoundingBox, List<BlockPos>)`.
- Create: `src/main/java/com/shatteredcitadels/entity/MageSpellCycle.java` (pure: picks the next
  spell, tracks cooldowns in ticks; unit-testable)
- Create: `src/main/java/com/shatteredcitadels/entity/ai/EntityAIMageKite.java` (keeps 8-12 blocks,
  strafes), `EntityAIMageCast.java` (runs `MageSpellCycle`)
- Create: `src/main/java/com/shatteredcitadels/entity/EntityMageBolt.java`
  (`EntityThrowable`, no gravity, Slowness II 80 ticks, 2 magic damage, witch particles; rendered
  invisible plus particles)
- Create: `src/main/java/com/shatteredcitadels/entity/ModEntities.java`
  - Registers `guardian_mage` (tracking 64, update 3) and `mage_bolt` with `EntityEntryBuilder`.
  - A spawn egg for `guardian_mage`.
- Create (client):
  - `src/main/java/com/shatteredcitadels/client/render/ModelGuardianMage.java` (`ModelBiped` plus a
    robe skirt box and a hood);
  - `RenderGuardianMage.java`; an invisible renderer for the bolt;
  - registered in the client proxy `preInit` through `RenderingRegistry`;
  - texture `textures/entity/guardian_mage.png` (64x64, PIL script);
  - lang names.
- Modify: `ModConfig`/`ConfigCache`: `mageHealth` 300, `mageArmor` 6, `mageFireballDamage` 5,
  `mageLightningWarningTicks` 30, `mageTeleportMinTicks` 160 / `mageTeleportMaxTicks` 240,
  `mageMaxGuards` 6.
- Test: `src/test/java/com/shatteredcitadels/entity/MageSpellCycleTest.java`

**Interfaces:**
- Consumes: Task 4 sigil data shape (area plus anchors).
- Produces:
  - `EntityGuardianMage(World)`;
  - `void setHomeArea(StructureBoundingBox area, List<BlockPos> anchors, UUID structureId, int floorIndex)`;
  - `boolean isShielded()`.
  - On death (`onDeath`, server): drops the staff and calls
    `MageSanctum.onMageDefeated(WorldServer, UUID structureId, int floorIndex)` (Task 8).
- `MageSpellCycle` API:
  - `enum Spell { FIREBALLS, SLOW_BOLT, LIGHTNING, SUMMON }`;
  - `Spell next(Random r, int aliveGuards, int maxGuards)`, which never returns SUMMON when
    `aliveGuards + 3 > maxGuards`;
  - `int cooldownAfter(Spell s)`: FIREBALLS 50, SLOW_BOLT 40, LIGHTNING 70, SUMMON 200;
  - `boolean shouldTeleport(int ticksInMelee, int ticksSinceTeleport, int nextTeleportAt)`:
    true when `ticksInMelee >= 40 || ticksSinceTeleport >= nextTeleportAt`.
- Shield: at `getHealth() < getMaxHealth()/2 && !shieldUsed` the mage summons 4 guards. Every
  damage source is cancelled while any shield guard lives. The aura is shown with
  `EnumParticleTypes.SPELL_WITCH` rings. Guards are `EntitySkeleton` with an iron sword and leather
  armour, tagged with `BossTags` so they do not count as floor wave mobs.
  Leash: every 20 ticks, if the mage is outside `area`, it teleports to the anchor nearest the area centre.

- [ ] **Step 1: Write failing tests** in `MageSpellCycleTest`:
  - `noSummonOverCap`: with `aliveGuards=4, maxGuards=6`, 1000 draws never return SUMMON.
  - `cooldownsMatchSpec`: the four values above.
  - `teleportRules`: `(40,0,200)` gives true; `(39,199,200)` gives false; `(0,200,200)` gives true.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*MageSpellCycleTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the entity, AI, bolt, registration, model, renderer, texture and config.
- [ ] **Step 4: Run it.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: `feat: guardian mage boss entity with spells, teleports, guards and shield phase`.

### Task 8: Sanctum logic — sigil spawning, defeat, gates, banner

**Files:**
- Modify: `src/main/java/com/shatteredcitadels/tile/TileEntityMageSigil.java` (`ITickable`, every
  20 ticks on the server)
- Create: `src/main/java/com/shatteredcitadels/structure/MageSanctum.java`. Static
  `onMageDefeated`:
  - marks floor `cleared` and `bossDefeated`;
  - `CitadelWorldSavedData.update`;
  - `SealedGates.open(world, rec, floorIndex)`;
  - sends a title to the players in the area.
- Modify: `src/main/resources/assets/shatteredcitadels/lang/*.lang`:
  `shatteredcitadels.mage.title`, `.subtitle`, `.defeated`; zone keys `catacombs_level_N`,
  `mage_sanctum` for the HUD (`CitadelTextFormatter`).
- Test: `src/test/java/com/shatteredcitadels/tile/MageSigilTest.java`

**Interfaces:**
- Consumes: Task 4 `configure(...)`; Task 7 `EntityGuardianMage.setHomeArea`; Task 1 flags.
- Produces: `static boolean shouldSpawn(boolean spawned, boolean unlocked, boolean bossDefeated, boolean playerInside, boolean mageAlive)`.
  It is true only when all of these hold:
  - `!spawned`;
  - `unlocked`;
  - `!bossDefeated`;
  - `playerInside`;
  - `!mageAlive`.
- Behaviour:
  - When `shouldSpawn` holds, spawn the mage at the dais centre and send the title. Store
    `spawned=true` and the mage UUID.
  - If `spawned` and the UUID entity is missing for 600 ticks while a player is inside and the
    boss is not defeated, respawn it (lost entity, for example a `/kill` or an unload bug). This
    is not done after defeat.
- Floor index and area come from the configure call. `unlocked` is
  `rec.isUnlocked(rec.getFloor(floorIndex))`.

- [ ] **Step 1: Write failing tests** in `MageSigilTest`:
  - `shouldSpawnTruthTable`: true only for `(false,true,false,true,false)`; each single flip gives
    false.
  - `nbtRoundTrip`: configure, `writeToNBT`, then a new tile `readFromNBT` keeps the area, anchors
    (size and first position), `spawned` and the floor index.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*MageSigilTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the sigil tick, `MageSanctum`, lang and HUD keys.
- [ ] **Step 4: Run it.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: `feat: mage sanctum spawning and defeat rewards`.

### Task 9: Dev-server verification and release 1.6.0

**Files:**
- Modify: `gradle.properties` (`mod_version=1.6.0`), `Reference.java` (`VERSION`), `HANDOFF.md`
  (roadmap item 4 done, architecture notes for catacombs), `README.md`
- Create: `dist/shatteredcitadels-1.6.0.jar` (replaces 1.5.1)

- [ ] **Step 1: Natural generation.** Run the dev server with level-type DEFAULT and a fresh world
  name.
  - Use `setworldspawn` near the test spot and restart so the spawn chunks stay loaded.
  - Spawn `tower`, `castle`, `manor`, `crypt` and `fragment` at spots ≥ 12 chunks apart.
  - Expected in the log:
    - every `Finished writing ... (N chunks, 0 blocks repaired)`;
    - catacomb floors listed;
    - no exceptions.
- [ ] **Step 2: Render.** Render each catacomb level with `scratchpad/tools/mapview.py` at a slice
  y (mode `slice`).
  - Check: corridors connected, halls themed, sanctum present.
  - Fix anything that reads as a plain grid.
- [ ] **Step 3: Mage summon check.** On the server console run
  `/summon shatteredcitadels:guardian_mage ~ ~ ~`. Expected: no exception, and the entity
  persists across a restart.
- [ ] **Step 4: Release.** Run `./gradlew build`, copy the jar to `dist/`, remove the 1.5.1 jar,
  commit, push to `claude/brave-franklin-ttdlk9`, and check that the raw link returns HTTP 200.
  - Commit message: `feat: catacombs with guardian mage, v1.6.0`.
