# Large structures: chunk-spanning generator, branched towers, walled castles

Part 2 of the agreed roadmap (then: manor, catacombs, Herobrine, other dimensions).

## Intent
Towers should not read as a single pillar: wings branch out at different heights and wings carry
their own annexes. A second kind of fortress appears: a curtain wall with towers, a gatehouse, a
moat with a drawbridge, a courtyard with buildings and the keep in the middle. Size is not capped;
the user's only hard requirement is *no freezes* and no content cut to get there.

## Goals
1. **Chunk-spanning generator** usable by every structure (citadels, crypts, castles, later manor
   and catacombs): any footprint, no cascading worldgen, no tick spikes.
2. **Branched towers**: 1-3 wings per qualifying floor band, 1-3 floors tall, 9-15 long; annexes
   (turret, bay, chapel) on wings, sometimes an annex on an annex; elevated wings stand on
   buttress piers. Wing floors belong to the tower floor at the same height (their spawners join
   that floor's waves, so the sealed stairwell still waits for the whole floor).
3. **Walled castle (user picked option B, siege)**: square or oblong bailey 64-112 blocks,
   curtain wall with walkway and crenellations, corner and mid-wall towers, twin-towered
   gatehouse, lined moat with drawbridge, courtyard buildings (barracks, smithy, stables,
   storehouse, chapel, well, garden). The courtyard is floor 0 of the trial: its waves must be
   cleared before the sealed keep door opens; the keep (a branched tower) follows with its
   floors, boss and optional crypt.

## Non-goals
Manor, catacombs, Herobrine, other dimensions (later parts). Retro-fitting existing structures.

## Constraints
- Never load or generate a chunk from generation code; never block the server thread for long.
- Must work with the existing floor/wave/gate/protection systems and saved data.
- Deterministic: a half-built structure must finish identically after a restart.

## Approaches (Claude's call; the user handed over the how)
- A. Vanilla-style structure components generated inside each chunk's population. Needs every
  builder rewritten as clipped components; population still runs the work synchronously.
- B. Re-run the whole builder clipped to each chunk. Simple, but N times the work and the
  builders read terrain that is not there yet.
- **C. Blueprint + budgeted writer (picked).** Plan the site on the anchor chunk (cheap, uses only
  loaded terrain and the biome provider), build the full structure into an in-memory blueprint on
  a background thread, then write it into loaded, populated chunks a few milliseconds per tick,
  nearest chunks first. Terrain-dependent steps become column operations resolved at write time.

## Design
- `BlockWriter` becomes the abstract write API with two implementations: `WorldBlockWriter`
  (direct, clipped; kept for compatibility) and `BlueprintWriter` (virtual). New operations:
  `fillDown` (foundations and piers down to solid ground), `clearColumn` (terraforming),
  `setIfNotSolid`, and `afterPlace(pos, action)` for tile entities (chest loot tables, trial
  spawners, altar, beds, dispensers, fridge stocking). Builders never touch the `World`.
- `Blueprint`: per chunk, packed block positions + state ids, column ops and tile actions.
- `StructureJob` (saved): structure kind, plan parameters, seed, citadel id, finished chunks.
  `StructureJobs` (per world): async blueprint build on a single worker thread, then a
  `WorldTickEvent` writer with a time budget (`generationBudgetMs`, default 5 ms/tick), only into
  chunks that are loaded, populated and have loaded neighbours; nearest to a player first.
  Unloaded chunks wait for the next load. Tile actions are idempotent.
- The `CitadelInstance` record comes from the blueprint build and is registered as soon as the
  blueprint is ready, so floors, gates and protection exist before the blocks.
- `FloorData` gains optional horizontal bounds; `floorAt(BlockPos)` prefers the smallest
  containing floor (courtyard vs. keep ground floor at the same height). Courtyard floors are
  labelled "Courtyard" in the HUD.
- `CitadelInstance` gains structural part boxes; protection applies inside parts only, so
  terrain around wings and in the courtyard stays diggable (fallback: total bounds for old saves).
- Placement: section grid unchanged, margin grows with the footprint so neighbours never
  overlap; castles reject mountain/ocean/river/swamp biomes via the biome provider (no chunk
  loads) and terraform the rest (fill below, clear above, lined moat).
- Config: `walledCastleChance` (0.35), `towerWingChance`, `generationBudgetMs`.
- Commands: `/citadel spawn castle`, all spawns go through the job queue.

## Calls made by Claude
- Courtyard = floor index 0, keep floors start at 1 in castles.
- Wings only on floors below the boss arena; the arena stays a clean top.
- Moat is lined with stone (no water spilling on slopes); drawbridge is a fixed plank bridge.
