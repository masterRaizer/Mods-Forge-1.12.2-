# Jungle Temple (YUNG-inspired)

YUNG item 4. Previous: desert temple (1.8.0). Next: bridges, witch huts.

## Intent
The vanilla jungle temple is a small box with one lever puzzle. The user wants it replaced, like the
stronghold and the desert pyramid, by a large explorative temple in the spirit of YUNG's mods
("головоломки, ловушки, тайники"). It must be visible from afar in the jungle and reward exploring
every corner.

## User decisions
- **Two endings, chosen at random per temple** (as in the desert; config `jungleTempleBossChance`,
  default 0.4):
  - **Vault (puzzle):** the treasure vault is sealed. It opens when three idols stand on the three
    sockets at its door. The idols lie in three hidden caches spread through the temple.
  - **Sanctuary (boss):** Barako from Mowzie's Mobs (`mowziesmobs:barako`) on an altar; without
    Mowzie's, a strong vanilla mob. The reward chest is sealed until the boss is dead.
- **Puzzle = idols in hidden caches** (option В). Brute force must not solve it; the player has to
  explore the temple.
- **Layout = mixed** (option 3): a large stepped temple on the surface with halls and caches inside,
  plus a smaller underground part where the vault or the sanctuary is.
- Traps throughout: tripwire arrow traps and pressure plates over a pit.

## Pack check (user's server check, 2026-11-23) and decisions
- **Biomes O' Plenty worlds (user's pick А: support them).** In the BOP world type the scattered
  features come from `biomesoplenty.common.world.BOPMapGenScatteredFeature`; 1.8.0 leaves it alone, so
  BOP worlds (the user's "Богач") have no Citadels temples at all, desert ones included. Our temples
  must replace BOP's desert and jungle temples too, in BOP's own desert/jungle biome lists
  (`lush_desert`, `cold_desert`, `bamboo_forest`, `eucalyptus_forest`...). BOP's witch huts and igloos
  stay BOP's (witch spawning included). Done first, as its own release (1.8.1), since it also fixes
  the desert temples in BOP worlds.
- **Dynamic Trees:** jungle trees are `dynamictrees:` branches, roots, trunk shells and leaves over
  `rootydirt`. Ground sampling skips wood, leaves and plants (any mod) and counts `rootydirt` as
  ground; clearing removes them.
- **Barako's sun strike needs the sky** (Mowzie's 1.5.8 checks `canBlockSeeSky`): the boss sanctuary
  is the open shrine on the top tier of the surface temple, above the canopy, not underground. In
  the boss variant the underground goal room is a plain treasure storeroom.
- **Protection bypass:** Mekanism's cardboard box and IC2's miner take blocks without a break event.
  The vault shell is a new unbreakable block that looks like mossy stone bricks (hardness -1), and
  the cardboard box is refused (right click cancelled) in every protected part of every structure.

## Claude's calls (correct if wrong)
- In the boss variant there are no idols and no sockets; the sanctuary is open, the caches still
  exist and hold loot.
- Idols are one generic item for all temples, consumed when placed. An idol brought from another
  temple also works (accepted loophole).
- The caches are: behind a false wall (a block of the wall that looks the same, mined through), under
  a floor tile, behind a painting. Each holds one idol and a little loot.
- The idol sockets must not open in any other way: no conquest, no reconcile, no gate opening of the
  boss variant.
- **The whole vault is locked until the idols open it:** its walls, floor, ceiling, door and the
  sockets are a protected part of the record that only the sockets release (not conquest, not a
  cleared floor), and its shell is the unbreakable vault brick. Digging around the door does not
  skip the puzzle.
- **Terrain:** the site is levelled within the footprint: hills are cut down and dips filled with
  the foundation, trees and plants cleared. A site is rejected only if it is mostly water or the
  slope is extreme (more than about 12 blocks between the lowest and highest samples); then there is
  no temple there. Jungle temples get somewhat rarer than vanilla, which is accepted.
- **Visible from afar:** the top tier carries a shrine that rises above the canopy (the temple is
  about 30–35 blocks tall), and trees are cleared in a ring of about 4 blocks around the footprint
  (included in the footprint and the `isAreaFree` check).
- Jungle temples where the world's scattered-feature generator puts jungle temples: vanilla
  `JUNGLE`, `JUNGLE_HILLS`, or BOP's jungle list in BOP worlds; overworld only. Swamp and igloo
  sites unchanged; witch spawning untouched.

