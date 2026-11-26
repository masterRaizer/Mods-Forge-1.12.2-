# HANDOFF — Citadels of the Shattered Crown

Состояние на выпуск 1.0.0 (все 7 этапов слиты в `main`).

## Ветки этапов
1. `feat/stage-1-core-config` — каркас, RFG-сборка, MixinBooter late loader, конфиг.
2. `feat/stage-2-worldgen-palettes` — палитры 5 архетипов, цитадели, крипты, миксины Castle Dungeons.
3. `feat/stage-3-floors-hud` — WorldSavedData, трекер этажей, сеть, HUD и баннеры.
4. `feat/stage-4-trial-spawners` — сканер тиров мобов, испытательные спавнеры, волны.
5. `feat/stage-5-boss-antigrief` — алтарь, фабрика босса, способности, кат-сцена, защита арены.
6. `feat/stage-6-loot-lore-quests` — лут-таблицы, реликвии (Baubles optional), книги лора, достижения, квестлайн BQ.
7. `feat/stage-7-commands-audit` — `/citadel`, финальный аудит, версия 1.0.0.

## Проверено
- `./gradlew build` (javac `-Werror`, юнит-тесты), JSON-ресурсы через `python3 -m json.tool`.
- E2E на dev-сервере и obf-клиенте: генерация без cascading от мода, HUD, волны, босс, анти-грифинг, лут, реликвии, лор, достижения, все подкоманды `/citadel`.

## Не проверено
- Импорт квестлайна в живой BetterQuesting, реальный Lootr, предметы модов сборки в лут-таблицах.
- Better Advancements, мультиплеерная кат-сцена, разрушение арены FakePlayer-ами.

## Известное
- Castle Dungeons 2.2p1 сам вызывает cascading worldgen (не наш мод).
- Звуки щита босса — собственные SoundEvents мода (в 1.12 нет звуков маяка); замена при бэкпорте.

## Следующий шаг
- Тест в полной сборке на 170 модов; бэкпорт новых звуков/блоков — отдельной сессией.

