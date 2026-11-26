# Herobrine: end-game boss, lairs, apparitions

Status: built to the lead's design defaults and the owner's answers (2026-11-26). Branch
`part/herobrine`. Tests pass; nothing was checked in the game yet.

## Owner's answers (applied)
- Lairs with the altar inside: yes. `playerBuiltAltar` stays off by default.
- End-game but fair and beatable: no regeneration, no invulnerability, at least 2 seconds between
  his actions, lightning marked by particles and a hiss for `lightningWarningTicks` (default 30,
  never under 20) and avoidable by leaving a 3-block circle, melee 11 and lightning 12 by default
  (no one-shot in diamond armour), minions bounded, he never flies and teleports only next to his
  target. Health 600 by default.
- Apparitions only within `apparitionRadiusBlocks` (default 256) of a lair altar whose boss is not
  yet defeated; nowhere else. Lairs are found in own saved data, no chunk is loaded for it.
- One combined release at the end: the version is not bumped here.

## Intent
An own end-game boss in the spirit of the old Herobrine legend: a rare lair with the classic altar,
a three-phase fight, a unique relic, and a harmless figure that now and then watches the player from
afar. All code and assets are our own (the skin is drawn by `tools/herobrine_textures.py`).

## What is built
- **Lair** (`StructureType.HEROBRINE_LAIR`, appended at the end of the enum; plan kind
  `herobrine_lair`). Three variants chosen by the seeded roll of the site:
  - `keep`: ruined stone-brick keep 17x17 with four corner towers, altar in the open yard;
  - `tower`: obsidian tower 9x9, altar on the ground floor, chest on the top floor (ladder);
  - `shrine`: underground chamber 11x11 under a small surface marker, entered by a ladder shaft.
  Each holds the altar (gold 3x3, netherrack on the centre, four redstone torches on the gold next to
  it) and one loot chest (`shatteredcitadels:chests/herobrine_lair`). Built through
  `StructurePlan` -> `Blueprint` -> `StructureJobs`. Lair records are unprotected, show no HUD panel.
- **Altar.** Using flint and steel (or a fire charge) on the netherrack of a lair altar summons the
  boss once. The altar is then dormant: the state is kept in own world saved data
  (`shatteredcitadels_herobrine`), and the netherrack turns to obsidian. With `playerBuiltAltar` a
  player-built altar of the same pattern works anywhere in the overworld (its netherrack also turns
  to obsidian, so each summon costs a new netherrack; the gold stays). No summon while another
  Herobrine is alive within 64 blocks, and none in Peaceful.
- **Boss** (`shatteredcitadels:herobrine`, player model, own skin with glowing white eyes). One boss
  bar (the entity owns its `BossInfoServer`, which `BossBars.hasOwnBar` recognises).
  - Phase 1 (health above 66%): melee, short teleports behind the target.
  - Phase 2 (66%-33%): adds lightning: a ring of particles marks the spot for
    `lightningWarningTicks`, then the bolt hits (effect-only bolt plus damage within 3 blocks; a
    real bolt that may start fire only with `herobrineGriefing`).
  - Phase 3 (below 33%): adds summoning undead minions (never more than `herobrineMaxMinions` alive, at most 3 per cast) and a
    short blindness (3 seconds, at most once in 20 seconds) on players within 16 blocks.
  The spell choice is a pure class (`HerobrineSpellCycle`), phases too (`HerobrinePhases`).
  Immune to fire, fall and lightning; does not despawn; drops the relic always, plus 3-5 diamonds.
  In Peaceful he leaves and a lair altar he came from is re-armed (obsidian back to netherrack).
- **Relic** `herobrine_eye` (charm): night vision while worn, blindness cannot be applied, +25%
  damage to undead. Appended at the end of `RelicKind`.
- **Apparition** (`shatteredcitadels:herobrine_apparition`): a still figure 30-60 blocks from a
  player, at night, in a thunderstorm or underground, and only near a lair whose boss is not yet
  defeated (`apparitionRadiusBlocks`). Looks at the player; vanishes when the player
  comes within 12 blocks, looks at it for about a second, hits it, or after 30 seconds. Never saved,
  never loads chunks (only placed in loaded chunks), deals no damage and changes no blocks. Per
  player one attempt per `apparitionIntervalMinutes`, each attempt succeeds with `apparitionChance`.

## Decisions
- **Placement.** The overworld is cut into regions of `lairSpacingChunks` (default 112 chunks =
  1792 blocks). Each region has one seed-scrambled candidate chunk (kept 8 chunks from the region
  edge so two lairs are never adjacent) and a seeded roll `lairChance` (0.6). The lair is submitted
  when that exact chunk is populated, so nothing is searched and nothing cascades. Skipped: flat and
  debug worlds, within `lairSpawnClearanceChunks` (32) of spawn, ocean/river/beach/mushroom biomes
  (read from the BiomeProvider), ground below 50 or above 180, areas that fail
  `CitadelWorldGenerator.isAreaFree` or `ForeignZones.blocks`, chunks near a citadel site candidate.
- **Config is read directly** from `ModConfig.HEROBRINE` through `herobrine.HerobrineSettings`
  (clamping there), not through `ConfigCache`, to keep the shared edit small.
- **Registration** of the entities, the relic item, the item model and the renderers lives in own
  event subscribers in `com.shatteredcitadels.herobrine`; entity network ids 40 and 41.
- **Sounds** are vanilla (wither spawn on summon, enderman teleport, dragon growl on a phase change, creeper hiss before
  lightning).
- **Minions** are vanilla zombies and skeletons in leather helmets; tagged, never dropping their
  equipment, removed when the boss dies or the world turns Peaceful.
- **Player-built altar and griefing are off by default.**

## Config keys (category `herobrine`)
`herobrineEnabled` (true), `lairsEnabled` (true), `lairSpacingChunks` (112, 32-512), `lairChance`
(0.6), `lairSpawnClearanceChunks` (32), `playerBuiltAltar` (false), `herobrineHealth` (600, 20-1024),
`herobrineDamage` (11), `herobrineArmor` (10), `herobrineMaxMinions` (6, 0-16),
`lightningWarningTicks` (30, 20-100), `lightningDamage` (12), `herobrineGriefing` (false),
`apparitions` (true), `apparitionIntervalMinutes` (8), `apparitionChance` (0.35),
`apparitionRadiusBlocks` (256, 0 = never).

## Commands (op level 2, under `/citadel`)
- `/citadel spawn herobrine_lair [keep|tower|shrine]`: builds a lair at the player's position.
- `/citadel summon herobrine`: spawns the boss at the player's position.

## Tests
`src/test/java/com/shatteredcitadels/herobrine`: phases and spell cycle, altar pattern, lair
placement roll (determinism, margins, scrambling), lair plan (NBT round-trip, determinism, all blocks
inside the footprint, altar present in every variant), apparition rule, saved-data round-trip,
settings clamps, relic factor.

## Not done / left to the lead
- In-game look of the three lairs, the fight balance, the apparition frequency.
- No advancement, no own sounds, no boss music.
- Lairs generated before this part exist in no world, so nothing is migrated. A lair whose altar
  blocks were never placed (job dropped) is simply not registered.
- Shared tests touched: `BridgePlanTest` no longer demands that BRIDGE is the last type;
  `LootTableItemsTest` counts 14 chest tables.
