# End Stronghold (YUNG-inspired)

YUNG item 2 of the agreed list. Next: desert temples, jungle temples, bridges, witch huts, then
Herobrine and other dimensions.

## Intent
The user wants the vanilla stronghold to be replaced by a large, varied one, "like YUNG's" (Better
Strongholds): free exploration, no sealed floors, waves or boss. The eye of ender and
`/locate Stronghold` must lead to it, and the End portal is inside it. The code and layouts are our
own. It uses our generator (no freezes), palettes, vases, lived-in rooms and modded loot.

## User decisions
- Style: **like YUNG**. Explorative: no seals, no waves, no boss, no protection; vanilla spawners and
  traps.
- Layout method: room and corridor **pieces with doorways** that attach to one another (Claude's
  pick; the user handed over the how).

## Goals
1. **Replace the vanilla stronghold in the Overworld** (config `replaceStrongholds`, default true).
   - On `InitMapGenEvent` with type `STRONGHOLD`, install `ShatteredStrongholdGen`, a subclass of
     `MapGenStronghold`.
   - Its `canSpawnStructureAtCoords` returns false, so no vanilla pieces are built.
   - It keeps the vanilla `getNearestStructurePos` and site positions (rings of 128, the first three
     at 1280–2816 blocks). The eye of ender, `/locate` and anything else that asks for "Stronghold"
     therefore get the sites.
   - Its `generateStructure` (called during chunk population) queues our stronghold when the
     populated chunk is a site (vanilla check `super.canSpawnStructureAtCoords`). This happens once
     per site; registered records and jobs prevent duplicates.
   - Sites whose chunks were populated before the mod (older worlds) keep the vanilla stronghold the
     eye already leads to.
2. **Size and depth.**
   - Roughly 120×120 blocks, mostly one level; stair pieces go up or down by one storey (6 blocks).
   - The floor of the main level is at y 20–35. It stays at least 20 below the lowest surface
     estimated from biome base heights (`CatacombSite.estimatedSurface`), and at y 8 or above.
   - There is no surface entrance (like vanilla).
3. **Layout from pieces.** Each piece has:
   - a bounding box;
   - doorways (position, facing, width 3, or 5 for halls);
   - a builder that writes it with random variation (dimensions within a range, furnishing,
     damage).

   Growth works like this:
   - Start with a **central crossing**. Repeatedly take an open doorway (breadth-first, with a
     random pick among the oldest) and try weighted candidate pieces, rotated to fit.
   - Keep the first piece that does not intersect any placed piece (1-block gap) and stays within
     70 blocks of the centre.
   - Stop at 60–90 pieces or 10 steps from the start.
   - Doorways left unused are walled up, or occasionally collapsed (gravel and cobble).
4. **Pieces.** Where a piece has a limit, it is given in brackets.
   - **Corridors**: straight (4–14 long; the variants are plain, cobwebbed, ruined/collapsed,
     torch-lit, with a side alcove), turn, T junction, crossing, stairs up and stairs down.
   - **Portal room** (exactly 1, at least 4 steps from the start):
     - large, with a stair up to a 5×5 frame of 12 End portal frames over a lava pool;
     - each frame has an eye with 10 % chance, as in vanilla;
     - one silverfish spawner under the stairs;
     - pillars and wall lighting.
   - **Grand library** (≤ 1): two storeys with a gallery, ladders, bookshelf aisles, chandeliers and
     chests.
   - **Small library** (≤ 3).
   - **Prison**: a row of cells with iron-bar doors, a guard's desk and chains; skeleton or zombie
     spawner.
   - **Armoury**: armour stands spawned with partial iron/chain sets, weapon racks (item frames),
     anvil.
   - **Treasure room** (≤ 1): behind an iron door opened by a hidden button, gold, chests.
   - **Mess hall**: long tables, cauldrons, furnaces.
   - **Storerooms**: chests, vases, crates.
   - **Quarters**: existing `RoomFurnisher` pantry, bedroom and smeltery, abandoned.
   - **Cistern**: water pool with walkways.
   - **Pillared hall**.
   - **Crypt**: sarcophagi like the catacomb crypt.
5. **Danger.**
   - Vanilla `mob_spawner` blocks: zombie, skeleton, spider and cave spider in prisons, crypts and
     some corridors; silverfish in the portal room. The list is in config
     `strongholdSpawnerMobs`.
   - About 1 trap per 8 corridor pieces: a pressure plate next to a wall dispenser with arrows.
   - Cobwebs and sparse light.
6. **Loot.**
   - Vanilla stronghold tables (library, corridor, crossing). `ModLootInjector` adds the modded pool
     to them, which also applies to any vanilla strongholds.
   - Vases in storerooms and corridors.
   - Treasure room: `CATACOMB_DEEP`.
7. **Record.**
   - Registered as a `CitadelInstance` of the new `StructureType.STRONGHOLD`, with no floors and no
     parts. It is therefore not protected and is mineable like vanilla.
   - It counts as a structure for overlap checks; fragments avoid it.
   - It is listed by `/citadel locate` and `/shatteredcitadels list`.
8. **Command:** `/citadel spawn stronghold` builds one at the player's chunk for testing.

## Non-goals
End cities, Nether fortresses, other dimensions, surface entrances, waves/seals/bosses in
strongholds, retro-fitting strongholds that already exist.

## Anti-goals
- The eye leads anywhere but a real portal room.
- Two portal rooms.
- Freezes or cascading generation.
- A stronghold that reads as a grid.
- Breaking the vanilla End fight.

## Constraints
- The planning (layout) runs on the server thread during population, so it must be cheap: pure
  data, no world access except biome base heights. The blueprint is built on the background thread
  as for every structure.
- Deterministic from the site seed.
- `-Werror`.

## Approaches (Claude's pick; the user handed over the how)
- **A. Pieces with doorways (picked).** Branching, varied and close to how YUNG's strongholds feel.
- B. The catacombs' cell grid: it reads as a maze, not a stronghold.
- C. A few fixed hand-made layouts: they repeat quickly.

## Builder's calls
- Corridors are 3 wide and 4 high; halls 5 wide where the doorway is a hall doorway.
- Piece weights and the 60–90 piece target can be tuned after the first renders.
- Spawners are vanilla `mob_spawner` set up through `afterPlace`.
- The palette is stone bricks (plain, mossy, cracked, chiseled), andesite and cobble, plus Quark
  stone-brick variants when installed.

## Testing
- Unit tests:
  - the layout has exactly one portal room;
  - pieces never overlap and stay within the radius;
  - every piece is reachable from the start through doorways;
  - the layout is deterministic;
  - the plan NBT round-trips;
  - the portal frame has 12 frames facing inward.
- Dev server:
  - `/locate Stronghold` and the site position match;
  - `/citadel spawn stronghold` writes with 0 repaired blocks;
  - renders of the level.
- In game (user): throw an eye of ender, find the portal room, activate the portal.
