# Desert Temple Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkboxes.

**Goal:** Replace vanilla desert pyramids with our pyramid plus an underground temple ending in a
tomb, which is a pharaoh boss tomb at `desertTempleBossChance`.

**Spec:** `docs/superpowers/specs/2026-11-22-desert-temple-design.md`

## Global Constraints
- Builders are pure (blueprint), `-Werror`, deterministic from the seed.
- Stronghold behaviour and tests stay green.
- Old saves: plan NBT keys are new, and records default to the old behaviour.

## Review Focus
1. Exactly one tomb per temple, for every seed.
2. The plain variant is unprotected; the pharaoh variant protects only the tomb until the boss dies,
   then the sarcophagus gates open.
3. Jungle, swamp and igloo sites are still built by vanilla.
4. The spiral really connects the hall floor to the start crossing.
5. Shell closed: no open block next to raw terrain.

### Task 1: Generalise the layout grower to a piece catalogue
- `PieceSpec` interface (dims, exits, weight, limit, minDepth) implemented by `PieceKind`.
- `Catalogue` holds:
  - start, goal, corridor, turn, stairs up and stairs down;
  - sunk pieces;
  - radius, min/max pieces, max depth.
- `StrongholdLayout.generate(seed, x, y, z)` keeps using the stronghold catalogue.
  `generate(..., Catalogue)` is new. `portalRoom()` becomes `goal()`, and `portalRoom()` stays as an
  alias.
- Test: the existing `StrongholdLayoutTest` stays green.

### Task 2: Shared piece builder and the temple underground
- Extract `PieceBuilder` (shell, doors, corridors, stairs, traps, spawners, vases, torches) with a
  `BlockSet` palette interface. `CatacombPalette` and the new `SandstonePalette` implement it.
- `StrongholdBuilder` extends `PieceBuilder`.
- `TempleKind` catalogue and `DesertTempleBuilder` rooms: burial chamber, storeroom, scroll library,
  pillared hall, treasury with TNT trap, tomb plain or pharaoh.
- Tests `DesertTempleTest`:
  - `exactlyOneTomb` (seeds 1..100);
  - `shellIsClosed`;
  - `pharaohTombSealsSarcophagus`: gates require floor 0, floor 0 is the boss floor, the arena is
    set, the record prefers `atum:pharaoh`;
  - `plainTombIsUnprotected`.

### Task 3: Pyramid, plan, generator, boss preference, config, command
- Pyramid builder and spiral; `DesertTemplePlan` with NBT.
- `StructureType.DESERT_TEMPLE`.
- `CitadelInstance.preferredBoss` in NBT; `BossFactory` tries it first, then husk.
- `ScatteredFeatureGen` and the replacer. Config `replaceDesertTemples`, `desertTempleBossChance`
  0.4, `desertTempleSpawnerMobs`.
- `ModLootInjector` modded pool on `CHESTS_DESERT_PYRAMID`.
- Command kind `desert_temple`.
- Tests:
  - `spiralReachesStart`;
  - `planRoundTrip`;
  - `onlyDesertSitesReplaced` (pure `ScatteredFeatureGen.isOurs(Biome)`);
  - `preferredBossRoundTrip`.

### Task 4: Dev-server check and release 1.8.0
- `/citadel spawn desert_temple`, render, natural generation in a desert near spawn if one exists;
  0 repaired.
- Version 1.8.0, README, HANDOFF, dist, push.
