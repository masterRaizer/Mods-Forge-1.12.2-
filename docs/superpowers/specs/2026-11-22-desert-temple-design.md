# Desert Temple (YUNG-inspired)

YUNG item 3. Next: jungle temples (same generator), bridges, witch huts.

## User decisions
- **Both endings**, chosen at random per temple (config `desertTempleBossChance`, default 0.4):
  - **tomb:** sarcophagus and treasure, explorative, no fight;
  - **pharaoh's tomb:** a boss on an altar in the tomb. It is `atum:pharaoh` when Atum is installed,
    otherwise a strong husk. The sarcophagus is sealed until the boss is dead.
- Everything else is explorative, like YUNG and our stronghold. The approach is Claude's call (the
  user handed over the how): a surface pyramid generator plus the stronghold's doorway grower,
  generalised to a piece catalogue, for the underground.

## Goals
1. **Replace the vanilla desert pyramid** (config `replaceDesertTemples`, default true).
   - A `MapGenScatteredFeature` subclass is installed by `InitMapGenEvent` `SCATTERED_FEATURE`.
   - It keeps the vanilla site grid and returns false from `canSpawnStructureAtCoords` only for
     desert sites (`DESERT`, `DESERT_HILLS`, as vanilla chooses).
   - Jungle temples, witch huts and igloos stay vanilla. The witch spawning (`isSwampHut`,
     `getMonsters`) is untouched.
   - `generateStructure` calls super first (older worlds keep their started vanilla temples) and
     queues our plan on a desert site, unless an old start or another structure is there. The
     stronghold-site reservation applies.
2. **Pyramid.**
   - Stepped sandstone, base 27–33, hollow interior, entrance colonnade facing south with two side
     towers.
   - A main hall with columns, chiseled and smooth sandstone, orange and blue terracotta patterns.
   - Sand piled against the walls.
   - A 5×5 spiral from the hall down to the underground start crossing, 16 below the surface.
3. **Underground:** 25–40 pieces within 40 blocks, one storey down by stairs.
   - Corridors, burial chambers (sarcophagi, vases, husk spawner), storerooms, a scroll library, a
     pillared hall, a treasury (gold, chests, a TNT pressure-plate trap as in vanilla).
   - The **tomb** goes on the deepest doorway.
   - Arrow traps in corridors.
   - Spawners: husk, skeleton, spider (config `desertTempleSpawnerMobs`).
4. **Tomb variants.**
   - Plain: a sarcophagus with 2 chests (desert pyramid table plus modded pool).
   - Pharaoh:
     - the tomb is a boss arena with a boss altar, floor 0 (`bossFloor`) covering the tomb;
     - the record prefers boss `atum:pharaoh` (falls back to `minecraft:husk`, then the pool);
     - the sarcophagus chest sits behind sealed gates that need floor 0, which opens on the boss's
       death;
     - the tomb is a protected part until then.
5. **Record:** `StructureType.DESERT_TEMPLE`. No parts (plain variant) means unprotected; the
   pharaoh variant protects only the tomb.
6. **Command:** `/citadel spawn desert_temple`.

## Non-goals
Jungle temples, witch huts, igloos; sand-fall and pit traps; surface structures in other biomes.

## Approaches (Claude's pick)
- **Doorway grower (generalised) for the underground plus a dedicated pyramid builder (picked):**
  reuses tested growth and shell code.
- A fixed hand-made temple: repeats.
- A cell grid: reads as a maze, not a temple.
