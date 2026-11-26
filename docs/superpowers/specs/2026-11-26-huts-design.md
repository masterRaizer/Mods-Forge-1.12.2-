# Witch huts with a cellar lair (part `huts`)

## What it is

Our own witch hut replaces the vanilla swamp hut on the swamp sites of the scattered-feature
generator (vanilla and Biomes O' Plenty, the same hook as the desert and jungle temples).
Above the swamp stands a timber hut on stilts; under it, reached by a hidden hatch, lies a
cellar lair of 4-7 rooms with witches, potion traps and a final chest. No boss.

## Decisions

- Package `com.shatteredcitadels.worldgen.hut`: `WitchHutPlan` (StructurePlan, kind `witch_hut`),
  `HutVariant`, `HutBuilder`, `CellarLayout` (pure), `CellarBuilder`, `WitchHutLoot`,
  `WitchHutSpawns`.
- Site: `TempleSites.Kind.SWAMP` (vanilla Swampland, BOP's swamp list). `ScatteredFeatureGen.handles`
  takes it when `witchHutsEnabled`; then the generator makes no vanilla start there. A hut started
  before the mod (old world) stays: `hasLegacyStartIn` as for temples. Overworld only.
- Plan: centre of the site chunk, surface = top water or ground block under the vegetation
  (the chunk is the one being populated). Built through `StructureJobs`; footprint is the
  centre +-18 blocks, from below the cellar floor to above the roof.
- Hut: floor 3 blocks above the surface on log stilts (`fillDown`), spruce planks, gable roof of
  stairs, fence windows, an open doorway to a fenced porch with a ladder down. Facing by seed.
  Variants by seed (or by command): `small` (7x7, one room), `tall` (7x7, a loft under a higher
  roof), `dock` (9x7 with a low pier and a boat). Inside: filled cauldron, crafting table, shelves
  with a brewing stand and potted mushrooms, a chest (`witch_hut/hut` loot). No cat, no light.
- Hidden way down: a trapdoor in the floor under the middle of a rug; a ladder in a closed
  3x3 stone shaft (it looks like the hut's stone footing) down to the entry room.
- Cellar (`CellarLayout`): rooms on a 3x3 grid of 12-block cells, entry room under the hut,
  4-7 rooms grown as a random tree, the deepest one is the ritual room; the others are a brewing
  lab, a herb store and cells. Rooms 7 or 9 wide outside; corridors 1 wide, 2 high. Every room
  and corridor is a full written shell (floor, walls, ceiling) of stone brick mix; the ceiling is
  at least 3 blocks under the surface, so nothing floods.
- Traps: in a corridor a stone pressure plate with a dispenser in the side wall at foot level,
  loaded with splash potions of poison, slowness or weakness (one kind per trap). At least one
  trap per cellar.
- Witches: `witchHutMinWitches..witchHutMaxWitches`, spread over the rooms beyond the entry,
  spawned once when the room is written, persistent. No spawners.
- Witch spawning at the hut: the record keeps the cabin box as its only part; `WitchHutSpawns`
  (Forge `WorldEvent.PotentialSpawns`) leaves only the witch in the monster list inside that box,
  exactly like vanilla does for its hut. Works for old huts regardless of the config switch.
- Record: `StructureType.WITCH_HUT` (appended last), unprotected (mineable like vanilla), name
  group `swamp`, chest points for `/citadel locate`.
- Loot: `loot_tables/witch_hut/cellar.json` (rare potions by vanilla potion NBT, nether wart, blaze
  powder, ghast tear, rabbit foot and other ingredients, emeralds, redstone, glowstone, a small
  chance of a mod relic) and `witch_hut/hut.json` (herbs and common ingredients; also the herb
  store chest).

## Config (category `witchHuts`)

| key | default | meaning |
| --- | --- | --- |
| `witchHutsEnabled` | true | our hut instead of the vanilla swamp hut (new sites only) |
| `witchHutCellar` | true | build the cellar lair (off: only the hut) |
| `witchHutMinWitches` | 2 | fewest witches in a cellar |
| `witchHutMaxWitches` | 5 | most witches in a cellar |

## Commands

- `/citadel spawn witch_hut [small|tall|dock]` in the player's chunk (tab completion, usage).
- `/citadel locate` lists huts like the temples.

## Tests

`WitchHutTest`: layout determinism, rooms do not overlap, links form a tree reaching every room,
cellar below the hut floor and inside the footprint, plan NBT round-trip, every written block inside
the footprint, chests present, at least one trap, witch count range, loot tables are valid JSON
with unique entry names. `TempleSitesTest`: swamp biomes are `SWAMP`.

## As built (notes)

- Seeds are spread with `CellarLayout.mix` (own bit mixer) before every `new Random`: site seeds of neighbouring chunks differ in a few bits only.
- Loot tables live in their own folder `loot_tables/witch_hut/` (`cellar.json`, `hut.json`) and are registered in `ModLootTables.register()`.
- `BridgePlanTest.structureTypeBridgeIsLast` became `structureTypeBridgeKeepsItsPlace` (ordinal 9): `WITCH_HUT` is appended after `BRIDGE`.
- Test class: `WitchHutTest` (12 tests).