## Goals
1. **Replace the vanilla jungle temple** (config `replaceJungleTemples`, default true) in the same
   `ScatteredFeatureGen` as the desert temple: jungle sites are excluded from vanilla and get our plan.
   Older worlds keep started vanilla temples (`hasLegacyStartIn`).
2. **Surface temple** (new builder, own code):
   - Stepped mossy-cobblestone/stone-brick temple, 3 tiers, base about 31–37, chiseled stone-brick
     accents, vines and leaves overgrowing it, a stair up the front to an entrance on the second tier.
   - Ground fitted with `sampleHeights` (no leaves, water rejects the site); trees and plants cleared
     from the footprint (`clearColumn`), foundation `fillDown`.
   - Inside: a main hall on the entry tier and 3–5 smaller rooms on the tiers (shrine, storeroom,
     guard room, a gallery), linked by stairs. Two of the three caches are in the surface part.
   - A stair or shaft from the main hall down to the underground.
3. **Underground** (doorway grower, `StrongholdLayout` with a jungle catalogue):
   - 12–20 pieces within about 30 blocks: corridors, a flooded cistern room, a spider den, a
     storeroom, a pit-trap hall; the goal room (vault or sanctuary) on the deepest doorway.
   - The third cache is underground.
   - Palette: mossy cobble, mossy/cracked stone bricks, chiseled stone bricks, jungle wood accents.
   - Spawners: zombie, skeleton, cave spider (config `jungleTempleSpawnerMobs`).
4. **Vault variant:**
   - A treasure room behind a sealed door (`SEALED_GATE` blocks). Three idol sockets beside the door.
   - Right-clicking a socket with an idol places it (consumed, socket shows it). When all three
     sockets of that temple hold idols, the vault's gates open.
   - The record stores the socket positions and their state; the state survives restarts; sockets
     and gates in unloaded chunks catch up when loaded.
   - Vault: chests with the jungle temple table plus the modded pool, gold, emeralds.
5. **Sanctuary variant:** the open shrine on the top tier is the boss arena, with an altar, `preferredBoss = mowziesmobs:barako` (fallback order as
   in the desert temple: preferred, then a strong vanilla mob, then the tier pool), reward chest
   sealed until the boss dies (floor 0 boss floor, as the pharaoh's tomb).
6. **Protection:** the vault variant is unprotected (players may dig, as YUNG) except the vault
   itself (shell, door, sockets), which is a protected part until all three idols are placed; the
   sanctuary variant protects only the sanctuary until the boss is dead.
7. **New content:** item `jungle_idol` (en/ru names, texture), block `idol_socket` (texture, empty
   and filled states). Loot: jungle temple table gets the modded pool (as the desert pyramid).
8. **Command:** `/citadel spawn jungle_temple` and `/citadel spawn jungle_temple boss|vault` to force
   a variant (and the same for `desert_temple pharaoh|tomb`, which the user's testing needs).
9. **Config + lang** for every new option (en_us, ru_ru), names group `jungle` (temple, ziggurat,
   shrine...).

## Non-goals
Bridges, witch huts, igloos; redstone-based puzzles; modded jungles in non-BOP world types (their
generator places no temples there either); other dimensions.

## Anti-goals
- A vault that opens without all three idols (by conquest, reconcile, breaking, or a lever).
- A temple floating over a valley or half buried in a hill.
- Caches so obvious they are not hidden, or so hidden they cannot be found (each has a tell: a
  slightly different block, a painting, a torch nearby).

## Constraints
- Async build into a `Blueprint` (no world access in `build()`), written by `StructureJobs`.
- `SealedGates.reconcile` opens gates whose floor has no `FloorData`: vault gates need a key that
  reconcile and conquest never open.
- `-Werror`; tests via `TestBlocks.install()`; no YUNG code or structure files.
- Foundation fills must not pour into planned openings (lesson from 1.8.0; there is a test).

## Approaches
1. Small surface temple + underground grower, like the desert (Claude's pick): least new code.
2. Big multi-storey surface temple only: impressive, new floor-by-floor layout, tree clearing.
3. **Mixed (user's pick):** a large surface temple with halls and caches plus a smaller underground
   part with the vault or sanctuary. Reuses the grower underground; the surface temple is new work.

## Left to the builder
Exact room shapes, trap placement, how the socket state is stored on the record, the vanilla
fallback boss, idol and socket textures, the names list.
