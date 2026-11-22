# End Stronghold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the vanilla Overworld stronghold with a large, varied, explorative stronghold built
from pieces with doorways. The eye of ender and `/locate` lead to it, and the End portal room is
inside it.

**Architecture:**
- A pure layout grower (`StrongholdLayout`) places rotated piece boxes with doorways around a
  central crossing.
- `StrongholdBuilder` writes the pieces into a `Blueprint`.
- `StrongholdPlan` is queued through `StructureJobs`.
- A `MapGenStronghold` subclass, installed through `InitMapGenEvent`, keeps the vanilla site
  positions and `getNearestStructurePos`, builds no vanilla pieces, and queues our plan when a
  site chunk is populated.

**Tech Stack:** Forge 1.12.2 (RetroFuturaGradle, `-Werror`), JUnit 4, existing
`StructurePlan`/`Blueprint`/`StructureJobs`.

**Spec:** `docs/superpowers/specs/2026-11-22-end-stronghold-design.md`

## Global Constraints
- **No vanilla pieces:** vanilla stronghold pieces are never built when `replaceStrongholds` is true
  (default true).
- **Same sites as vanilla:** the site positions and `getNearestStructurePos` are vanilla's.
- **Size:** about 120×120, 60–90 pieces, at most 10 steps from the start, within 70 blocks of the
  centre. Pieces keep a 1-block gap from each other.
- **Depth:** main floor y 20–35, at least 20 below the lowest surface estimated with
  `CatacombSite.estimatedSurface`, and at y 8 or above.
- **Pieces:** corridors are 3 wide and 4 high; halls 5 wide; stairs change the level by 6.
- **Portal room:** exactly 1, at least 4 steps from the start. 12 End portal frames face inward;
  each has an eye with 10 % chance. Lava under the frame; one silverfish spawner.
- **Limits:** grand library ≤ 1, small libraries ≤ 3, treasure room ≤ 1.
- **No mod mechanics:** no floors, waves, seals, boss or protection. Vanilla `mob_spawner`; mobs from
  config `strongholdSpawnerMobs`, default `minecraft:zombie, minecraft:skeleton, minecraft:spider,
  minecraft:cave_spider, minecraft:silverfish`.
- **Traps:** about 1 per 8 corridors: a stone pressure plate next to a wall dispenser holding
  arrows.
- **Loot:**
  - vanilla stronghold tables (`LootTableList.CHESTS_STRONGHOLD_LIBRARY`, `_CORRIDOR`,
    `_CROSSING`) get the modded pool;
  - the treasure room uses `ModLootTables.CATACOMB_DEEP`.
- **Record:** `StructureType.STRONGHOLD`; it counts as a structure in overlap checks.
- **Commits:** end with the session's Co-Authored-By / Claude-Session lines; no model names.

## Review Focus
1. **The portal room must always exist.** Growth can fail to fit it at random. Expected: the layout
   retries or forces it so that every seed has exactly one. Test in Task 1 (seeds 1..200).
2. **The eye of ender target vs the built stronghold.** The start crossing is centred on the site
   block (`chunkX*16+8`, `chunkZ*16+8`), so the eye ends right above it. Test in Task 3.
3. **A site chunk populated twice, or a restart:** no duplicate stronghold. The site area must be
   free and no job reserved before submitting. Test in Task 4.
4. **Sites under the ocean or on low ground.** The floor goes below the estimated sea floor
   (ocean base -1.0 gives surface 47, so the floor is ≤ 27) and stays ≥ 8. Test in Task 3.
5. **Older worlds** whose site chunks are already populated keep the vanilla stronghold, and the eye
   still points to it: `getNearestStructurePos` is unchanged. Test in Task 4.

---

### Task 1: Stronghold layout grower

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/StrongholdLayout.java`
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/PieceKind.java` (enum with
  footprint ranges, doorway pattern, weight, limit and minimum depth)
- Test: `src/test/java/com/shatteredcitadels/worldgen/StrongholdLayoutTest.java`

