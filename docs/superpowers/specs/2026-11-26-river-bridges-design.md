# River Bridges (YUNG-inspired)

Status: approved by the user 2026-11-26. Plan: `docs/superpowers/plans/2026-11-26-river-bridges.md`.

## Intent
Rivers in the pack are bare. The user wants bridges over them in the spirit of YUNG's Bridges: they
make the world look lived-in, help crossing on foot or horse, and now and then give a small fight
with a reward. Bridges are small, frequent structures, unlike temples and citadels.

## User decisions (2026-11-26, answered one question at a time)
- **Purpose: both** (option В). Most bridges are peaceful decoration. About **one in five is
  fortified**: guards and a chest.
- **Frequency: moderate** (option А). Walking along a river, a bridge about every 400-600 blocks.
  Adjustable in the config.
- **Look: by biome, plus rare special bridges** (option В):
  - by biome: timber and stone arch (forest, plains), spruce (taiga), sandstone (desert), rope
    suspension with vines (jungle), low boardwalk on piles (swamp), stone with lanterns (snow);
    each in 2-3 lengths;
  - rare specials: ruined with a gap in the middle, covered with a roof, large stone with turrets.
- **Guards of fortified bridges: bandits** (option А). Vindicators and crossbow pillagers, 3-5 of
  them, spawned once, never respawning. Barricades on the deck and a small hut with a chest.
  Medium loot: food, arrows, iron, now and then an enchanted book.
- **Placement: at generation of new terrain** (approach А). The mod finds a narrow spot of a river
  (water 5-24 blocks wide, banks of similar height) and builds in the background like temples. No
  bridges in already explored chunks of old worlds.

## Claude's calls (correct if wrong)
- **Rivers are found by biome, not by scanning blocks.** A column is "river" when its biome is a
  river biome (`BiomeDictionary.Type.RIVER`: vanilla river, frozen river; Biomes O' Plenty worlds use
  the same river biomes). Reading the biome map loads no chunks, so the search cannot cascade. The
  style comes from the biome of the banks.
- **One candidate per map section.** The world is cut into sections of `bridgeSpacing` chunks
  (default 28 = 448 blocks). Each section holds at most one bridge. The first river chunk of the
  section that passes the checks takes it; the choice is remembered in the mod's saved data, so it is
  stable across restarts. Sections whose seeded roll fails stay empty (default chance 0.8), so
  bridges do not form a visible grid.
- **Axis-aligned bridges only** (north-south or east-west). The crossing is taken where the river
  biome is narrowest along one axis and at least twice as long along the other. Diagonal reaches are
  skipped. Simpler, always tidy, and enough sites remain.
- **Deck height is fixed from sea level**, not from sampled terrain: rivers lie at sea level, the
  deck sits 2-5 blocks above it depending on the style (boardwalk lowest, stone arch highest, so
  boats pass under arches). The ends adapt when their chunk is written: piers go down to the river
  bed, abutments fill down to the ground, and approach stairs or a ramp follow the real bank, cutting
  at most 3 blocks and filling at most 6. A bank steeper than that cancels the bridge before any
  block is placed in that chunk (see "Left to the builder" for how the check is made cheap).
- **Length 9-40 blocks** including abutments, width 3 (footbridges) to 5 (stone, covered, turreted).
- **Fortified bridges only where they fit**: water at least 9 wide and a style with a solid deck
  (not rope, not boardwalk). The hut stands on the bank at one end.
- **Guards**: `minecraft:vindication_illager` plus a crossbow pillager from Modern Backports when
  that mod has one registered, else a vanilla skeleton archer in leather. Looked up by id at run
  time (no compile-time dependency). Spawned once when a player first comes within 32 blocks,
  marked persistent so they do not despawn; the record remembers that they were spawned. None in
  Peaceful (and they are not spawned later either: the camp is simply empty).
- **Specials are rare**: about one bridge in ten, and a special bridge is never also fortified
  except the turreted one, which always is (its turrets are the camp).
- **Bridges are not protected.** Players may break, extend or rebuild them. No sealed blocks, no
  boss, no advancement in the first version.
- **Not near our large structures or in spawn-protected land**: a section is skipped when the
  crossing lies inside or within 2 chunks of a recorded citadel, castle, manor or temple.
- **Dynamic Trees and other trees** on the approach are cleared like at the jungle temple (wood and
  leaves are not ground).
