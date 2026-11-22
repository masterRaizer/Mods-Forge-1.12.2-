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
5. Херобрин (телепорт, молнии, призыв, фазы) и несколько вариантов его замков.
6. Другие измерения (Незер, Край, Atum) для всех типов построек.

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
4. Храмы в джунглях: головоломки, ловушки, тайники.
5. Мосты через реки.
6. Хижины ведьм на болотах.
7. Крепости Незера → вместе с работой «Другие измерения».
Не берём: Cave Biomes (в 1.12 биомы плоские, спорит с Better Caves), Ribbits, Menu Tweaks, Paxi, API, The First City.
Под вопросом на потом: Ocean Monuments, End Island, Extras.

## Как устроена генерация (с 1.4.0)
- `CitadelWorldGenerator` только планирует (`StructurePlan`: `CitadelPlan`, `CryptPlan`, `CastlePlan`) и ставит в
  `StructureJobs` (WorldSavedData `shatteredcitadels_jobs`).
- План строится в `Blueprint` на фоновом потоке (`BlockWriter` больше не трогает мир; рельеф — `fillDown`,
  `clearColumn`, `setIfNotSolid`; tile entity — `afterPlace`).
- Запись: только в загруженные заселённые чанки с загруженными соседями, напрямую в секции, свет один раз на чанк,
  пересылка чанка игрокам. Бюджет `generationBudgetMs` (5 мс/тик).
- Этажи с горизонтальной областью (`FloorData.setArea`): двор (этаж 0) и этажи донжона на одной высоте,
  `floorAt(BlockPos)` берёт самый маленький. Защита — только внутри `CitadelInstance.parts`.