## После 1.0.0 (ветка `claude/brave-franklin-ttdlk9`)
- 1.1.0: модовые боссы (Mowzie's Mobs, Atum, Thermal), модовые раньше ванильных.
- 1.2.0: цитадели-башни до Y 250, палитры из модовых блоков с выбором на каждую цитадель.
- 1.3.0: запечатанные этажи, неразрушимые цитадели, вазы, жилые комнаты
  (дизайн: `docs/superpowers/specs/2026-11-20-sealed-floors-vases-rooms-design.md`).
- 1.4.0: фоновый генератор больших построек, башни с ответвлениями, замок со стеной и рвом.
- 1.5.0: готический особняк.
- 1.6.0: катакомбы с магом-хранителем, куски катакомб вместо ванильных подземелий.
- 1.7.0: своя крепость Края.
- 1.8.0: пустынный храм; 1.8.1: храмы в мирах BOP, `/citadel spawn desert_temple pharaoh|tomb`.
- 1.9.0: храм в джунглях.
- 1.10.0: мосты через реки (7 стилей по биому + 3 особых, лагерь разбойников), `compat/ForeignZones`
  (в зоне Припяти Modern Backports ничего не строим).
- 1.11.0: хижины ведьм с погребом, Херобрин (логова, алтарь, босс, видения), башни и замки в Незере, Крае
  и Atum, своя крепость Незера.
- 1.11.1: 8 своих звуков Херобрина (ванильные файлы), достижения `herobrine_altar` и `herobrine_slayer`
  с квестами, `apparitionChance` 0,0003, одна строка предупреждения в логе о неизвестных id мобов гарнизона.
- Таблица версий с подробностями — `docs/TRANSFER.md`.

## Согласованный план (по одной работе, после проверки предыдущей в игре)
1. ✅ Запертые этажи, вазы, жилые комнаты (1.3.x).
2. ✅ Генератор больших построек + башни с ответвлениями + замок со стеной и рвом (1.4.0,
   дизайн: `docs/superpowers/specs/2026-11-21-large-structures-towers-castles-design.md`).
3. ✅ Гигантский особняк (1.5.0): `ManorPlan`/`ManorPlanner`/`ManorBuilder`, зоны = индексы этажей с
   `FloorData.zoneKey` и `requiresFloor` (спавнеры спят, пока предыдущая зона не зачищена), срезки — железная
   дверь + рычаг на дальней стороне + печать на ближней. Тип `MANOR`, палитра `GOTHIC_MANOR`.
4. ✅ Катакомбы с магом-хранителем (1.6.0, дизайн `docs/superpowers/specs/2026-11-22-catacombs-guardian-mage-design.md`,
   план `docs/superpowers/plans/2026-11-22-catacombs-guardian-mage.md`) + пункт 1 YUNG (подземелья со спавнером):
   `worldgen/catacomb/` — `CatacombLayout` (сетка ячеек 5 блоков: залы, лабиринт, обрезка тупиков, петли),
   `CatacombBuilder` (уровни через 10 блоков, винтовые лестницы, печати между уровнями, залы, святилище на этаже -10
   с `FloorData.sideBoss`, не снимающим защиту постройки), `FragmentPlan` + `VanillaDungeonReplacer` (ванильные
   подземелья запрещены на `TERRAIN_GEN_BUS`, по одному куску на регион). Маг — `entity/EntityGuardianMage`
   (`MageSpellCycle`, `EntityMageBolt`), вызывается `TileEntityMageSigil` под помостом, смерть —
   `structure/MageSanctum`. Предметы: посох, фолиант (TRINKET), кольцо (RING).
5. ✅ Херобрин (1.11.0, дизайн `docs/superpowers/specs/2026-11-26-herobrine-design.md`): пакет `herobrine/` —
   `LairPlacement` (области по `lairSpacingChunks`, одно место-кандидат на область только по сиду, бросок
   `lairChance`; ничего не ищется и не грузится), `HerobrineLairGenerator` (только верхний мир; вес после цитаделей
   и мостов; отказ у спавна, в 6 чанках от мест цитаделей, на воде/пляже/грибном острове, при земле вне 50–180,
   в `ForeignZones`), `HerobrineLairPlan` + `LairBuilder` + `LairVariant` (`keep`, `tower`, `shrine`),
   `AltarPattern` + `AltarHandler` (огниво или огненный шар по адскому камню алтаря; камень становится обсидианом;
   отказ в мирном режиме и при живом Херобрине в 64 блоках), `HerobrineSavedData` (`shatteredcitadels_herobrine`:
   логова и их состояние), `EntityHerobrine` + `HerobrinePhases` + `HerobrineSpellCycle` (три фазы: телепорт,
   молнии, призыв и слепота), `EntityHerobrineApparition` + `ApparitionHandler` + `ApparitionRule` (видения),
   `HerobrineRelic` (Око Херобрина), `HerobrineSettings` (читает конфиг напрямую), `HerobrineCommands`.
6. ✅ Другие измерения (1.11.0, дизайн `docs/superpowers/specs/2026-11-26-dims-design.md`): `worldgen/dims/` —
   `DimKind` (NETHER, END, ATUM по имени измерения), `DimensionWorldGenerator` (своя сетка секций = секция
   верхнего мира × множитель, до трёх кандидатов на секцию; бросок замок/храм, иначе башня), `DimSites`,
   `DimensionPlanner` + `GroundFinder` (земля измерения), `DimensionPlan` + `DimPalette` (обёртка над готовыми
   `CitadelPlan`/`CastlePlan`/`DesertTemplePlan`: блоки верхнего мира заменяются блоками измерения),
   `DimensionMobs` (гарнизон из списков конфига), `DimsConfig`, `DimensionCommands`. Новые архетипы в конце
   `CitadelArchetype`: `NETHER_CITADEL`, `END_CITADEL`, `ATUM_CITADEL`. Не для всех типов: особняк, склеп,
   катакомбы, небесная цитадель, храм джунглей и мосты там не строятся; пустынный храм — только в Atum, замков в
   Atum нет.

## Переделки ванильных построек 1.12 по мотивам модов YUNG (согласовано: все 7, в этом порядке)
Свои реализации (планировки, названия, код), без кода и файлов построек YUNG; с нашими механиками:
запечатанные проходы, волны, вазы, жилые комнаты, модовые палитры и лут, генерация через `StructureJobs`.
Better Caves, Better Mineshafts, Traveler's Titles и YUNG's Law уже есть в сборке на 1.12.2 — не трогаем.
1. Подземелья со спавнером → вместе с катакомбами (работа 4): склепы, паучьи пещеры, залы скелетов.
2. ✅ Крепости Края (1.7.0, дизайн `docs/superpowers/specs/2026-11-22-end-stronghold-design.md`): `worldgen/stronghold/` —
   `StrongholdLayout` (помещения с дверями от центрального перекрёстка, зал портала на самой глубокой двери),
   `StrongholdBuilder`, `StrongholdPlan` (зал портала пишется первым через `StructurePlan.priorityArea`),
   `ShatteredStrongholdGen` (подкласс `MapGenStronghold` через `InitMapGenEvent`: ванильные места и поиск оком,
   ванильные куски не строятся). Мир BIOMESOP проверяется в сборке (в dev Forge 2847, BOP требует 2858).
3. ✅ Пустынные храмы (1.8.0, дизайн `docs/superpowers/specs/2026-11-22-desert-temple-design.md`): `worldgen/temple/` —
   рост `StrongholdLayout` обобщён до каталога (`PieceSpec`, `Catalogue`; `TempleKind` — комнаты храма),
   `DesertTempleBuilder extends StrongholdBuilder` (палитра `BlockSet`, `SandstonePalette`), `Pyramid` (наземная
   часть), `DesertTemplePlan` (винтовая лестница от пирамиды к входному залу), `ScatteredFeatureGen` (подкласс
   `MapGenScatteredFeature` через `InitMapGenEvent`: только пустынные места, остальное ваниль). Гробница фараона —
   `preferredBoss` записи (`BossFactory.candidateOrder`: фараон Atum → кадавр → пул уровня).
   1.8.1: `ScatteredFeatureGen(TempleSites, spacing)` — правила мест ванили (`VanillaTempleSites`) или BOP
   (`BopTempleSites`, рефлексия: списки биомов и `$Start` BOP); своя сетка мест, шаг генератора сохраняется.
4. ✅ Храмы в джунглях (1.9.0, дизайн `docs/superpowers/specs/2026-11-23-jungle-temple-design.md`):
   `JungleTemple` (поверхность, ярусы, тайники, святилище-арена Барако), `JungleTempleBuilder` + `JungleKind`
   (подземелье), `JungleTemplePlan`, `JungleSite` (замер земли без деревьев), `VaultLock` в записи
   (сокровищница защищена и её печати `GATE_FLOOR` закрыты до трёх идолов; `SealedGates.mayOpen`),
   блоки `idol_socket`, `vault_brick`, предмет `jungle_idol`. Тайники — сундуки без таблицы лута (Lootr).
5. ✅ Мосты через реки (1.10.0, дизайн `docs/superpowers/specs/2026-11-26-river-bridges-design.md`):
   `worldgen/bridge/` — `BridgeGenerator` (река ищется по биомам, `BiomeDictionary.Type.RIVER`, без загрузки
   чанков; мосты только вдоль осей; вес после цитаделей), `BridgeStyle` (7 стилей по берегу + `ruined`,
   `covered`, `turreted`), `BridgeBuilder`, `BridgeCamp` (баррикады, хижина с сундуком `chests/bridge_camp`,
   3–5 охранников: поборники + разбойник из `raids`/`modernbackports` или скелет). `compat/ForeignZones` — чужие
   зоны (Припять Modern Backports), где мод не строит.
6. ✅ Хижины ведьм (1.11.0, дизайн `docs/superpowers/specs/2026-11-26-huts-design.md`): `worldgen/hut/` —
   `WitchHutPlan`, `HutVariant` (`small`, `tall`, `dock`), `HutBuilder`, `CellarLayout` (сетка 3×3 ячеек по 12
   блоков, 4–7 комнат, самая глубокая — ритуальный зал), `CellarBuilder`, `WitchHutLoot`, `WitchHutSpawns`
   (`WorldEvent.PotentialSpawns`: в хижине только ведьмы). Места — тот же `ScatteredFeatureGen`
   (`TempleSites.Kind.SWAMP`, и ваниль, и BOP); ванильная хижина старого мира остаётся (`hasLegacyStartIn`).
7. ✅ Крепости Незера (1.11.0, дизайн `docs/superpowers/specs/2026-11-26-dims-design.md`):
   `worldgen/dims/fortress/` — `ShatteredNetherBridgeGen extends MapGenNetherBridge` (через `InitMapGenEvent`,
   покрывает и Незер BOP; ванильные места, ванильные куски не начинаются, старые достраиваются; `/locate Fortress`),
   `FortressReplacer`, `FortressPlan`, `FortressLayout` (решётка узлов с шагом 22 вокруг донжона: перекрёстки,
   площадки ифритов, комнаты нароста, башенки; мосты и крытые коридоры), `FortressBuilder`, `FortressSpawns`
   (места по правилу ванили; внутри — ванильный список мобов крепости).
Не берём: Cave Biomes (в 1.12 биомы плоские, спорит с Better Caves), Ribbits, Menu Tweaks, Paxi, API, The First City.
Под вопросом на потом: Ocean Monuments, End Island, Extras.

## StructureType: номера (с 1.11.0)
`structure/StructureType` сохраняется в мир по номеру (ordinal). Константы нельзя переименовывать и
переставлять, новые — только в конец:
CITADEL=0, SKY_CITADEL=1, CASTLE_DUNGEONS=2, CRYPT=3, WALLED_CASTLE=4, MANOR=5, STRONGHOLD=6, DESERT_TEMPLE=7,
JUNGLE_TEMPLE=8, BRIDGE=9, WITCH_HUT=10, HEROBRINE_LAIR=11, NETHER_FORTRESS=12.
То же для `CitadelArchetype`: `NETHER_CITADEL`, `END_CITADEL`, `ATUM_CITADEL` добавлены в конец.

## Важные решения из дизайнов 2026-11-26
- Мосты: частота — не секции, а минимальное расстояние (`bridgeMinDistanceChunks` 24) и бросок на чанк
  (`bridgeChance` 0,5); в разделе «Goals» дизайна остались старые `bridgeSpacing` 28 и шанс 0,8 — верить коду.
  Крутой берег не отменяет мост: концы подстраиваются (`fillDown`/`clearColumn`/`setIfNotSolid`). Лагерь — только
  на воде шириной от 9 блоков; `turreted` всегда с лагерем, остальные особые — никогда. Охрана создаётся один раз
  при записи чанка с сундуком. Мосты не защищены, без HUD, этажей и достижения; `/citadel locate` их пропускает.
- Хижины: ведьмы создаются один раз и не исчезают, спавнеров нет; хижина и погреб не защищены; в погребе не
  меньше одной ловушки с зельем; только верхний мир. Соседние чанки дают похожие сиды, поэтому сид
  перемешивается (`CellarLayout.mix`; то же в `LairPlacement.scramble`, `DimSites.scramble`).
- Херобрин: логова — обычные ломаемые блоки без защиты; алтарь логова работает один раз, состояние хранится в
  `shatteredcitadels_herobrine`; видения только возле логова с непобеждённым хозяином и безвредны; сетевые id
  сущностей 40 и 41. `apparitionChance` в 1.11.0 по умолчанию 0,35, с 1.11.1 — 0,0003 (значение в уже записанном конфиге
  само не меняется, его правят руками).
- Измерения и крепость: зависимости от Atum при компиляции нет (измерение узнаётся по имени); в Крае — только
  дальше `endMinDistance` и на сплошном камне Края, замки — только на островах ванильного генератора; пустой
  список мобов измерения — запасной реестр тиров. Крепость Незера не защищена (как крепость Края), настил на
  Y 52–63, сундуки с `minecraft:chests/nether_bridge` (в донжоне два из четырёх — с таблицей мода); место
  пропускается, если там уже стоит ванильная крепость старого мира. В таблице дизайна у `atumMobs` указан
  `skeleton` — в коде его нет (только мобы Atum и `minecraft:husk`).
- Устаревшие комментарии в коде: javadoc `ScatteredFeatureGen` и лог `TempleReplacer` говорят только о
  пустынных и джунглевых местах, хотя болотные (хижины) тоже обрабатываются.

## Как устроена генерация (с 1.4.0)
- `CitadelWorldGenerator` только планирует (`StructurePlan`: `CitadelPlan`, `CryptPlan`, `CastlePlan`) и ставит в
  `StructureJobs` (WorldSavedData `shatteredcitadels_jobs`).
- План строится в `Blueprint` на фоновом потоке (`BlockWriter` больше не трогает мир; рельеф — `fillDown`,
  `clearColumn`, `setIfNotSolid`; tile entity — `afterPlace`).
- Запись: только в загруженные заселённые чанки с загруженными соседями, напрямую в секции, свет один раз на чанк,
  пересылка чанка игрокам. Бюджет `generationBudgetMs` (5 мс/тик).
- Этажи с горизонтальной областью (`FloorData.setArea`): двор (этаж 0) и этажи донжона на одной высоте,
  `floorAt(BlockPos)` берёт самый маленький. Защита — только внутри `CitadelInstance.parts`.
