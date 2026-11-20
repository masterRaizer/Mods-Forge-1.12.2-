# Catacombs and the Guardian Mage

Part 4 of the agreed roadmap, merged with YUNG-inspired item 1 (the vanilla spawner dungeon
overhaul). Next: YUNG items 2-6, then Herobrine (which reuses the mage's entity groundwork), then
other dimensions.

## Intent
The user found the basements too shallow. Under every surface structure, a spiral staircase leads
deep down into wide catacombs. Under all the structures they should feel like getting lost: winding corridors with
bone niches, dead ends, themed halls. At the far end a dangerous guardian mage keeps his own zone
of bookshelves and artifact pedestals. The vanilla 1.12 spawner dungeon, a single cobblestone box,
is replaced by small pieces of the same catacombs.

## User decisions
- Mage: **own entity** (option A), not a buffed evoker. Its code is the base for Herobrine later.
- Shape: **wide network** (option A). 2-3 levels of about 100x100 blocks each. This replaces the
  current 21x21 stacked crypt.
- Access: **open from the start** (option A). The catacombs are an optional dungeon with their own
  order: levels unlock one after another, the mage zone comes last, and mobs get stronger with depth.
- Loot: **own items** (option A). The mage always drops the Guardian Staff. The pedestals hold the
  Guardian Folio, additional relics, and loot from the pack's mods.
- Layout method: **cell grid with halls** (option A).

## Goals
1. **Entrance.** A spiral staircase (5x5 shaft around a 1x1 core) from the ground floor of a tower,
   a castle keep or the manor (from its cellar) down to catacomb level 0, which lies about 10
   blocks under the base. The stairwell is never sealed.
2. **Levels.** 2-3 levels, each about 100x100 (clamped to fit above y=6), centred under the
   structure.
   - On a 5-block cell grid: corridors 3 wide and 4 high.
   - Halls placed first, then a maze connects them, with extra loops so there is more than one way
     through.
   - Corridor dressing breaks the grid: bone niches (bone blocks, skulls), cave-ins (gravel,
     cobblestone), narrowings, short steps, cobwebs, torches that have mostly burnt out (sparse light).
   - Every carved space gets a 1-block shell wall, so caves (Better Caves) and liquids never flood it.
3. **Halls**, 2-4 per level, chosen at random:
   - crypt: sarcophagi made of slabs and stairs, wall tombs;
   - spider cave: irregular rounded cavern with heavy webs, egg clutches (vases), cave spiders and
     spiders on the floor's spawners;
   - skeleton hall: colonnade with rows of tombs and an ossuary.

   There are also small abandoned rooms reused from `RoomFurnisher`: storage, a bedroom-like guard
   post. Vases and chests use the existing loot tiers.
4. **Progression.**
   - Each level is a floor (indices -1, -2, -3) with wave spawners.
   - Level k+1 `requiresFloor` level k, so its spawners sleep until the level above is cleared.
   - The stair or ramp down to the next level is a sealed gate that opens when the current level
     is cleared.
   - Tiers rise with depth: structure base tier + 1 + level.
5. **Mage zone.** One per catacomb, on the deepest level, at the end farthest from the entrance
   stair.
   - A hall of about 27x27 that is two levels tall, so it digs down below the deepest level.
   - Furnishings: bookshelf walls and stacks, enchanting tables, brewing stands, cauldrons, a raised
     dais, light from palette light blocks.
   - The zone is its own floor (index -10) with an area, and it `requiresFloor` the deepest level.
     Its entrance is a sealed gate.
   - It has no wave spawners. The mage spawns once, the first time a player enters the unlocked
     zone, with a title banner like other bosses and a boss bar.
6. **Guardian Mage entity** (`shatteredcitadels:guardian_mage`): a biped in a robe with its own
   model and a 64x64 texture.
   - Stats: about 300 HP (config), armour 6, immune to fall damage and to its own spells.
   - Moves: keeps 8-12 blocks from the target and strafes.
   - Spell cycle:
     - a burst of 3 small fireballs;
     - a slowing bolt (Slowness II for 4 s);
     - a lightning strike on the player's spot after a 1.5 s particle warning.
   - Teleports to one of the zone's anchor points (next to bookshelves) when a player stays in melee
     range for 2 s, and every 8-12 s otherwise.
   - Summons 3 skeleton guards (at most 6 alive).
   - Below 50 % HP: a shield phase. He is invulnerable with a visible aura until the 4 guards
     summoned for that phase are dead. This happens once.
   - Leash: if he leaves the zone area he teleports to an anchor.
   - Does not despawn.
   - On death: drops the staff and marks the zone floor `bossDefeated`.
7. **Pedestals.** 4-6 pillar pedestals around the dais, each with a chest behind a ring of sealed
   gate blocks that need the zone floor. Pedestal loot is a new table `chests/mage_pedestal`:
   - the Guardian Folio (guaranteed on one pedestal);
   - the new Ring of Ashen Sight;
   - a chance of an existing relic (crown shard, aegis, forge heart);
   - high enchanted books;
   - modded magic loot (below).
8. **New items.**
   - **Guardian Staff.** Right click fires a small fireball that sets things on fire and deals
     about 6 damage. 60-tick cooldown, 250 durability, repaired with blaze rods. Drawn and modelled
     like the other items.
   - **Guardian Folio.** A relic in the Baubles TRINKET slot, or carried in the inventory when
     Baubles is missing. While worn, staff cooldown is -25 % and magic damage taken is -20 %.
   - **Ring of Ashen Sight.** A RING relic: Night Vision while below y=40 or in darkness, and undead
     deal -15 % damage.
9. **Modded loot.** An injector pool on the mage pedestal and on the deepest-level chests. Candidates
   from the user's pack: Artifacts, Trinkets and Baubles (`xat`), Mowzie's Mobs, Gobber, Iron
   Apples. Candidates are resolved against the item registry at start-up: missing ones are skipped
   and logged once, the same as palettes. The exact IDs are checked against the pack log before
   release.
10. **Vanilla spawner dungeons.**
    - Cancel `PopulateChunkEvent.Populate` with `DUNGEON` in the Overworld (config toggle, on by
      default).
    - Place **catacomb fragments** on a region grid instead, so the density stays similar to vanilla
      rather than one per chunk. A fragment is about 40x40, one level: one hall plus corridors and
      vases. It has no mage and no entrance (it is found by mining or through caves), like vanilla
      dungeons.
    - The existing standalone crypt with a ruined shrine on the surface becomes a fragment with
      that shrine and a spiral staircase.

## Non-goals
Herobrine; other dimensions; YUNG items 2-6; catacombs under sky citadels (no ground); retrofitting
structures that are already generated.

## Anti-goals
- Freezes or cascading worldgen: everything goes through `StructurePlan` → `Blueprint` →
  `StructureJobs`.
- A grid that reads as a grid.
- A catacomb that can be skipped to the mage by digging: spawners sleep, gates and the protection
  handler cover the parts.
- The mage dying to suffocation or to the player standing on a pillar: teleport anchors are open
  floor, and the shield and leash work against cheese.

## Constraints
- Forge 1.12.2; `-Werror`.
- Deterministic blueprints (a restart rebuilds the same thing).
- Existing floor, gate, protection and HUD systems.
- Protection only inside parts. Conquest is still decided by the **surface** boss: catacomb floors
  never turn `isConquered` on, and the mage zone floor is not counted as a boss floor for conquest.

## Approaches (user picked A)
- **A. Cell grid with halls (picked).** Halls first, maze plus loops, dressing to hide the grid.
  Guaranteed connectivity, easy per-level gates, fits the blueprint generator. Risk: a grid look,
  handled by the dressing.
- B. Free halls joined by winding tunnels. More organic, but connectivity, overlap and gating are
  hard.
- C. Hand-designed pieces stitched together. The most crafted look, but it needs many pieces to
  avoid repetition.

## Builder's calls (marked as Claude's)
- Spawn chance: catacombs under towers 50 %, under castles and manors 80 % (config). This replaces
  `undergroundUnderCastleChance`. The old crypt levels config maps to catacomb levels 2-3.
- Fragment grid: one per 10x10-chunk region with a 60 % chance, y 12-40, never within 2 chunks of a
  structure footprint.
- Floor indices: levels -1..-3, mage zone -10. The HUD shows the zone key `catacombs_level_N` /
  `mage_sanctum`.
- Mage numbers (HP, damage, cooldowns) live in the config. The mage spawns from a hidden marker tile
  (not the boss altar) that checks for players in the zone once a second.
- Texture art is drawn procedurally in the same style as the existing item textures.
- The palette is an underground variant of the structure's archetype (mossy/cracked stone bricks,
  modded stone options) through `ModPaletteRegistry`.

## Testing
- Unit tests:
  - catacomb plan NBT round trip;
  - the maze is connected (every hall reachable from the stair);
  - floor and requiresFloor chain;
  - mage zone registered with area;
  - fragment plan;
  - loot tables resolve.
- Dev server:
  - natural and command spawn of a tower, castle and manor with catacombs, rendered with mapview;
  - check the write time and `blocks repaired` = 0.
- Pack test by the user: walk through it, fight the mage, send the log.
