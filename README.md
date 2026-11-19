# Citadels of the Shattered Crown

Мод подземелий для Minecraft 1.12.2 (Forge 14.23.5.2860, Java 8, MixinBooter).
modid: `shatteredcitadels`, версия 1.0.0.

## Что добавляет мод
- Многоэтажные цитадели пяти архетипов (Цитадель Хранителей, Обсидиановая Бастилия, Техно-арканный Шпиль,
  Кузня Титанов, Небесная Цитадель), подземные крипты-лабиринты с ловушками; генерация без cascading worldgen.
- Интеграция с Castle Dungeons (через миксины, только если мод установлен): фильтр дистанции, свои спавнеры в замках.
- Этажи с HUD и баннерами, волны мобов из испытательных спавнеров (тиры мобов собираются из всех модов сборки).
- Алтарь босса, кат-сцена появления, 5 способностей босса, защита арены от гриферства.
- Лут по этажам (совместим с Lootr), 4 реликвии (слоты Baubles, если установлен), 7 глав книг лора,
  12 достижений и квестлайн BetterQuesting.

## Установка в сборку
1. Положите `shatteredcitadels-1.0.0.jar` и MixinBooter (8.0 или новее) в папку `mods`.
   Forge 14.23.5.2847+ (рекомендуется 2860). Остальные интеграции (Castle Dungeons, Lootr, Baubles,
   Apotheosis, BetterQuesting) необязательны и подхватываются автоматически.
2. Первый запуск создаст конфиг `config/shatteredcitadels/shatteredcitadels.cfg`
   (разделы `castle_generation`, `spawner_and_waves`, `boss_settings`, `hud_and_loot`, `integration`).
   Большинство параметров можно менять в игре через меню Mods → Config.
3. BetterQuesting: при первом запуске с установленным BQ мод кладёт квестлайн в
   `config/betterquesting/citadels_questline.json` (существующий файл не перезаписывается).
   Импорт: в игре откройте редактор квестов BQ (режим редактирования) → Import → «Native JSON» →
   выберите `citadels_questline.json`.
4. Сервер и клиент должны иметь одинаковую версию мода.

## Команды (уровень оператора 2)
- `/citadel spawn <keepers_citadel|obsidian_bastille|techno_arcane_spire|titan_forge|sky|dungeon>` — построить структуру в текущем чанке.
- `/citadel locate` — ближайшая цитадель или подземелье в измерении.
- `/citadel floorinfo` — этаж, волна, лимит мобов, состояние арены и босса.
- `/citadel listmobs <1-5|underground>` — мобы тира после сканирования модов.
- `/citadel testboss` — принудительно вызвать босса структуры, в которой вы стоите.
- `/shatteredcitadels` (`/scitadels`) — расширенная диагностика: list, info, status, spawners, boss, relics, lore и др.

## Сборка из исходников
`./gradlew build` → `build/libs/shatteredcitadels-1.0.0.jar` (refmap миксинов включён в jar).
Для тестов с Castle Dungeons/Baubles: `./gradlew runServer -PwithCastleDungeons -PwithBaubles`.