- **New `StructureType.BRIDGE`** is added at the end of the enum. Existing constants are untouched,
  old worlds load as before. Records are small (bounds, style, fortified flag, guard-spawned flag,
  chest position).
- **Command for testing**: `/citadel spawn bridge [style] [fortified]` builds one at the nearest
  suitable crossing or, failing that, straight ahead of the player over whatever is there.

## Amendments after reading the code (2026-11-26, approved design unchanged for the player)
- **Spacing instead of sections.** No per-section saved state. A river chunk being populated may get
  a bridge when its centre is a crossing, a seeded per-chunk roll passes (`bridgeChance`, 0.5) and
  no other bridge (recorded or pending) lies within `bridgeMinDistanceChunks` (24). Replaces
  `bridgeSpacing`.
- **No "too steep cancels" rule.** `StructurePlan.build` has no world access, so ends adapt through
  the write-time column operations (`fillDown`, `clearColumn`, `setIfNotSolid`). Sites whose bank
  biome is mountain, ocean or mushroom are rejected from the biome map instead.
- **Guards are spawned when the camp is written**, by a tile action, and made persistent; no
  proximity tracking and no guard-spawned flag. None in Peaceful.
- **Bridge records** show no HUD panel, title, floors or advancement and are unprotected.

## Goals
- A player following a river in a new world meets a bridge every 400-600 blocks on average.
- Seven biome styles and three specials, each recognisable from a distance, built from vanilla
  blocks with optional Modern Backports and pack blocks through the existing palette mechanism
  (`ModPaletteRegistry.resolveDecor`), so the mod still works alone.
- Every bridge is walkable end to end by a player and a horse (no 2-block steps, headroom 3), except
  the ruined one, whose gap is the point.
- Boats pass under arch bridges.
- Fortified bridge: 3-5 guards once, a hut with one chest (own loot table
  `shatteredcitadels:chests/bridge_camp`), barricades that do not block the crossing completely.
- No cascading generation, no measurable TPS cost: one biome-map query per river chunk of a section
  that has no bridge yet, building through `StructureJobs` with its chunk budget.
- Everything switchable: `bridgesEnabled`, `bridgeSpacing`, `bridgeChance`,
  `bridgeFortifiedChance` (0.2), `bridgeSpecialChance` (0.1), dimension list (overworld by default).
- en_us and ru_ru for every new string (command help, config comments shown in game, loot names if
  any).

## Non-goals
- Bridges over lakes, oceans, ravines or lava.
- Roads leading to bridges, links to villages.
- Retro-generation in explored chunks.
- Bridges in other dimensions (they come with the dimensions work).

## Anti-goals
- No code or structure files from YUNG's Bridges; the look is our own.
- No bridge that dams the river or leaves floating pieces when a neighbouring chunk never loads.
- No endless guard spawning.

## Constraints
- Forge 1.12.2 (14.23.5.2860), Java 8, `-Werror`, no compile-time dependency on pack mods.
- Biome budget untouched; no new biomes, dimensions or block ids are required. New blocks are not
  planned (rope bridge uses fences, slabs and trapdoors).
- OptiFine G5: nothing visual beyond ordinary blocks.
- Old worlds: new saved fields have defaults; `StructureType` constants keep their names.

## Approaches considered
- **А (chosen): generate with new terrain, sites from the biome map.**
- Б: also retro-generate near players in explored chunks: risk of building over player bases.
- В: only near villages and our structures: far fewer bridges than asked.

## Verification
- Unit tests (written first): river-width and axis finder on synthetic biome maps; section roll is
  deterministic for a seed; plan of every style fits its bounds, deck is continuous (except
  ruined), headroom 3; end adaptation on synthetic banks (flat, +3, -6, too steep cancels).
- Test server with the whole pack: new world, 1500+ chunks, count of bridges against the number of
  river sections, zero cascade lines from the mod, TPS 20; a Biomes O' Plenty world as well; an old
  1.9.x world loads.
- In game (autotest, screenshots): one bridge of each style by command, walk across, boat under an
  arch, fortified bridge: guards appear once, chest opens, guards do not return after reload.

## Left to the builder
- Exact block patterns of each style and its length variants.
- Cheap pre-check of the banks: either accept the cancel-per-end rule above (a bridge may then lack
  one approach and gets plain stairs instead), or judge steepness from the biome of the banks
  (hills variants excluded). Decide after measuring on real terrain.
- Whether Biomes O' Plenty rivers are all tagged `RIVER` (verify on the pack's BOP world first).
- Exact loot weights of `bridge_camp`.