**Interfaces:**
- Produces:
  - `enum PieceKind { CROSSING, CORRIDOR, TURN, T_JUNCTION, STAIRS_UP, STAIRS_DOWN, PORTAL_ROOM,
    GRAND_LIBRARY, SMALL_LIBRARY, PRISON, ARMOURY, TREASURE, MESS_HALL, STOREROOM, QUARTERS,
    CISTERN, PILLARED_HALL, CRYPT }`
  - `final class Piece { PieceKind kind; StructureBoundingBox box; EnumFacing facing; int depth;
    List<Doorway> doors; }`
  - `final class Doorway { BlockPos pos; EnumFacing out; int width; boolean used; }`. `pos` is the
    bottom-centre of the opening, on the piece's wall.
  - `static StrongholdLayout generate(long seed, int centreX, int floorY, int centreZ)`
  - `List<Piece> pieces()`, `Piece start()`, `Piece portalRoom()`
- Rules:
  - The start is a `CROSSING` centred on (`centreX`, `centreZ`), with 4 doorways.
  - Growth: FIFO queue of open doorways, with a random pick among the first three. For each,
    shuffle a weighted list of kinds (respecting limits and `minDepth`), rotate each candidate so
    one of its doorways faces back into this doorway, and keep the first whose box (grown by 1)
    intersects no placed box and lies within 70 of the centre.
  - Stop at a random target in 60..90 pieces or when the queue empties. Pieces at depth 10 only
    take corridors-free kinds (rooms close the branch).
  - If no portal room was placed, rerun with `seed + attempt` (at most 8 attempts). Then force one
    on the deepest open doorway that fits, replacing the farthest dead-end corridor if needed.
  - `STAIRS_UP`/`STAIRS_DOWN` shift the y of their far doorway by ±6 (kept within floorY-12 ..
    floorY+12).

- [ ] **Step 1: Write failing tests:**
  - `exactlyOnePortalRoomForEverySeed`: for seeds 1..200, `count(PORTAL_ROOM) == 1` and its
    `depth >= 4`.
  - `piecesDoNotOverlapAndStayInRadius`: no two boxes intersect; every box is within 70 (x/z) of the
    centre.
  - `everyPieceReachableFromStart`: BFS over doorways with `used` linking (each used doorway
    coincides with a doorway of a neighbour piece at the same position, facing opposite) reaches
    every piece.
  - `sizeAndLimits`: 60 ≤ size ≤ 90 for ≥ 90 % of seeds 1..100; GRAND_LIBRARY ≤ 1,
    SMALL_LIBRARY ≤ 3, TREASURE ≤ 1.
  - `deterministic`: equal seeds give equal kind/box lists.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*StrongholdLayoutTest*'`. Expected: FAIL
  (missing classes).
- [ ] **Step 3: Implement** `PieceKind` and `StrongholdLayout`.
- [ ] **Step 4: Run them.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat: stronghold layout grown from pieces with doorways`.

### Task 2: Stronghold builder — corridors, crossing, stairs, portal room, sealing

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/StrongholdBuilder.java`
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/StrongholdPalette.java`
  (stone bricks plain, mossy, cracked and chiseled, andesite, cobble; Quark stone-brick variants
  through `ModPaletteRegistry.resolveDecor`)
- Modify: `src/main/java/com/shatteredcitadels/spawner/VanillaFallbackSpawnerPlacer.java`. Reuse it,
  or add `static void placeVanillaSpawner(World, BlockPos, ResourceLocation entity)` if it has none.
- Test: `src/test/java/com/shatteredcitadels/worldgen/StrongholdBuilderTest.java`

**Interfaces:**
- Consumes: Task 1 layout.
- Produces:
  - `StrongholdBuilder(BlockWriter w, CitadelInstance rec, Random rand)`
  - `void build(StrongholdLayout layout)`
  - `protected void buildRoom(Piece piece)`: the Task 3 hook; rooms are bare carved boxes until
    Task 3.
- Behaviour:
  - Every piece: masonry shell (floor, walls, ceiling) over its box, interior carved, doorways cut.
    Unused doorways are walled up; 1 in 6 is collapsed instead (gravel and cobble).
  - Corridor variants per piece: plain, cobwebbed, ruined, torch-lit, side alcove (a chest or
    vase).
  - About 1 trap per 8 corridors.
  - Stairs: a stair run of the shifted height with headroom.
  - Portal room:
    - room 11×16×9;
    - stair to a raised platform;
    - 12 `END_PORTAL_FRAME` around a 3×3 centre, facing inward (`BlockEndPortalFrame.FACING`),
      `EYE` true with 10 %;
    - lava 2 below the frame centre;
    - silverfish spawner under the stairs;
    - pillars and wall lights.
  - Spawners: vanilla `mob_spawner` through `afterPlace`; the mob is drawn from
    `strongholdSpawnerMobs` (Task 4 config; until then the default list as a constant).

- [ ] **Step 1: Write failing tests:**
  - `portalFrameHas12InwardFrames`: on the built blueprint there are exactly 12
    `END_PORTAL_FRAME` states around the portal room centre. Each faces the centre: the frame's
    `FACING` points from the frame towards the 3×3 middle.
  - `unusedDoorsAreClosed`: for every unused doorway, the opening's 3×4 (or 5×4) blocks are solid
    (wall, gravel or cobble).
  - `corridorsAreWalkable`: for every used doorway, the 3 blocks of the opening at floor+1 and
    floor+2 are not solid.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*StrongholdBuilderTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the palette, the builder (structure pieces, portal room, sealing,
  traps, spawners) and the spawner helper.
- [ ] **Step 4: Run them.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat: stronghold corridors, stairs, traps and the portal room`.

