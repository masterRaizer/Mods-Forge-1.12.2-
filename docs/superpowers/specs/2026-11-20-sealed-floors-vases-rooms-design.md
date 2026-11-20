# Sealed floors, unbreakable citadels, vases and lived-in rooms

Part 1 of 4 agreed with the user (then: other dimensions, giant manor, Herobrine castles).

## Intent
The user wants citadels that feel like a soulslike / Minecraft Dungeons crawl: you cannot skip
ahead, cannot dig around the intended path, and every room feels like someone used to live there,
with things to smash and loot. The same room/vase/lock pieces will be reused by the giant manor.

## Goals
1. **Sealed floors (user picked option A + unbreakable walls).** The stairwell opening into the
   next floor is closed by a visible, unbreakable *sealed gate* until the current floor's waves are
   cleared; the gate then vanishes with sound and particles. Same downward: crypt ladders are
   sealed until the level above is cleared; the citadel's crypt hatch needs floor 0 cleared.
2. **Unbreakable citadels (like the Twilight Forest castle).** Inside a citadel's bounds players
   cannot break blocks (no crack progress), explosions and griefing mobs cannot either.
   Exempt: vases, cobwebs, blocks with a tile entity other than ours (chests, furnaces,
   furniture, beds). Creative/spectator ignore it. After the citadel's boss is defeated the
   protection lifts and every remaining gate opens. Works for already generated citadels too
   (gates only in new ones).
3. **Vases.** New block, breaks in one hit, drops loot from a tier table (low/mid/high by floor
   difficulty), ~12 % chance to release a mob of the floor's tier instead of/besides loot.
   Variants: clay, dark, gilded (gilded = rare, rolls the next tier). 3-6 per floor, also in crypts.
4. **Lived-in, abandoned rooms.** New room types in the floor rotation:
   - *Pantry*: barrels, hay, pumpkins, melons, MrCrayfish fridge/freezer and oven when present
     (fridge filled with pantry loot), pantry chest.
   - *Bedroom*: beds, carpets, bedside cabinet, bookshelf, bedroom chest (books, potions, trinkets).
   - *Smeltery*: row of furnaces / blast furnaces / smokers, damaged anvils, cauldron,
     crafting table, smeltery chest (ores, ingots, coal, tools).
   - Abandoned pass for every room: cobwebs in upper corners, rubble (cobble/mossy cobble),
     overturned furniture (upside-down stairs).
   - Each room type has its own loot table; modded items are injected at load like today.

## Non-goals
- Retro-fitting gates into existing citadels. Other dimensions, manor, Herobrine (later parts).
- Piston/fluid protection.

## Constraints
- Generation stays inside the 2x2-chunk population box (no cascading worldgen).
- Loot JSON only vanilla items; modded items via `ModLootInjector` (missing mods skipped).
- No class references to other mods; furniture is resolved by registry name/scan.

## Approaches for the lock (user picked A)
- A: visible gate blocks in the stairwell (+ unbreakable structure so it cannot be bypassed). **Picked.**
- B: invisible push-back when above an uncleared floor. Rejected: not visible.
- C: both. Not picked.

## Design
- `BlockSealedGate`: unbreakable (hardness -1, huge resistance), full collision, cutout bars with
  glowing runes, light 7. Right-click: "the seal holds until this floor's guardian falls".
- Gate placement: surface stairs f→f+1 occupy steps k=0..6; the slab of floor f+1 is cut for
  k>=4. Gates fill k=4..7 at the slab Y of floor f+1 (k=7 is the top stair: restore = stair).
  Crypt: top cell of each inter-level ladder and the citadel hatch (restore = ladder).
- `CitadelInstance` stores `GatePoint{pos, requiredFloor, restoreStateId}` (NBT).
- `SealedGates.open(world, citadel, floor)`: restores every loaded gate of that floor, removes it
  from the record, plays iron-door/wither-break sound + particles, tells players on the citadel.
  Called when `FloorCombatState` clears a floor, on boss defeat (all), and reconciled from
  `FloorTrackerHandler.refresh` (gates whose floor is cleared but were unloaded).
- Floors with no spawner point (non-boss) are marked cleared at generation and their gates are
  opened immediately, so nothing can stay locked forever.
- `CitadelProtectionHandler`: server `BreakEvent` cancel + `BreakSpeed` 0; client `BreakSpeed` 0
  while the HUD says the player is inside an unconquered citadel; explosion filter;
  `LivingDestroyBlockEvent` and mob griefing inside bounds.
- Config: `lockFloorsUntilCleared`, `unbreakableCitadels`, `vaseMobChance`.

## Calls made by Claude (user can override)
- Protection lifts after the boss is defeated.
- Gilded vases are rare (10 %) and roll the next loot tier.
- Room types join the existing rotation (guard room, library, barracks, treasury) rather than
  replacing them.
