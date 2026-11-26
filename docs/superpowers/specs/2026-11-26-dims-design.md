# Other dimensions and the Nether fortress (part `dims`)

## What it is

The mod's towers and walled castles also generate in the Nether, on the outer End islands and in
Atum (Atum 2, optional), each in the dimension's own stone and with the dimension's own garrison.
The vanilla Nether fortress is replaced by an own, larger one that keeps every vanilla mechanic.

Code lives in `com.shatteredcitadels.worldgen.dims` and `...dims.fortress`.

## Decisions

- **Dimension kind** (`DimKind`): NETHER = dimension -1, END = dimension 1, ATUM = any dimension
  whose `DimensionType` name or provider class name contains `atum`. No compile dependency.
- **Reuse, not rewrite.** The existing `CitadelPlan` / `CastlePlan` / `DesertTemplePlan` are planned
  by the new code (own ground finder) and wrapped in `DimensionPlan`. After the inner plan has
  built its blueprint, `DimensionPlan` remaps overworld blocks to the dimension's blocks
  (`DimPalette`: grass -> netherrack / end stone / sand, wood -> nether brick / purpur, water ->
  lava / nothing, sandstone -> Atum limestone, ...) and keeps terrain clearing below the Nether roof.
- **Tower stone** comes from three new archetypes appended to `CitadelArchetype`:
  `NETHER_CITADEL`, `END_CITADEL`, `ATUM_CITADEL` (optional `modernbackports` blackstone/basalt and
  Atum limestone by registry name, vanilla last).
- **Ground** (`GroundFinder`, pure): Nether = lowest solid floor at or above the lava sea with
  free air above it, below the roof; a site needs a floor under 7/8 of its samples and a small
  height spread. End = top solid block; a site with more than 1/8 void samples is rejected.
  Atum = top solid block like the overworld. Only the populated chunk and its +x/+z neighbours
  are read.
- **Sites** (`DimensionWorldGenerator`): own section grid = overworld section size x spacing
  multiplier (1.5 by default), up to three candidate chunks per section, the usual minimum
  distance between structures. Neighbouring seeds are scrambled.
- **End**: only farther than `endMinDistance` (1000) blocks from 0,0, only on solid end stone.
  Castles only where every chunk of the footprint is an island chunk of the vanilla End
  generator (never asked from other generators: there only towers are built).
- **Garrison**: wave spawners in these dimensions pick their mobs from the dimension's list
  (config) instead of the tier registry; unknown names are skipped, an empty result falls back to
  the tier registry.
- **Left out**: manor, crypt and tower catacombs, sky citadel, jungle temple and river bridges do
  not generate in these dimensions. The desert temple generates only in Atum.

### Nether fortress

- `ShatteredNetherBridgeGen extends MapGenNetherBridge` is installed through `InitMapGenEvent`
  (type `NETHER_BRIDGE`); Biomes O' Plenty's Nether generator asks the same event, so both are
  covered. It starts no vanilla pieces for new sites, old saved starts keep generating (old
  worlds keep their fortresses and spawns).
- At a vanilla fortress site chunk an own `FortressPlan` is queued through `StructureJobs`.
- `FortressLayout` (pure, deterministic from the seed): a lattice of nodes 22 blocks apart, the
  keep with the treasure hall in the middle, a random tree with a few loops around it. Nodes:
  keep, crossings, blaze platforms (vanilla blaze spawner), wart rooms (stairs, soul sand, nether
  wart), turrets with a chest. Edges: open bridges on pillars down to the ground (through lava) or
  roofed corridors.
- Vanilla mechanics kept: `isInsideStructure` is true inside the pieces of an own fortress, so the
  vanilla and BOP generators return the fortress spawn list there (wither skeletons, blazes,
  magma cubes, pigmen, skeletons); chests use `minecraft:chests/nether_bridge`; blaze spawners are
  vanilla spawner blocks, 2-4 per fortress; wart rooms grow nether wart.
- The fortress is recorded as `StructureType.NETHER_FORTRESS` (appended), unprotected like the
  stronghold. `/locate Fortress` keeps working (site rule unchanged).

## Config (category `dimensions`)

| key | default | meaning |
| --- | --- | --- |
| `netherEnabled` | true | towers and castles in the Nether |
| `netherSpacing` | 1.5 | section size multiplier (bigger = rarer) |
| `netherCastleChance` | 0.25 | share of walled castles |
| `netherMobs` | wither skeleton, blaze, zombie pigman, magma cube | garrison |
| `endEnabled` | true | towers and castles on the outer End islands |
| `endSpacing` | 1.5 | |
| `endCastleChance` | 0.15 | |
| `endMinDistance` | 1000 | blocks from 0,0 |
| `endMobs` | enderman, shulker, endermite | |
| `atumEnabled` | true | towers and desert temples in Atum |
| `atumSpacing` | 1.5 | |
| `atumTempleChance` | 0.4 | share of desert temples |
| `atumMobs` | Atum mummy, forsaken, bonestorm, wraith, stoneguard; husk, skeleton | |
| `replaceNetherFortress` | true | own fortress instead of the vanilla one |
| `fortressBlazeSpawners` | 3 | blaze platforms per fortress (0-4) |

## Commands

- `/citadel spawn <tower|castle|archetype>` in the Nether, the End or Atum plans on the
  dimension's ground with the dimension's blocks and garrison (`desert_temple` in Atum too).
- `/citadel spawn nether_fortress` builds the own fortress at the player (Nether only).