### Task 3: Stronghold rooms and the plan

**Files:**
- Modify: `StrongholdBuilder.java` (`buildRoom` for every room kind)
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/StrongholdPlan.java`
  (`StructurePlan`; kind `stronghold`)
- Modify: `src/main/java/com/shatteredcitadels/worldgen/StructurePlans.java` (dispatch)
- Modify: `src/main/java/com/shatteredcitadels/structure/StructureType.java` (`STRONGHOLD`)
- Modify: `src/main/java/com/shatteredcitadels/command/CommandCitadel.java` (kind `stronghold`;
  site = the player's chunk centre)
- Test: `StrongholdBuilderTest`, `BlueprintTest`

**Interfaces:**
- Produces:
  - `StrongholdPlan(int centreX, int floorY, int centreZ, long seed, int dimension)`;
    `static StrongholdPlan read(NBTTagCompound)`; `footprint()` = centre ±72 x/z, floorY-14 ..
    floorY+20.
  - `static int floorYFor(int estimatedSurface, Random r)` = `clamp(20 + r.nextInt(16),
    8, estimatedSurface - 20)`.
  - The record is a `CitadelInstance` of type `STRONGHOLD`, with no floors and no parts; its
    origin is the centre.
- Rooms:
  - **grand library:** 2 storeys, gallery with fences, ladders, aisles, chandeliers, 2 chests
    (library table);
  - **small library:** shelves and a desk, 1 chest;
  - **prison:** cells with iron bars and an iron door, a desk; zombie or skeleton spawner;
  - **armoury:** armour stands spawned in `afterPlace` with partial iron or chain armour, item
    frames with swords and bows, an anvil;
  - **treasure:** iron door with a button hidden in a chiseled brick niche, gold blocks, 2 chests
    `CATACOMB_DEEP`;
  - **mess hall:** tables (fence plus pressure plate), cauldrons, furnaces;
  - **storeroom:** chests (corridor table) and vases;
  - **quarters:** `RoomFurnisher` with PANTRY, BEDROOM or SMELTERY, then `abandon`;
  - **cistern:** water 2 deep with walkways;
  - **pillared hall:** pillars every 3;
  - **crypt:** sarcophagi, spider spawner;
  - **crossing:** crossing table chest.

- [ ] **Step 1: Write failing tests:**
  - In `BlueprintTest`, `strongholdPlanRoundTripAndCentre`: the NBT round trip keeps the plan. The
    built record has type `STRONGHOLD`, `getParts().isEmpty()` and no floors. The blueprint is
    open (not solid) at (`centreX`, `floorY+1`, `centreZ`): the eye leads to the crossing.
  - `floorStaysUnderLowGround`: `StrongholdPlan.floorYFor(47, r) <= 27` and `>= 8` for 100
    randoms; `floorYFor(20, r) == 8`.
  - In `StrongholdBuilderTest`, `roomsHaveContent`: at least one bookshelf in a library, iron bars
    in a prison, gold in a treasure room.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*StrongholdBuilderTest*' --tests '*BlueprintTest*'`.
  Expected: FAIL.
