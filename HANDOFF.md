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