- [ ] **Step 3: Implement** the rooms, plan, type, dispatch and command kind.
- [ ] **Step 4: Run it.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: `feat: stronghold rooms and plan`.

### Task 4: Replace the vanilla stronghold generator

**Files:**
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/ShatteredStrongholdGen.java`
  (extends `MapGenStronghold`)
- Create: `src/main/java/com/shatteredcitadels/worldgen/stronghold/StrongholdReplacer.java`
  (`InitMapGenEvent` on `TERRAIN_GEN_BUS`, registered by `CommonProxy` next to
  `VanillaDungeonReplacer`)
- Modify: `ModConfig`/`ConfigCache`/lang: `replaceStrongholds` true; `strongholdSpawnerMobs` with
  the default list.
- Modify: `src/main/java/com/shatteredcitadels/loot/ModLootInjector.java` (modded pool on the three
  vanilla stronghold tables)
- Test: `src/test/java/com/shatteredcitadels/worldgen/StrongholdGenTest.java`

**Interfaces:**
- `ShatteredStrongholdGen` overrides:
  - `canSpawnStructureAtCoords(int, int)` → false;
  - `generateStructure(World, Random, ChunkPos)`: if `isSite(chunkX, chunkZ)` and
    `CitadelWorldGenerator.isAreaFree(world, plan.footprint())`, it submits a `StrongholdPlan` at
    (`chunkX*16+8`, `floorYFor(...)`, `chunkZ*16+8`) with seed `world.getSeed() ^ chunk long`.
    Returns false.
  - `public boolean isSite(int chunkX, int chunkZ)` = `super.canSpawnStructureAtCoords(chunkX, chunkZ)`.
  - `getNearestStructurePos` is **not** overridden.
- The replacer sets `event.setNewGen(new ShatteredStrongholdGen())` for `EventType.STRONGHOLD`
  when `replaceStrongholds` is true.

- [ ] **Step 1: Write failing tests:**
  - `noVanillaPiecesButSitesKept`: for a new `ShatteredStrongholdGen`,
    `canSpawnStructureAtCoords(x, z)` is false for a range of inputs (the override is `public` in
    the subclass). `getStructureName()` is "Stronghold". `ShatteredStrongholdGen.class` does not
    declare `getNearestStructurePos` (reflection), so the vanilla site lookup, and old worlds'
    vanilla strongholds, stay what the eye finds.
  - `duplicateSiteIsNotQueuedTwice`: a `StructureJobs` with a submitted `StrongholdPlan`; a second
    plan at the same centre fails `isReservedForStructures` → reserved true. Use the jobs API
    directly.
- [ ] **Step 2: Run them.** Run `./gradlew test --tests '*StrongholdGenTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the generator, the replacer, config, lang and loot pools.
- [ ] **Step 4: Run it.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: `feat: our stronghold replaces the vanilla one; eye of ender leads to it`.

### Task 5: Dev-server verification and release 1.7.0

**Files:**
- Modify: `gradle.properties` (1.7.0), `Reference.java`, `README.md`, `HANDOFF.md` (YUNG item 2
  done)
- Create: `dist/shatteredcitadels-1.7.0.jar` (replaces 1.6.4)

- [ ] **Step 1: Locate and command build.** On a DEFAULT dev world, run `/locate Stronghold`.
  Expected: coordinates. Then `/shatteredcitadels generate stronghold <x> <z>` at those
  coordinates; the log shows `Finished writing stronghold … 0 blocks repaired`. Repeat for one
  more site.
- [ ] **Step 2: Render.** Render a slice at floorY+1 and floorY+7 with `mapview.py`. Check the
  branching (not a grid), the portal room and the walled dead ends.
- [ ] **Step 3: Natural generation.** Set the spawn near a ring-1 site, restart, and check that
  `Planned stronghold` appears without a command.
- [ ] **Step 4: Release.** Run `./gradlew build`, copy the jar to `dist/`, remove 1.6.4, commit,
  push, and check that the link returns HTTP 200.
